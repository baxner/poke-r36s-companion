package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.editor.Casilla

/*
 * Contrato de la Pokédex global (fase 4). Lo escribe Opus. Junta las 386 especies de la 3.ª
 * generación entre todas las partidas respaldadas (la última versión de cada partida de Pokémon
 * que el motor reconoce) y el banco:
 * - "registrado" (visto / atrapado) sale de la Pokédex de cada juego, con `PartidaGen3.pokedex()`
 *   (coincide con los conteos del motor: 78 atrapados y 111 vistos en el Rojo Fuego del usuario);
 * - "lo tienes" sale de dónde está cada Pokémon ahora (`party.get` y `box.get` del motor) y del banco;
 * - "dónde atraparlo" sale de `encounters.search(versión, especie)` del motor, para los juegos que
 *   el usuario tiene.
 * Nombres en español con `Textos` (especies y lugares de la 3.ª generación).
 */

/** Dónde está un Pokémon que el usuario tiene ahora. */
sealed interface Ubicacion {
    data class EnPartida(val juegoId: Long, val casilla: Casilla, val nivel: Int, val esShiny: Boolean) : Ubicacion
    data class EnBanco(val pokemonId: Long, val nivel: Int, val esShiny: Boolean) : Ubicacion
}

data class EntradaDePokedex(
    /** Número de la Pokédex Nacional (1-386). */
    val especie: Int,
    /** Juegos (id) donde la Pokédex del juego la registra como vista / atrapada. */
    val vistaEn: Set<Long>,
    val atrapadaEn: Set<Long>,
    /** Dónde la tiene ahora (puede estar registrada como atrapada y ya no tenerla). */
    val ubicaciones: List<Ubicacion>,
) {
    val laTiene: Boolean get() = ubicaciones.isNotEmpty()
    val registrada: Boolean get() = atrapadaEn.isNotEmpty()
}

/** Un lugar donde se puede atrapar, de `encounters.search` (lugar y nivel vienen en inglés del motor). */
data class DondeAtrapar(val juegoId: Long, val versionId: Int, val lugar: String, val niveles: String, val esHuevo: Boolean)

data class ResumenDePokedex(val total: Int, val registradas: Int, val tenidas: Int, val vistas: Int)

interface PokedexGlobal {
    /** Las 386 entradas, en orden de la Pokédex Nacional. */
    suspend fun entradas(): List<EntradaDePokedex>

    suspend fun resumen(): ResumenDePokedex

    /** Dónde atraparla en los juegos del usuario (vacío si solo sale por intercambio o evento). */
    suspend fun dondeAtrapar(especie: Int): List<DondeAtrapar>
}
