package com.baxner.r36scompanion.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class BolsillosGen3Test {
    @Test
    fun `los seis bolsillos de una partida de gen 3, en espanol`() {
        assertEquals("Objetos", nombreDeBolsillo("Items"))
        assertEquals("Objetos clave", nombreDeBolsillo("KeyItems"))
        assertEquals("Poké Balls", nombreDeBolsillo("Balls"))
        assertEquals("MT y MO", nombreDeBolsillo("TMHMs"))
        assertEquals("Bayas", nombreDeBolsillo("Berries"))
        assertEquals("PC", nombreDeBolsillo("PCItems"))
    }

    @Test
    fun `un bolsillo desconocido se muestra tal cual`() {
        assertEquals("Mail", nombreDeBolsillo("Mail"))
    }
}
