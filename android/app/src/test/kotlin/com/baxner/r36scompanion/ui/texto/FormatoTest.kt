package com.baxner.r36scompanion.ui.texto

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoTest {
    @Test
    fun horasYMinutosConHoras() {
        assertEquals("34 h 12 min", formatoDeHorasYMinutos(34, 12))
    }

    @Test
    fun horasYMinutosSinHoras() {
        assertEquals("45 min", formatoDeHorasYMinutos(0, 45))
    }

    @Test
    fun duracionEnMinutosPartideEnHorasYMinutos() {
        assertEquals("2 h 10 min", formatoDeDuracionEnMinutos(130))
        assertEquals("45 min", formatoDeDuracionEnMinutos(45))
    }

    @Test
    fun tamanoEnMb() {
        assertEquals("1.0 MB", formatoDeTamanoEnMb(1024L * 1024))
        assertEquals("2.5 MB", formatoDeTamanoEnMb((2.5 * 1024 * 1024).toLong()))
        assertEquals("0.0 MB", formatoDeTamanoEnMb(0L))
    }
}
