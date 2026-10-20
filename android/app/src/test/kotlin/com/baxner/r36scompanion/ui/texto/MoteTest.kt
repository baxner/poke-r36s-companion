package com.baxner.r36scompanion.ui.texto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoteTest {
    @Test
    fun `el mote por defecto es el nombre de la especie, sin importar mayusculas`() {
        assertTrue(esMoteDeEspecie("KADABRA", "Kadabra"))
        assertTrue(esMoteDeEspecie("Kadabra", "Kadabra"))
        assertTrue(esMoteDeEspecie("kadabra", "Kadabra"))
    }

    @Test
    fun `un mote de verdad no es la especie`() {
        assertFalse(esMoteDeEspecie("Draco", "Charizard"))
    }

    @Test
    fun `sin mote o sin especie no hay nada que comparar`() {
        assertFalse(esMoteDeEspecie(null, "Kadabra"))
        assertFalse(esMoteDeEspecie("KADABRA", null))
    }
}
