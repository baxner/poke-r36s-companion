package com.baxner.r36scompanion.ui.pokedex

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.pokedex.DondeAtrapar
import com.baxner.r36scompanion.data.pokedex.Ubicacion
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.textos.Textos

/*
 * Textos del detalle de una especie (ver el encargo, punto 2): dónde está cada ejemplar y dónde
 * atraparla. Puras: se prueban sin Android. `lugar` y `niveles` de [DondeAtrapar] vienen en
 * inglés del propio motor (ver el KDoc de `PokedexGlobal.kt`: no hay una tabla de `Textos` para
 * nombres de lugar de `encounters.search`, a diferencia de `lugarGen3` que traduce por id el
 * "lugar de captura" ya grabado en un Pokémon); se muestran tal cual, como etiqueta técnica.
 */

/** "Caja 3, casilla 12" o "En el equipo, posición 2" (1-based para mostrar; el dominio es 0-based). */
fun Casilla.aTexto(): String = when (origen) {
    OrigenDeCasilla.EQUIPO -> "en el equipo, posición ${posicion + 1}"
    OrigenDeCasilla.CAJA -> "caja ${(caja ?: 0) + 1}, casilla ${posicion + 1}"
}

/** El nombre para mostrar de un juego: el de su partida (traducido) si ya se leyó, si no el de la ROM. */
fun JuegoRespaldado.nombreParaMostrar(textos: Textos): String = resumen?.let { textos.juego(it.version) } ?: nombre

data class TextoDeUbicacion(val lugar: String, val detalle: String)

/** Dónde está un ejemplar que el usuario tiene ahora, para una fila de la lista del detalle de especie. */
fun Ubicacion.aTexto(juegos: Map<Long, JuegoRespaldado>, textos: Textos): TextoDeUbicacion {
    val sufijoShiny = if (esShinyDe(this)) ", shiny" else ""
    return when (this) {
        is Ubicacion.EnPartida -> TextoDeUbicacion(
            lugar = juegos[juegoId]?.nombreParaMostrar(textos) ?: "un juego",
            detalle = "${casilla.aTexto()} — Nv $nivel$sufijoShiny",
        )
        is Ubicacion.EnBanco -> TextoDeUbicacion(lugar = "En el banco", detalle = "Nv $nivel$sufijoShiny")
    }
}

private fun esShinyDe(ubicacion: Ubicacion): Boolean = when (ubicacion) {
    is Ubicacion.EnPartida -> ubicacion.esShiny
    is Ubicacion.EnBanco -> ubicacion.esShiny
}

/** "Pokemon Edicion Rojo Fuego: Viridian Forest (niveles 3-5)" o "… por huevo (Day Care)". */
fun DondeAtrapar.aTexto(juegos: Map<Long, JuegoRespaldado>, textos: Textos): String {
    val nombreJuego = juegos[juegoId]?.nombreParaMostrar(textos) ?: textos.juego(versionId) ?: "un juego"
    return if (esHuevo) "$nombreJuego: por huevo ($lugar)" else "$nombreJuego: $lugar (niveles $niveles)"
}
