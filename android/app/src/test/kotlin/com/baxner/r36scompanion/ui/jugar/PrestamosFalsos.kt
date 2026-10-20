package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar
import com.baxner.r36scompanion.data.jugar.ErrorDeJuego
import com.baxner.r36scompanion.data.jugar.PartidaPrestada
import com.baxner.r36scompanion.data.jugar.Prestamos
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [Prestamos] en memoria para las pruebas de [SeguirJugandoViewModel] (ver AGENTS.md). */
class PrestamosFalsos : Prestamos {
    private val prestamosPorJuego = linkedMapOf<Long, PartidaPrestada>()
    private val partidasPorJuego = mutableMapOf<Long, ByteArray>()
    private val _prestadas = MutableStateFlow<List<PartidaPrestada>>(emptyList())

    var avisos: List<AvisoAntesDeJugar> = emptyList()
    var errorAlLlevar: ErrorDeJuego? = null
    var errorAlRegresar: Throwable? = null
    /** Si no es null, [guardar] lo lanza (p. ej. sin espacio en el teléfono). */
    var errorAlGuardar: Throwable? = null
    /** Si no es null, [guardar] espera a que se complete antes de guardar (un disco lento). */
    var guardadoDetenido: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    /** "guardar" y "regresar" en el orden en que llegaron. */
    val llamadas = mutableListOf<String>()

    /** Cada llamada a [guardar], en orden: para comprobar que se llama cuando toca y no de más. */
    val historialDeGuardados = mutableListOf<Pair<Long, ByteArray>>()
    var vecesLlamadoRegresar = 0
    var vecesLlamadoRegresarAunqueCambio = 0
    var vecesLlamadoDescartar = 0

    /** Pone una partida prestada ya existente, como si se hubiera llevado antes de la prueba. */
    fun ponerPrestamo(prestamo: PartidaPrestada, bytes: ByteArray) {
        prestamosPorJuego[prestamo.juegoId] = prestamo
        partidasPorJuego[prestamo.juegoId] = bytes
        actualizar()
    }

    override fun prestadas(): StateFlow<List<PartidaPrestada>> = _prestadas

    override suspend fun prestada(juegoId: Long): PartidaPrestada? = prestamosPorJuego[juegoId]

    override suspend fun avisosAntesDeLlevar(juegoId: Long): List<AvisoAntesDeJugar> = avisos

    override suspend fun llevar(juegoId: Long): PartidaPrestada {
        errorAlLlevar?.let { throw it }
        prestamosPorJuego[juegoId]?.let { return it }
        val nuevo = PartidaPrestada(juegoId = juegoId, base = null, desde = 1_000L, actual = null, vecesGuardada = 0, ultimaVezGuardada = null)
        prestamosPorJuego[juegoId] = nuevo
        if (juegoId !in partidasPorJuego) partidasPorJuego[juegoId] = ByteArray(0)
        actualizar()
        return nuevo
    }

    override suspend fun partida(juegoId: Long): ByteArray = partidasPorJuego[juegoId] ?: ByteArray(0)

    override suspend fun guardar(juegoId: Long, bytes: ByteArray): PartidaPrestada {
        guardadoDetenido?.await()
        errorAlGuardar?.let { throw it }
        llamadas += "guardar"
        historialDeGuardados += juegoId to bytes
        partidasPorJuego[juegoId] = bytes
        val actual = prestamosPorJuego[juegoId] ?: PartidaPrestada(juegoId, null, 1_000L, null, 0, null)
        val nuevo = actual.copy(vecesGuardada = actual.vecesGuardada + 1, ultimaVezGuardada = 2_000L + actual.vecesGuardada)
        prestamosPorJuego[juegoId] = nuevo
        actualizar()
        return nuevo
    }

    override suspend fun regresar(sesion: SesionDeConsola, juegoId: Long): ResultadoDeEscritura? {
        vecesLlamadoRegresar++
        llamadas += "regresar"
        errorAlRegresar?.let { throw it }
        prestamosPorJuego.remove(juegoId)
        actualizar()
        return null
    }

    override suspend fun regresarAunqueCambio(sesion: SesionDeConsola, juegoId: Long): ResultadoDeEscritura? {
        vecesLlamadoRegresarAunqueCambio++
        prestamosPorJuego.remove(juegoId)
        actualizar()
        return null
    }

    override suspend fun descartar(juegoId: Long) {
        vecesLlamadoDescartar++
        prestamosPorJuego.remove(juegoId)
        actualizar()
    }

    private fun actualizar() {
        _prestadas.value = prestamosPorJuego.values.toList()
    }
}

/** Un [ErrorDeEscritura.Conflicto] listo para lanzar en las pruebas (los campos no importan). */
fun conflictoDePrueba(): ErrorDeEscritura.Conflicto = ErrorDeEscritura.Conflicto(esperado = "a", encontrado = "b")

/** Un [ErrorDeEscritura.JuegoAbierto] listo para lanzar en las pruebas. */
fun juegoAbiertoDePrueba(): ErrorDeEscritura.JuegoAbierto = ErrorDeEscritura.JuegoAbierto(rom = "Pokemon Edicion Rojo Fuego")
