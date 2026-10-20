package com.baxner.r36scompanion.ui.inicio

import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado

/** Los juegos de Inicio, ya separados (ver el encargo, punto 3: "primero los de Pokémon… Después, Otros juegos"). */
data class JuegosOrganizados(val pokemon: List<JuegoRespaldado>, val otros: List<JuegoRespaldado>)

/**
 * Separa [juegos] en Pokémon / otros, conservando el orden que ya trae
 * [com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos.juegos] (del más reciente al
 * más viejo). Pura: ordenar y filtrar no necesita Android ni la consola.
 */
fun organizarJuegos(juegos: List<JuegoRespaldado>): JuegosOrganizados {
    val (pokemon, otros) = juegos.partition { it.esPokemon }
    return JuegosOrganizados(pokemon, otros)
}

/** El aviso (si alguno) que se muestra bajo un juego de Pokémon en la lista de Inicio. */
enum class AvisoDeJuego { NINGUNO, PARTIDA_VACIA, SOLO_RESPALDO }

/**
 * Qué aviso mostrar bajo un juego de Pokémon (ver el encargo, punto 3). Una partida vacía
 * siempre es también "no editable" (el motor no tiene nada que leer), pero el aviso que importa
 * ahí es el de la partida vacía, no el genérico de "solo respaldo" (que es para los hacks que el
 * motor sí intentó leer y no pudo, como *Pokemon Lets Go Eevee*: ver docs/plan.md). Un juego que
 * nunca se ha respaldado ([JuegoRespaldado.ultimoRespaldo] null) tampoco es "solo respaldo"
 * todavía: ese aviso es un veredicto sobre su partida, y sin haberla leído ni una vez no hay
 * veredicto que dar (se vio en una captura de Inicio: *Rouge Feu (F)*, sin respaldar, salía como
 * "Hack" sin que la app supiera nada de verdad sobre su partida).
 */
fun avisoDe(juego: JuegoRespaldado): AvisoDeJuego = when {
    !juego.esPokemon -> AvisoDeJuego.NINGUNO
    juego.ultimoRespaldo == null -> AvisoDeJuego.NINGUNO
    juego.estadoDePartida == EstadoDePartida.VACIA -> AvisoDeJuego.PARTIDA_VACIA
    !juego.editable -> AvisoDeJuego.SOLO_RESPALDO
    else -> AvisoDeJuego.NINGUNO
}

const val TextoPartidaVacia =
    "Partida vacía: tu avance solo está en el estado automático. Abre el juego en la consola y guarda desde el menú del juego."
const val TextoSoloRespaldo = "Hack: solo respaldo"
