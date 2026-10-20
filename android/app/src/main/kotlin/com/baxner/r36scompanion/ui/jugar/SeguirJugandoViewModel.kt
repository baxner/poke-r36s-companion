package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.respaldos.juegoPorNombreDeArchivo
import com.baxner.r36scompanion.ui.juego.nombreDeJuego
import com.baxner.r36scompanion.data.textos.Textos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar
import com.baxner.r36scompanion.data.jugar.ErrorDeJuego
import com.baxner.r36scompanion.data.jugar.ErrorDeRom
import com.baxner.r36scompanion.data.jugar.Prestamos
import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import com.baxner.r36scompanion.data.jugar.RomsDelTelefono
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.Reloj
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.emulador.AbridorDeSalas
import com.baxner.r36scompanion.emulador.JuegoParaSala
import com.baxner.r36scompanion.emulador.Sala
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeJuego
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeRom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** En qué va "seguir jugando en el teléfono" (ver el encargo, punto 3). */
enum class EtapaDeSeguirJugando {
    CARGANDO,

    /** No hay ROM en el teléfono y no hay consola conectada para copiarla. */
    SIN_CONSOLA_NI_ROM,
    ERROR_DE_ROM,

    /** [Prestamos.avisosAntesDeLlevar] tuvo algo que decir antes de llevarse la partida. */
    AVISOS,
    JUGANDO,
    CONFIRMAR_SALIR,

    /** Ya se dejó de jugar: la partida quedó en el teléfono, falta regresarla (o no, "Después"). */
    PARTIDA_EN_TELEFONO,

    /** La consola cambió desde que se leyó: elegir con cuál quedarse. */
    CONFLICTO,

    /** Hay que cerrar el juego en RetroArch antes de poder escribir. */
    JUEGO_ABIERTO,
    ERROR_DE_RED,

    /** Ya se regresó a la consola (o se descartó a propósito): no queda nada pendiente. */
    REGRESADO,
    DESCARTADO,
}

data class SeguirJugandoUiState(
    val etapa: EtapaDeSeguirJugando = EtapaDeSeguirJugando.CARGANDO,
    val nombreDeJuego: String = "",
    val mensajeDeErrorDeRom: String? = null,
    val avisos: List<AvisoAntesDeJugar> = emptyList(),
    val ultimoGuardadoEnElTelefono: Long? = null,
    val conSonido: Boolean = true,
    val mensajeDeError: String? = null,
    /** Si hay conexión con la consola ahora mismo (para ofrecer "Regresar a la consola" o solo "Después"). */
    val puedeRegresar: Boolean = false,
    val procesando: Boolean = false,
    /**
     * Lo último que guardó el juego no se pudo escribir en el teléfono (p. ej. sin espacio): se
     * reintenta solo, y mientras tanto no se regresa nada a la consola (sería una versión vieja).
     */
    val errorAlGuardarEnElTelefono: String? = null,
)

/**
 * "Seguir jugando en el teléfono" (ver el encargo, punto 3): trae la ROM y la partida, la presta
 * si hace falta (avisando antes si [Prestamos.avisosAntesDeLlevar] tiene algo que decir), abre una
 * [Sala] de un jugador y guarda en el teléfono cada vez que el juego guarda. Al salir, regresa la
 * partida a la consola con las reglas de seguridad de siempre.
 *
 * Con [regresarAlAbrir] en true (desde la tarjeta de Inicio o el aviso del detalle del juego,
 * "Regresar a la consola" sin volver a jugar) se salta todo lo de la ROM/sala y va derecho a
 * [regresar]: [Prestamos.regresar] no necesita la ROM ni el emulador, solo la partida que ya está
 * guardada en el teléfono.
 */
class SeguirJugandoViewModel(
    private val juegoId: Long,
    private val repositorio: RepositorioDeRespaldos,
    private val roms: RomsDelTelefono,
    private val prestamos: Prestamos,
    private val sesion: StateFlow<SesionDeConsola?>,
    private val abridor: AbridorDeSalas,
    private val alcanceDeLaApp: CoroutineScope,
    regresarAlAbrir: Boolean = false,
    private val reloj: Reloj = Reloj { System.currentTimeMillis() },
    /** Para el nombre a mostrar ("Rojo Fuego"); sin él, se deduce del nombre del archivo. */
    private val textos: Textos? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SeguirJugandoUiState())
    val uiState: StateFlow<SeguirJugandoUiState> = _uiState.asStateFlow()

    private var sala: Sala? = null
    private var salaTerminada = false
    private var pollJob: kotlinx.coroutines.Job? = null
    private var ultimoVecesGuardado = 0
    @Volatile private var ultimaPartidaGuardadaBytes: ByteArray? = null
    /** Lo último que el juego guardó y todavía no está en el disco del teléfono. */
    @Volatile private var pendienteDeGuardar: ByteArray? = null
    /** El guardado de lo último al salir: [regresar] lo espera antes de escribir en la consola. */
    private var guardadoFinal: kotlinx.coroutines.Job? = null
    private var romActual: RomEnElTelefono? = null

    init {
        viewModelScope.launch {
            val juego = repositorio.juegos().first().firstOrNull { it.id == juegoId }
            _uiState.update {
                it.copy(nombreDeJuego = juego?.let { j -> textos?.let { t -> nombreDeJuego(j, t) } ?: juegoPorNombreDeArchivo(j.nombre) ?: j.nombre }.orEmpty())
            }
            when {
                regresarAlAbrir -> {
                    val sesionActual = sesion.value
                    if (sesionActual == null) {
                        _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.SIN_CONSOLA_NI_ROM) }
                    } else {
                        _uiState.update { it.copy(procesando = true) }
                        ejecutarRegresar(sesionActual)
                    }
                }
                juego == null -> _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.SIN_CONSOLA_NI_ROM) }
                else -> iniciarFlujoDeJuego(juego)
            }
        }
    }

    private suspend fun iniciarFlujoDeJuego(juego: com.baxner.r36scompanion.data.respaldos.JuegoRespaldado) {
        val sesionActual = sesion.value
        try {
            val rom = if (sesionActual != null) roms.traer(sesionActual, juego) else roms.rom(juegoId)
            if (rom == null) {
                _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.SIN_CONSOLA_NI_ROM) }
                return
            }
            romActual = rom
        } catch (e: ErrorDeRom) {
            _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.ERROR_DE_ROM, mensajeDeErrorDeRom = mensajeDeErrorDeRom(e)) }
            return
        } catch (e: Exception) {
            _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.ERROR_DE_RED, mensajeDeError = e.message ?: "se perdió la conexión con la consola") }
            return
        }

        val yaPrestada = prestamos.prestada(juegoId)
        if (yaPrestada == null) {
            val avisos = prestamos.avisosAntesDeLlevar(juegoId)
            if (avisos.isNotEmpty()) {
                _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.AVISOS, avisos = avisos) }
                return
            }
        }
        empezarAJugar()
    }

    /** "Jugar de todos modos" en la pantalla de avisos. */
    fun continuarApesarDeAvisos() {
        viewModelScope.launch { empezarAJugar() }
    }

    private suspend fun empezarAJugar() {
        val rom = romActual ?: return
        try {
            val prestada = prestamos.llevar(juegoId)
            val partida = prestamos.partida(juegoId)
            val nuevaSala = abridor.abrir(listOf(JuegoParaSala(rom.archivo, partida)), conCable = false)
            sala = nuevaSala
            salaTerminada = false
            nuevaSala.arrancar()
            nuevaSala.sonidoDe(0)
            ultimoVecesGuardado = nuevaSala.vecesQueGuardo(0)
            _uiState.update {
                it.copy(etapa = EtapaDeSeguirJugando.JUGANDO, ultimoGuardadoEnElTelefono = prestada.ultimaVezGuardada, conSonido = nuevaSala.conSonido)
            }
            empezarSondeo()
        } catch (e: ErrorDeJuego) {
            _uiState.update { it.copy(mensajeDeError = mensajeDeErrorDeJuego(e)) }
        } catch (e: Exception) {
            _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.ERROR_DE_RED, mensajeDeError = e.message ?: "se perdió la conexión con la consola") }
        }
    }

    private fun empezarSondeo() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val s = sala ?: break
                val veces = s.vecesQueGuardo(0)
                if (veces != ultimoVecesGuardado) {
                    ultimoVecesGuardado = veces
                    s.partidaGuardada(0)?.let { pendienteDeGuardar = it }
                }
                guardarPendiente()
                if (!s.conSonido) {
                    val reabierto = s.reabrirSonido()
                    _uiState.update { it.copy(conSonido = reabierto) }
                }
            }
        }
    }

    fun pausar() {
        sala?.pausar()
    }

    fun reanudar() {
        sala?.reanudar()
    }

    /** Dónde dibujar (o `null` al destruirse: ver [PantallaDelGba]). */
    fun pantalla(superficie: android.view.Surface?, escala: Int) {
        sala?.pantalla(0, superficie, escala)
    }

    /** La máscara actual de [EntradaDeJugadores] (táctil + mandos), para mandarla al emulador. */
    fun teclas(mascara: Int) {
        sala?.teclas(0, mascara)
    }

    /** Atrás o el botón de salir, estando [EtapaDeSeguirJugando.JUGANDO]: pide confirmar. */
    fun pedirConfirmarSalir() {
        if (_uiState.value.etapa == EtapaDeSeguirJugando.JUGANDO) {
            _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.CONFIRMAR_SALIR) }
        }
    }

    fun cancelarSalir() {
        _uiState.update { it.copy(etapa = EtapaDeSeguirJugando.JUGANDO) }
    }

    fun confirmarSalir() {
        terminarYGuardarLoUltimo()
        _uiState.update { it.copy(procesando = true) }
        viewModelScope.launch {
            // "Regresar a la consola" aparece hasta que lo último ya está en el disco del teléfono.
            guardadoFinal?.join()
            _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO, puedeRegresar = sesion.value != null) }
        }
    }

    /**
     * Escribe en el teléfono lo último que guardó el juego, si falta. true si ya no falta nada. Si
     * falla, lo deja pendiente (se reintenta en la siguiente vuelta del sondeo o al regresar) y lo avisa.
     */
    private suspend fun guardarPendiente(): Boolean {
        val bytes = pendienteDeGuardar ?: return true
        return try {
            val prestada = prestamos.guardar(juegoId, bytes)
            ultimaPartidaGuardadaBytes = bytes
            if (pendienteDeGuardar === bytes) pendienteDeGuardar = null
            _uiState.update {
                it.copy(ultimoGuardadoEnElTelefono = prestada.ultimaVezGuardada ?: reloj.ahoraMs(), errorAlGuardarEnElTelefono = null)
            }
            true
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorAlGuardarEnElTelefono = "No se pudo guardar en el teléfono lo último que guardó el juego (${e.message ?: "error"}). Libera espacio; se vuelve a intentar solo.")
            }
            false
        }
    }

    /** Termina la sala (si sigue abierta) y guarda en [alcanceDeLaApp] lo último si cambió desde el último guardado visto. */
    private fun terminarYGuardarLoUltimo() {
        pollJob?.cancel()
        val s = sala ?: return
        if (salaTerminada) return
        salaTerminada = true
        val resultados = s.terminar()
        sala = null
        val bytesFinal = resultados.getOrNull(0)
        if (bytesFinal != null && (ultimaPartidaGuardadaBytes == null || !bytesFinal.contentEquals(ultimaPartidaGuardadaBytes))) {
            pendienteDeGuardar = bytesFinal
        }
        if (pendienteDeGuardar != null) {
            guardadoFinal = alcanceDeLaApp.launch {
                withContext(NonCancellable) { guardarPendiente() }
            }
        }
    }

    /** "Regresar a la consola", desde [EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO] o al abrir con `regresarAlAbrir`. */
    fun regresar() {
        val sesionActual = sesion.value ?: return
        if (_uiState.value.procesando) return
        _uiState.update { it.copy(procesando = true, mensajeDeError = null) }
        viewModelScope.launch { ejecutarRegresar(sesionActual) }
    }

    private suspend fun ejecutarRegresar(sesionActual: SesionDeConsola) {
        guardadoFinal?.join()
        if (!guardarPendiente()) {
            _uiState.update { it.copy(procesando = false) }
            return
        }
        try {
            prestamos.regresar(sesionActual, juegoId)
            _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.REGRESADO) }
        } catch (e: ErrorDeEscritura.Conflicto) {
            _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.CONFLICTO) }
        } catch (e: ErrorDeEscritura.JuegoAbierto) {
            _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.JUEGO_ABIERTO) }
        } catch (e: Exception) {
            _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.ERROR_DE_RED, mensajeDeError = e.message ?: "se perdió la conexión con la consola") }
        }
    }

    /** Conflicto: "Quedarme con la del teléfono". */
    fun regresarAunqueCambio() {
        val sesionActual = sesion.value ?: return
        _uiState.update { it.copy(procesando = true, mensajeDeError = null) }
        viewModelScope.launch {
            guardadoFinal?.join()
            if (!guardarPendiente()) {
                _uiState.update { it.copy(procesando = false) }
                return@launch
            }
            try {
                prestamos.regresarAunqueCambio(sesionActual, juegoId)
                _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.REGRESADO) }
            } catch (e: ErrorDeEscritura.JuegoAbierto) {
                _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.JUEGO_ABIERTO) }
            } catch (e: Exception) {
                _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.ERROR_DE_RED, mensajeDeError = e.message ?: "se perdió la conexión con la consola") }
            }
        }
    }

    /** Conflicto: "Quedarme con la de la consola" (ya confirmado en la pantalla). */
    fun descartarYQuedarseConLaDeLaConsola() {
        _uiState.update { it.copy(procesando = true) }
        viewModelScope.launch {
            prestamos.descartar(juegoId)
            _uiState.update { it.copy(procesando = false, etapa = EtapaDeSeguirJugando.DESCARTADO) }
        }
    }

    /** Reintentar tras [EtapaDeSeguirJugando.JUEGO_ABIERTO] o [EtapaDeSeguirJugando.ERROR_DE_RED]. */
    fun reintentarRegresar() = regresar()

    fun descartarError() {
        _uiState.update { it.copy(mensajeDeError = null) }
    }

    override fun onCleared() {
        terminarYGuardarLoUltimo()
    }
}
