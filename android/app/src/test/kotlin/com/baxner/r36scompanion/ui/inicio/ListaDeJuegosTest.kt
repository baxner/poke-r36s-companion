package com.baxner.r36scompanion.ui.inicio

import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import org.junit.Assert.assertEquals
import org.junit.Test

class ListaDeJuegosTest {
    private fun juego(
        id: Long,
        nombre: String,
        esPokemon: Boolean,
        editable: Boolean = false,
        estadoDePartida: EstadoDePartida = EstadoDePartida.NO_APLICA,
    ) = JuegoRespaldado(
        id = id,
        sistema = "gba",
        nombre = nombre,
        rutaDePartida = "/roms/gba/$nombre.srm",
        esPokemon = esPokemon,
        editable = editable,
        ultimoRespaldo = 1_700_000_000_000L,
        resumen = null,
        estadoDePartida = estadoDePartida,
    )

    @Test
    fun separaPokemonDeOtrosConservandoElOrden() {
        val rojoFuego = juego(1, "Pokemon Edicion Rojo Fuego", esPokemon = true, editable = true, estadoDePartida = EstadoDePartida.CON_DATOS)
        val siete = juego(2, "007", esPokemon = false)
        val verdeHoja = juego(3, "Pokemon Edicion Verde Hoja", esPokemon = true, estadoDePartida = EstadoDePartida.VACIA)
        val avatar = juego(4, "Avatar", esPokemon = false)

        val organizados = organizarJuegos(listOf(rojoFuego, siete, verdeHoja, avatar))

        assertEquals(listOf(rojoFuego, verdeHoja), organizados.pokemon)
        assertEquals(listOf(siete, avatar), organizados.otros)
    }

    @Test
    fun unJuegoQueNoEsPokemonNuncaTieneAviso() {
        assertEquals(AvisoDeJuego.NINGUNO, avisoDe(juego(1, "Avatar", esPokemon = false)))
    }

    @Test
    fun partidaVaciaAvisaAunqueTambienSeaNoEditable() {
        val verdeHoja = juego(1, "Pokemon Edicion Verde Hoja", esPokemon = true, editable = false, estadoDePartida = EstadoDePartida.VACIA)
        assertEquals("la partida vacía es el aviso que importa, no el genérico de hack", AvisoDeJuego.PARTIDA_VACIA, avisoDe(verdeHoja))
    }

    @Test
    fun unHackQueElMotorNoReconoceEsSoloRespaldo() {
        val hack = juego(1, "Pokemon Lets Go Eevee", esPokemon = true, editable = false, estadoDePartida = EstadoDePartida.CON_DATOS)
        assertEquals(AvisoDeJuego.SOLO_RESPALDO, avisoDe(hack))
    }

    @Test
    fun unaPartidaDePokemonEditableNoTieneAviso() {
        val rojoFuego = juego(1, "Pokemon Edicion Rojo Fuego", esPokemon = true, editable = true, estadoDePartida = EstadoDePartida.CON_DATOS)
        assertEquals(AvisoDeJuego.NINGUNO, avisoDe(rojoFuego))
    }
}
