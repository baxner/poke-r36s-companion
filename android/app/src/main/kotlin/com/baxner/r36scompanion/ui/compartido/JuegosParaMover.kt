package com.baxner.r36scompanion.ui.compartido

import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import kotlinx.coroutines.flow.first

/**
 * Los juegos de Pokémon que el motor puede editar, para elegir de dónde sale o a dónde llega un
 * Pokémon (Banco, Intercambio rápido). Mismo criterio que
 * `PokedexGlobalConRespaldos.juegosEditablesConSuUltimaPartida` (privado ahí porque esa clase no
 * expone bytes crudos; aquí sí hacen falta para preparar el movimiento y escribirlo en la consola).
 */
suspend fun RepositorioDeRespaldos.juegosDePokemonEditables(): List<JuegoRespaldado> =
    juegos().first().filter { it.editable }

/** Los bytes de la última partida (no estado) respaldada de [juegoId], o null si nunca se respaldó una. */
suspend fun RepositorioDeRespaldos.ultimaPartidaDe(juegoId: Long): ByteArray? {
    val version = versiones(juegoId).first().firstOrNull { it.tipo == TipoDeArchivo.PARTIDA } ?: return null
    return bytesDe(version.id)
}
