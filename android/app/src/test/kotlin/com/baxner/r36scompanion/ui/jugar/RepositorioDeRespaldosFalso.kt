package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.respaldos.ProgresoDeRespaldo
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResultadoDeRespaldo
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [RepositorioDeRespaldos] en memoria, solo con lo que hace falta para [SeguirJugandoViewModel] e
 * [IntercambioRealViewModel] (nombre y resumen del juego): nada de SFTP ni de la consola de
 * verdad (ver AGENTS.md).
 */
class RepositorioDeRespaldosFalso(juegosIniciales: List<JuegoRespaldado> = emptyList()) : RepositorioDeRespaldos {
    private val _juegos: MutableStateFlow<List<JuegoRespaldado>> = MutableStateFlow(juegosIniciales)
    val juegosFlow: StateFlow<List<JuegoRespaldado>> = _juegos

    fun ponerJuegos(juegos: List<JuegoRespaldado>) {
        _juegos.value = juegos
    }

    override fun juegos(): StateFlow<List<JuegoRespaldado>> = _juegos

    override fun versiones(juegoId: Long): StateFlow<List<VersionRespaldada>> = MutableStateFlow(emptyList())

    override suspend fun bytesDe(versionId: Long): ByteArray = ByteArray(0)

    override suspend fun respaldarTodo(sesion: SesionDeConsola, progreso: (ProgresoDeRespaldo) -> Unit): ResultadoDeRespaldo =
        ResultadoDeRespaldo(nuevas = emptyList(), sinCambios = 0, fallidos = emptyList())

    override suspend fun exportarZip(destino: OutputStream, juegos: List<Long>?) {
        destino.close()
    }
}
