package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.banco.Banco
import com.baxner.r36scompanion.data.banco.MovimientoPreparado
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.editor.Casilla
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [Banco] falso para probar [PokedexGlobalConRespaldos]: a la Pokédex global solo le importa
 * [pokemon] (de dónde saca las ubicaciones "en el banco"); el resto de operaciones del banco no
 * las usa, así que ni falta implementarlas de verdad.
 */
class BancoFalso(private val lista: List<PokemonEnBanco>) : Banco {
    override fun pokemon(): Flow<List<PokemonEnBanco>> = flowOf(lista)

    override suspend fun prepararDeposito(juegoId: Long, partida: ByteArray, nombreDeArchivo: String, casilla: Casilla): MovimientoPreparado =
        error("BancoFalso no implementa prepararDeposito")

    override suspend fun prepararRetiro(pokemonId: Long, juegoId: Long, partida: ByteArray, nombreDeArchivo: String): MovimientoPreparado =
        error("BancoFalso no implementa prepararRetiro")

    override suspend fun confirmar(movimiento: MovimientoPreparado) = error("BancoFalso no implementa confirmar")

    override suspend fun cancelar(movimiento: MovimientoPreparado) = error("BancoFalso no implementa cancelar")

    override suspend fun pendientes(): List<PokemonEnBanco> = lista.filter { it.estado.name != "EN_BANCO" }

    override suspend fun conciliar(pokemonId: Long, partidaEnConsola: ByteArray, nombreDeArchivo: String) = Unit

    override suspend fun mover(pokemonId: Long, caja: Int, posicion: Int) = Unit
}
