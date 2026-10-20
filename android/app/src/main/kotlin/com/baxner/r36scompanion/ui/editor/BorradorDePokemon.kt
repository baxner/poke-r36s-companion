package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.editor.CambioDeEstadisticas
import com.baxner.r36scompanion.data.editor.CambioDePokemon
import com.baxner.r36scompanion.data.editor.Estadisticas
import com.baxner.r36scompanion.data.editor.PokemonEditable

/**
 * Lo que la pantalla del editor de un Pokémon trae en pantalla mientras el usuario lo edita: un espejo
 * de los campos de [PokemonEditable] que de verdad se pueden tocar (ver el encargo de esta fase,
 * punto 5: especie, nivel, mote, naturaleza, shiny, objeto, movimientos, IVs, EVs, Poké Ball,
 * amistad — sin género ni habilidad, que el encargo no pide mostrar). [movimientos] siempre tiene
 * 4 elementos (0 = casilla vacía). Se manda completo a `probar`/`aplicar` en cada cambio (en vez de
 * solo el campo que cambió) para que el resultado de `probar` siempre refleje TODO lo editado
 * hasta ese momento, no solo el último campo.
 */
data class BorradorDePokemon(
    val especie: Int?,
    val naturaleza: Int,
    val objeto: Int,
    val pokeBall: Int,
    val amistad: Int,
    val esShiny: Boolean,
    val mote: String,
    val nivel: Int,
    val movimientos: List<Int>,
    val ivs: Estadisticas,
    val evs: Estadisticas,
)

fun PokemonEditable.aBorrador(): BorradorDePokemon = BorradorDePokemon(
    especie = especie,
    naturaleza = naturaleza,
    objeto = objeto,
    pokeBall = pokeBall,
    amistad = amistad,
    esShiny = esShiny,
    mote = mote,
    nivel = nivel,
    movimientos = movimientos.map { it.id }.let { ids -> (ids + List(4) { 0 }).take(4) },
    ivs = ivs,
    evs = evs,
)

fun BorradorDePokemon.aCambio(): CambioDePokemon = CambioDePokemon(
    especie = especie,
    naturaleza = naturaleza,
    objeto = objeto,
    pokeBall = pokeBall,
    amistad = amistad,
    esShiny = esShiny,
    mote = mote,
    nivel = nivel,
    movimientos = movimientos,
    ivs = ivs.aCambioDeEstadisticas(),
    evs = evs.aCambioDeEstadisticas(),
)

private fun Estadisticas.aCambioDeEstadisticas(): CambioDeEstadisticas = CambioDeEstadisticas(
    ps = ps,
    ataque = ataque,
    defensa = defensa,
    ataqueEspecial = ataqueEspecial,
    defensaEspecial = defensaEspecial,
    velocidad = velocidad,
)
