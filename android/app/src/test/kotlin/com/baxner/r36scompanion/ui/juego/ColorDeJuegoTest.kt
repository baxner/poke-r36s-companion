package com.baxner.r36scompanion.ui.juego

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorDeJuegoTest {
    @Test
    fun cadaJuegoDeGen3TieneSuPropioColor() {
        assertEquals(ColorDeJuego.ROJO_FUEGO, colorDeJuego("FireRed"))
        assertEquals(ColorDeJuego.VERDE_HOJA, colorDeJuego("LeafGreen"))
        assertEquals(ColorDeJuego.ESMERALDA, colorDeJuego("Emerald"))
        assertEquals(ColorDeJuego.RUBI, colorDeJuego("Ruby"))
        assertEquals(ColorDeJuego.ZAFIRO, colorDeJuego("Sapphire"))
    }

    @Test
    fun unJuegoQueNoEsDePokemonEsNeutro() {
        assertEquals(ColorDeJuego.NEUTRO, colorDeJuego(null))
        assertEquals(ColorDeJuego.NEUTRO, colorDeJuego("Unbound"))
    }
}
