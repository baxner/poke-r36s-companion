package com.baxner.r36scompanion.ui.jugar

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.intercambio.ErrorDeIntercambio
import com.baxner.r36scompanion.data.jugar.CambiosDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.ErrorDeJuego
import com.baxner.r36scompanion.data.jugar.EstadoDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.ErrorDeRom
import com.baxner.r36scompanion.data.jugar.IntercambiosReales
import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import com.baxner.r36scompanion.data.jugar.RomsDelTelefono
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.emulador.AbridorDeSalas
import com.baxner.r36scompanion.emulador.JuegoParaSala
import com.baxner.r36scompanion.emulador.Sala
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeEscritura
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeIntercambio
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeJuego
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeRom
import com.baxner.r36scompanion.ui.juego.frasesDeListaDeCambios
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** En qué va el intercambio real (ver el encargo, punto 4). */
enum class EtapaDeIntercambioReal {
    CARGANDO,

    /** Falta la ROM de uno de los dos juegos y no hay consola para copiarla. */
    FALTA_CONSOLA,
    ERROR_DE_ROM,

    /** No se pudo empezar (ver [ErrorDeJuego]: otro intercambio sin terminar, o una partida ya prestada). */
    ERROR_AL_EMPEZAR,

    /** Antes de jugar: ir al Centro Pokémon y al Club de Cable en los dos juegos. */
    INSTRUCCIONES,
    JUGANDO,
    CONFIRMAR_TERMINAR,

    /** Ya se dejó de jugar: qué cambió en cada partida, y escribir o descartar. */
    REVISION,
    CONFIRMAR_DESCARTAR,
    ESCRITO,
    DESCARTADO,
    ERROR_DE_RED,
}

data class IntercambioRealUiState(
    val etapa: EtapaDeIntercambioReal = EtapaDeIntercambioReal.CARGANDO,
    val nombreA: String = "",
    val nombreB: String = "",
    val mensajeDeErrorDeRom: String? = null,
    val necesitaPokedexNacional: Boolean = false,
    val jugadorControlado: Int = 0,
    val vecesGuardadaA: Int = 0,
    val vecesGuardadaB: Int = 0,
    val conSonido: Boolean = true,
    val mensajeDeError: String? = null,
    val procesando: Boolean = false,
    val cambios: CambiosDeIntercambioReal? = null,
    val frasesDeCambiosA: List<String> = emptyList(),
    val frasesDeCambiosB: List<String> = emptyList(),
)

/**
 * El intercambio real (ver el encargo, punto 4): dos partidas corriendo a la vez, unidas por el
 * cable link emulado, para intercambiar Pokémon dentro de los juegos (Centro Pokémon → Club de
 * Cable) en vez de con el intercambio rápido. Retoma [IntercambiosReales.pendiente] si ya hay uno
 * de estos dos juegos sin terminar (la app se cerró a medias); si no, [IntercambiosReales.empezar].
 */
class IntercambioRealViewModel(
    private val juegoA: Long,
    private val juegoB: Long,
    private val repositorio: RepositorioDeRespaldos,
    private val roms: RomsDelTelefono,
    private val reales: IntercambiosReales,
    private val sesion: StateFlow<SesionDeConsola?>,
    private val abridor: AbridorDeSalas,
    private val alcanceDeLaApp: CoroutineScope,
    private val textos: Textos,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IntercambioRealUiState())
    val uiState: StateFlow<IntercambioRealUiState> = _uiState.asStateFlow()

    private var id: Long = -1
    private var sala: Sala? = null
    private var salaTerminada = false
    private var pollJob: Job? = null
    private var romA: RomEnElTelefono? = null
    private var romB: RomEnElTelefono? = null
    private val ultimosVecesGuardado = IntArray(2)
    private val ultimasGuardadas = arrayOfNulls<ByteArray>(2)

    init {
        viewModelScope.launch {
            val juegos = repositorio.juegos().first()
            val juegoAInfo = juegos.firstOrNull { it.id == juegoA }
            val juegoBInfo = juegos.firstOrNull { it.id == juegoB }
            _uiState.update { it.copy(nombreA = juegoAInfo?.nombre.orEmpty(), nombreB = juegoBInfo?.nombre.orEmpty()) }
            if (juegoAInfo == null || juegoBInfo == null) {
                _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.FALTA_CONSOLA) }
                return@launch
            }
            iniciar(juegoAInfo, juegoBInfo)
        }
    }

    private suspend fun iniciar(juegoAInfo: JuegoRespaldado, juegoBInfo: JuegoRespaldado) {
        try {
            val pendiente = reales.pendiente().first()
            val intercambio = if (pendiente != null && pendiente.juegoA == juegoA && pendiente.juegoB == juegoB) {
                pendiente
            } else {
                reales.empezar(juegoA, juegoB)
            }
            id = intercambio.id

            // Si ya se dejó de jugar (la app se cerró entre "Terminé" y escribir/descartar), no
            // hace falta la ROM ni la sala: directo a la revisión de lo que ya se guardó.
            if (intercambio.estado == EstadoDeIntercambioReal.POR_ESCRIBIR) {
                val cambios = reales.cambios(id)
                _uiState.update {
                    it.copy(
                        etapa = EtapaDeIntercambioReal.REVISION,
                        cambios = cambios,
                        frasesDeCambiosA = frasesDeListaDeCambios(cambios.cambiosA, null, cambios.resumenA, textos),
                        frasesDeCambiosB = frasesDeListaDeCambios(cambios.cambiosB, null, cambios.resumenB, textos),
                    )
                }
                return
            }

            val sesionActual = sesion.value
            val romAObtenida = if (sesionActual != null) roms.traer(sesionActual, juegoAInfo) else roms.rom(juegoA)
            val romBObtenida = if (sesionActual != null) roms.traer(sesionActual, juegoBInfo) else roms.rom(juegoB)
            if (romAObtenida == null || romBObtenida == null) {
                _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.FALTA_CONSOLA) }
                return
            }
            romA = romAObtenida
            romB = romBObtenida
            _uiState.update {
                it.copy(
                    etapa = EtapaDeIntercambioReal.INSTRUCCIONES,
                    necesitaPokedexNacional = necesitaPokedexNacional(juegoAInfo.resumen?.version, juegoBInfo.resumen?.version),
                    vecesGuardadaA = intercambio.vecesGuardadaA,
                    vecesGuardadaB = intercambio.vecesGuardadaB,
                )
            }
        } catch (e: ErrorDeRom) {
            _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.ERROR_DE_ROM, mensajeDeErrorDeRom = mensajeDeErrorDeRom(e)) }
        } catch (e: ErrorDeJuego) {
            _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.ERROR_AL_EMPEZAR, mensajeDeError = mensajeDeErrorDeJuego(e)) }
        } catch (e: Exception) {
            _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.ERROR_DE_RED, mensajeDeError = e.message ?: "se perdió la conexión con la consola") }
        }
    }

    /** Reintenta desde [EtapaDeIntercambioReal.FALTA_CONSOLA], [EtapaDeIntercambioReal.ERROR_DE_ROM], [EtapaDeIntercambioReal.ERROR_AL_EMPEZAR] o [EtapaDeIntercambioReal.ERROR_DE_RED] (antes de jugar). */
    fun reintentar() {
        viewModelScope.launch {
            val juegos = repositorio.juegos().first()
            val juegoAInfo = juegos.firstOrNull { it.id == juegoA } ?: return@launch
            val juegoBInfo = juegos.firstOrNull { it.id == juegoB } ?: return@launch
            _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.CARGANDO, mensajeDeError = null) }
            iniciar(juegoAInfo, juegoBInfo)
        }
    }

    /** "Empezar a jugar" en la pantalla de instrucciones. */
    fun empezarAJugar() {
        viewModelScope.launch {
            val archivoA = romA?.archivo ?: return@launch
            val archivoB = romB?.archivo ?: return@launch
            try {
                val (bytesA, bytesB) = reales.partidas(id)
                val nuevaSala = abridor.abrir(listOf(JuegoParaSala(archivoA, bytesA), JuegoParaSala(archivoB, bytesB)), conCable = true)
                sala = nuevaSala
                salaTerminada = false
                nuevaSala.arrancar()
                nuevaSala.sonidoDe(0)
                ultimosVecesGuardado[0] = nuevaSala.vecesQueGuardo(0)
                ultimosVecesGuardado[1] = nuevaSala.vecesQueGuardo(1)
                _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.JUGANDO, jugadorControlado = 0, conSonido = nuevaSala.conSonido) }
                empezarSondeo()
            } catch (e: Exception) {
                _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.ERROR_DE_RED, mensajeDeError = e.message ?: "no se pudo abrir el juego") }
            }
        }
    }

    private fun empezarSondeo() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val s = sala ?: break
                for (jugador in 0..1) {
                    val veces = s.vecesQueGuardo(jugador)
                    if (veces != ultimosVecesGuardado[jugador]) {
                        ultimosVecesGuardado[jugador] = veces
                        s.partidaGuardada(jugador)?.let { bytes ->
                            ultimasGuardadas[jugador] = bytes
                            runCatching { reales.guardar(id, jugador, bytes) }.onSuccess { intercambio ->
                                _uiState.update { it.copy(vecesGuardadaA = intercambio.vecesGuardadaA, vecesGuardadaB = intercambio.vecesGuardadaB) }
                            }
                        }
                    }
                }
                if (!s.conSonido) _uiState.update { it.copy(conSonido = s.reabrirSonido()) }
            }
        }
    }

    fun pausar() {
        sala?.pausar()
    }

    fun reanudar() {
        sala?.reanudar()
    }

    fun pantalla(jugador: Int, superficie: Surface?, escala: Int) {
        sala?.pantalla(jugador, superficie, escala)
    }

    fun teclas(jugador: Int, mascara: Int) {
        sala?.teclas(jugador, mascara)
    }

    /** Tocar una de las dos pantallas: a ese jugador van los controles táctiles y de ahí sale el sonido. */
    fun controlar(jugador: Int) {
        sala?.sonidoDe(jugador)
        _uiState.update { it.copy(jugadorControlado = jugador) }
    }

    fun pedirTerminar() {
        if (_uiState.value.etapa == EtapaDeIntercambioReal.JUGANDO) {
            _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.CONFIRMAR_TERMINAR) }
        }
    }

    fun cancelarTerminar() {
        _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.JUGANDO) }
    }

    fun confirmarTerminar() {
        _uiState.update { it.copy(procesando = true) }
        viewModelScope.launch {
            guardarLoQueCambioAlTerminar(resultadosDeTerminarSala())
            try {
                reales.terminarDeJugar(id)
                val cambios = reales.cambios(id)
                _uiState.update {
                    it.copy(
                        procesando = false,
                        etapa = EtapaDeIntercambioReal.REVISION,
                        cambios = cambios,
                        frasesDeCambiosA = frasesDeListaDeCambios(cambios.cambiosA, null, cambios.resumenA, textos),
                        frasesDeCambiosB = frasesDeListaDeCambios(cambios.cambiosB, null, cambios.resumenB, textos),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(procesando = false, etapa = EtapaDeIntercambioReal.ERROR_DE_RED, mensajeDeError = e.message ?: "no se pudo terminar") }
            }
        }
    }

    /** "Escribir en la consola" desde la revisión. Sin conexión, no escribe y queda pendiente (se puede reintentar). */
    fun escribir() {
        val sesionActual = sesion.value
        if (sesionActual == null) {
            _uiState.update { it.copy(mensajeDeError = "No hay conexión con la consola: el intercambio queda pendiente. Conéctala y vuelve a intentar.") }
            return
        }
        if (_uiState.value.procesando) return
        _uiState.update { it.copy(procesando = true, mensajeDeError = null) }
        viewModelScope.launch {
            try {
                reales.escribir(sesionActual, id)
                _uiState.update { it.copy(procesando = false, etapa = EtapaDeIntercambioReal.ESCRITO) }
            } catch (e: ErrorDeIntercambio) {
                _uiState.update { it.copy(procesando = false, mensajeDeError = mensajeDeErrorDeIntercambio(e)) }
            } catch (e: ErrorDeEscritura) {
                _uiState.update { it.copy(procesando = false, mensajeDeError = mensajeDeErrorDeEscritura(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(procesando = false, mensajeDeError = e.message ?: "se perdió la conexión con la consola") }
            }
        }
    }

    fun pedirDescartar() {
        _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.CONFIRMAR_DESCARTAR) }
    }

    fun cancelarDescartar() {
        _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.REVISION) }
    }

    fun confirmarDescartar() {
        viewModelScope.launch {
            reales.descartar(id)
            _uiState.update { it.copy(etapa = EtapaDeIntercambioReal.DESCARTADO) }
        }
    }

    fun descartarError() {
        _uiState.update { it.copy(mensajeDeError = null) }
    }

    private fun resultadosDeTerminarSala(): List<ByteArray?> {
        pollJob?.cancel()
        val s = sala ?: return emptyList()
        if (salaTerminada) return emptyList()
        salaTerminada = true
        val resultados = s.terminar()
        sala = null
        return resultados
    }

    private suspend fun guardarLoQueCambioAlTerminar(resultados: List<ByteArray?>) {
        resultados.forEachIndexed { jugador, bytes ->
            if (bytes != null && !bytes.contentEquals(ultimasGuardadas.getOrNull(jugador))) {
                ultimasGuardadas[jugador] = bytes
                runCatching { reales.guardar(id, jugador, bytes) }
            }
        }
    }

    override fun onCleared() {
        val resultados = resultadosDeTerminarSala()
        if (resultados.isEmpty()) return
        alcanceDeLaApp.launch {
            withContext(NonCancellable) {
                guardarLoQueCambioAlTerminar(resultados)
            }
        }
    }
}
