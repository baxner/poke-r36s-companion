package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.intercambio.ErrorDeIntercambio
import com.baxner.r36scompanion.data.jugar.CambiosDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.ErrorDeJuego
import com.baxner.r36scompanion.data.jugar.EstadoDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.IntercambioReal
import com.baxner.r36scompanion.data.jugar.IntercambiosReales
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [IntercambiosReales] en memoria para las pruebas de [IntercambioRealViewModel] (ver AGENTS.md). */
class IntercambiosRealesFalsos : IntercambiosReales {
    private val _pendiente = MutableStateFlow<IntercambioReal?>(null)
    private val partidasPorId = mutableMapOf<Long, Pair<ByteArray, ByteArray>>()
    private var siguienteId = 1L

    var errorAlEmpezar: ErrorDeJuego? = null
    var errorAlEscribir: Throwable? = null
    var cambiosARespoonder: CambiosDeIntercambioReal = CambiosDeIntercambioReal(null, null, emptyList(), emptyList())

    val historialDeGuardados = mutableListOf<Triple<Long, Int, ByteArray>>()
    var vecesLlamadoEscribir = 0
    var vecesLlamadoDescartar = 0

    /** Dado uno ya existente (p. ej. para probar "retomar un intercambio sin terminar"). */
    fun ponerPendiente(intercambio: IntercambioReal, partidaA: ByteArray, partidaB: ByteArray) {
        siguienteId = maxOf(siguienteId, intercambio.id + 1)
        partidasPorId[intercambio.id] = partidaA to partidaB
        _pendiente.value = intercambio
    }

    override fun pendiente(): StateFlow<IntercambioReal?> = _pendiente

    override suspend fun empezar(juegoA: Long, juegoB: Long): IntercambioReal {
        errorAlEmpezar?.let { throw it }
        if (_pendiente.value != null) throw ErrorDeJuego.HayOtroIntercambio(_pendiente.value!!.id)
        val nuevo = IntercambioReal(
            id = siguienteId++,
            juegoA = juegoA,
            baseA = null,
            juegoB = juegoB,
            baseB = null,
            empezado = 1_000L,
            vecesGuardadaA = 0,
            vecesGuardadaB = 0,
            estado = EstadoDeIntercambioReal.JUGANDO,
        )
        partidasPorId[nuevo.id] = ByteArray(16) to ByteArray(16)
        _pendiente.value = nuevo
        return nuevo
    }

    override suspend fun partidas(id: Long): Pair<ByteArray, ByteArray> = partidasPorId[id] ?: (ByteArray(0) to ByteArray(0))

    override suspend fun guardar(id: Long, jugador: Int, bytes: ByteArray): IntercambioReal {
        historialDeGuardados += Triple(id, jugador, bytes)
        val actual = _pendiente.value ?: throw ErrorDeJuego.NoEstaEnElTelefono(id)
        val anterior = partidasPorId[id] ?: (ByteArray(0) to ByteArray(0))
        partidasPorId[id] = if (jugador == 0) bytes to anterior.second else anterior.first to bytes
        val nuevo = if (jugador == 0) actual.copy(vecesGuardadaA = actual.vecesGuardadaA + 1) else actual.copy(vecesGuardadaB = actual.vecesGuardadaB + 1)
        _pendiente.value = nuevo
        return nuevo
    }

    override suspend fun terminarDeJugar(id: Long): IntercambioReal {
        val nuevo = (_pendiente.value ?: throw ErrorDeJuego.NoEstaEnElTelefono(id)).copy(estado = EstadoDeIntercambioReal.POR_ESCRIBIR)
        _pendiente.value = nuevo
        return nuevo
    }

    override suspend fun cambios(id: Long): CambiosDeIntercambioReal = cambiosARespoonder

    override suspend fun escribir(sesion: SesionDeConsola, id: Long) {
        vecesLlamadoEscribir++
        errorAlEscribir?.let { throw it }
        _pendiente.value = null
    }

    override suspend fun descartar(id: Long) {
        vecesLlamadoDescartar++
        _pendiente.value = null
    }
}

/** Un [ErrorDeIntercambio.SeDeshizo] listo para lanzar en las pruebas. */
fun seDeshizoDePrueba(): ErrorDeIntercambio.SeDeshizo = ErrorDeIntercambio.SeDeshizo(causa = RuntimeException("falló la escritura de la segunda partida"))
