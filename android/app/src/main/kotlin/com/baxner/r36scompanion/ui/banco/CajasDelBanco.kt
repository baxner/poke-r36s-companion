package com.baxner.r36scompanion.ui.banco

import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.gen3.PartidaGen3

/** El banco se ve como el PC del juego: 6 columnas por fila (30 casillas por caja, como en el juego real). */
const val ColumnasDelBanco = 6
const val FilasDelBanco = PartidaGen3.CasillasPorCaja / ColumnasDelBanco

/**
 * Las 30 casillas de [caja] (de [pokemon], ya en [EstadoEnBanco.EN_BANCO]: los pendientes se
 * muestran aparte, en el banner de arriba), cada una con su Pokémon o `null` si está vacía. Pura.
 */
fun casillasDeLaCaja(pokemon: List<PokemonEnBanco>, caja: Int): List<PokemonEnBanco?> {
    val porPosicion = pokemon.filter { it.caja == caja && it.estado == EstadoEnBanco.EN_BANCO }.associateBy { it.posicion }
    return (0 until PartidaGen3.CasillasPorCaja).map { posicion -> porPosicion[posicion] }
}

/**
 * Qué cajas mostrar en el selector: todas las que tengan al menos un Pokémon, más la 0 (para que
 * el banco vacío igual muestre una caja vacía en vez de nada). Pura.
 */
fun cajasConContenido(pokemon: List<PokemonEnBanco>): List<Int> {
    val usadas = pokemon.filter { it.estado == EstadoEnBanco.EN_BANCO }.map { it.caja }
    val maximo = usadas.maxOrNull() ?: 0
    return (0..maximo).toList()
}
