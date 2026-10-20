package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.respaldos.EscritorDePartidas

/**
 * [EjecutorDeIntercambio] real, sobre [EscritorDePartidas] (ver su KDoc y el de `Intercambio.kt`).
 * Escribe A y luego B; si B falla, restaura A a la versión de antes del intercambio
 * ([com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura.copiaPrevia] de la escritura de
 * A) para que ningún Pokémon quede duplicado ni perdido.
 */
class EjecutorDeIntercambioConEscritor(private val escritor: EscritorDePartidas) : EjecutorDeIntercambio {

    override suspend fun escribirEnLaConsola(
        sesion: SesionDeConsola,
        juegoA: Long,
        baseA: String,
        juegoB: Long,
        baseB: String,
        partidas: PartidasIntercambiadas,
    ) {
        val resultadoA = escritor.escribir(sesion, juegoA, partidas.partidaA, baseA)
        try {
            escritor.escribir(sesion, juegoB, partidas.partidaB, baseB)
        } catch (fallaB: Exception) {
            val versionPreviaDeA = resultadoA.copiaPrevia?.id
                ?: throw ErrorDeIntercambio.QuedoAMedias(juegoA, -1, fallaB) // A no tenía partida antes: no hay nada a qué restaurarla.

            val restaurada = runCatching { escritor.restaurar(sesion, versionPreviaDeA) }
            if (restaurada.isSuccess) throw ErrorDeIntercambio.SeDeshizo(fallaB)
            throw ErrorDeIntercambio.QuedoAMedias(juegoA, versionPreviaDeA, restaurada.exceptionOrNull()!!)
        }
    }
}
