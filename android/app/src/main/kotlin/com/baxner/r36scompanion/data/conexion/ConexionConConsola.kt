package com.baxner.r36scompanion.data.conexion

import com.baxner.r36scompanion.data.consola.Anclaje
import com.baxner.r36scompanion.data.consola.BuscadorDeConsola
import com.baxner.r36scompanion.data.consola.Candidata
import com.baxner.r36scompanion.data.consola.ConectorDeConsola
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.consola.Credenciales
import com.baxner.r36scompanion.data.consola.ErrorDeConsola
import com.baxner.r36scompanion.data.consola.EstadoDelAnclaje
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** En qué va la conexión con la consola: lo que muestra la tarjeta de conexión del inicio. */
sealed interface EstadoDeConexion {
    /** El teléfono no está conectado por USB a nada. */
    data object SinCable : EstadoDeConexion

    /** Hay cable, pero el anclaje de USB está apagado (con un cable C a C sale en gris: ver Introducción). */
    data object SinAnclaje : EstadoDeConexion

    data object Buscando : EstadoDeConexion

    /** Hay anclaje, pero nadie contesta por SSH: casi siempre falta "Enable Remote Services". */
    data object NoEncontrada : EstadoDeConexion

    data class Conectada(val consola: Consola, val esNueva: Boolean) : EstadoDeConexion

    /** La consola que contestó no tiene la llave fijada. No se mandó la contraseña. */
    data class HuellaDistinta(val esperada: String, val recibida: String) : EstadoDeConexion

    data object CredencialesIncorrectas : EstadoDeConexion

    data class Fallo(val mensaje: String) : EstadoDeConexion
}

/** La consola fijada y cómo entrar a ella. En Android, cifrado (ver PreferenciasDeConsolaCifradas). */
interface PreferenciasDeConsola {
    /** Huella SSH de la consola fijada (`SHA256:…`), o null si nunca se conectó. */
    var huellaFijada: String?
    var nombreDeEquipo: String?
    var credenciales: Credenciales
}

/**
 * Une anclaje, búsqueda y conexión, y mantiene abierta una [sesion] mientras la consola esté
 * conectada. La primera vez fija sola la huella de la consola (el anclaje USB es un cable
 * directo entre el teléfono y la consola: no hay nadie en medio); desde ahí la exige, y si otra
 * llave contesta queda en [EstadoDeConexion.HuellaDistinta] hasta que el usuario decida
 * ([olvidarConsolaYReconectar]).
 *
 * Una sola instancia por proceso: la sesión es compartida por respaldos, editor e intercambios.
 */
class ConexionConConsola(
    private val anclaje: Anclaje,
    private val buscador: BuscadorDeConsola,
    private val conector: ConectorDeConsola,
    private val preferencias: PreferenciasDeConsola,
    private val alcance: CoroutineScope,
) {
    private val _estado = MutableStateFlow<EstadoDeConexion>(estadoSinRed(anclaje.estadoActual()))
    val estado: StateFlow<EstadoDeConexion> = _estado.asStateFlow()

    private val _sesion = MutableStateFlow<SesionDeConsola?>(null)

    /** La sesión abierta, o null si no hay consola conectada. */
    val sesion: StateFlow<SesionDeConsola?> = _sesion.asStateFlow()

    private val turno = Mutex()
    private var observador: Job? = null
    private var ultimoAnclaje: EstadoDelAnclaje = anclaje.estadoActual()

    /** Empieza a seguir el anclaje: al activarse busca y conecta, al apagarse cierra la sesión. */
    fun iniciar() {
        if (observador != null) return
        observador = alcance.launch {
            anclaje.observar()
                .distinctUntilChangedBy { Triple(it.cableConectado, it.anclajeActivo, it.interfaces.map { i -> i.direccion }) }
                .collect { nuevo ->
                    ultimoAnclaje = nuevo
                    if (nuevo.anclajeActivo) conectar() else desconectar(estadoSinRed(nuevo))
                }
        }
    }

    fun detener() {
        observador?.cancel()
        observador = null
        alcance.launch { desconectar(estadoSinRed(ultimoAnclaje)) }
    }

    /** Vuelve a buscar y conectar (botón "Reintentar", o tras un fallo de una operación). */
    fun reintentar() {
        alcance.launch {
            if (ultimoAnclaje.anclajeActivo) {
                desconectar(EstadoDeConexion.Buscando)
                conectar()
            }
        }
    }

    /** Después de [EstadoDeConexion.HuellaDistinta], si el usuario dice que sí es su consola (la reinstaló). */
    fun olvidarConsolaYReconectar() {
        preferencias.huellaFijada = null
        preferencias.nombreDeEquipo = null
        reintentar()
    }

    fun abrirAjustesDeAnclaje(): Boolean = anclaje.abrirAjustesDeAnclaje()

    /** Una operación falló por red (la consola se apagó, se desconectó el cable): cierra y vuelve a buscar. */
    fun reportarFalloDeRed() = reintentar()

    private suspend fun conectar() = turno.withLock {
        if (_sesion.value != null) return@withLock
        _estado.value = EstadoDeConexion.Buscando
        val candidatas = runCatching { buscador.buscar() }.getOrElse {
            _estado.value = EstadoDeConexion.Fallo(it.message ?: "no se pudo buscar la consola")
            return@withLock
        }
        if (candidatas.isEmpty()) {
            _estado.value = EstadoDeConexion.NoEncontrada
            return@withLock
        }
        _estado.value = intentarCandidatas(candidatas)
    }

    private suspend fun intentarCandidatas(candidatas: List<Candidata>): EstadoDeConexion {
        var mejorFallo: EstadoDeConexion = EstadoDeConexion.NoEncontrada
        for (candidata in candidatas) {
            val fijada = preferencias.huellaFijada
            try {
                val sesion = conector.conectar(candidata, preferencias.credenciales, fijada)
                if (fijada == null) {
                    preferencias.huellaFijada = sesion.consola.huella
                }
                preferencias.nombreDeEquipo = sesion.consola.nombreDeEquipo
                _sesion.value = sesion
                return EstadoDeConexion.Conectada(sesion.consola, esNueva = fijada == null)
            } catch (e: ErrorDeConsola.HuellaDistinta) {
                // Puede haber otro equipo con SSH en la misma red (raro en un cable directo): se
                // prueban los demás, pero si ninguno es la consola, esto es lo que se muestra.
                mejorFallo = EstadoDeConexion.HuellaDistinta(e.esperada, e.recibida)
            } catch (_: ErrorDeConsola.CredencialesIncorrectas) {
                if (mejorFallo !is EstadoDeConexion.HuellaDistinta) mejorFallo = EstadoDeConexion.CredencialesIncorrectas
            } catch (e: ErrorDeConsola) {
                if (mejorFallo == EstadoDeConexion.NoEncontrada) mejorFallo = EstadoDeConexion.Fallo(e.message ?: "no se pudo conectar")
            }
        }
        return mejorFallo
    }

    private suspend fun desconectar(nuevoEstado: EstadoDeConexion) = turno.withLock {
        _sesion.value?.let { runCatching { it.close() } }
        _sesion.value = null
        _estado.value = nuevoEstado
    }

    private fun estadoSinRed(anclaje: EstadoDelAnclaje): EstadoDeConexion = when {
        anclaje.anclajeActivo -> EstadoDeConexion.Buscando
        anclaje.cableConectado -> EstadoDeConexion.SinAnclaje
        else -> EstadoDeConexion.SinCable
    }
}
