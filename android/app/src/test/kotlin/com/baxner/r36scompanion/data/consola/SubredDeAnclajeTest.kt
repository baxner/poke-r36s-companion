package com.baxner.r36scompanion.data.consola

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubredDeAnclajeTest {

    @Test
    fun `prefijo 24 da los 253 hosts de la subred sin red, broadcast ni el propio telefono`() {
        val direcciones = direccionesParaBuscar("10.101.33.64", 24)

        assertEquals(253, direcciones.size)
        assertTrue("10.101.33.1" in direcciones)
        assertTrue("10.101.33.254" in direcciones)
        assertFalse("10.101.33.0" in direcciones)
        assertFalse("10.101.33.255" in direcciones)
        assertFalse("10.101.33.64" in direcciones)
    }

    @Test
    fun `prefijo 16 se acota a la 24 que contiene la IP del telefono, no a toda la 16`() {
        val direccionesDesde16 = direccionesParaBuscar("10.101.33.64", 16)
        val direccionesDesde24 = direccionesParaBuscar("10.101.33.64", 24)

        assertEquals(direccionesDesde24.toSet(), direccionesDesde16.toSet())
        assertFalse("10.101.1.5" in direccionesDesde16) // de la /16 real, pero fuera de la /24 del teléfono
    }

    @Test
    fun `prefijo 25 respeta la subred mas chica, no la agranda a 24`() {
        // 130 = 1000_0010: con /25 la red es .128, broadcast .255.
        val direcciones = direccionesParaBuscar("10.0.0.130", 25)

        // 126 hosts posibles en la /25 (.129 a .254), menos el propio .130: 125.
        assertEquals(125, direcciones.size)
        assertTrue("10.0.0.129" in direcciones)
        assertTrue("10.0.0.254" in direcciones)
        assertFalse("10.0.0.128" in direcciones)
        assertFalse("10.0.0.255" in direcciones)
        assertFalse("10.0.0.130" in direcciones)
        assertFalse("10.0.0.1" in direcciones) // ni siquiera está en esta /25
    }

    @Test
    fun `prefijo 30, el borde de una subred minuscula, deja solo al vecino`() {
        val direcciones = direccionesParaBuscar("10.0.0.1", 30)

        assertEquals(listOf("10.0.0.2"), direcciones)
    }

    @Test
    fun `prefijo 30 con el telefono en el otro extremo deja al otro vecino`() {
        val direcciones = direccionesParaBuscar("10.0.0.2", 30)

        assertEquals(listOf("10.0.0.1"), direcciones)
    }

    @Test
    fun `prefijo 31 no deja ningun host (sin hueco para red y broadcast)`() {
        assertTrue(direccionesParaBuscar("10.0.0.1", 31).isEmpty())
    }

    @Test
    fun `prefijo 32 no deja ningun host`() {
        assertTrue(direccionesParaBuscar("10.0.0.1", 32).isEmpty())
    }

    @Test
    fun `prefijo invalido truena claro`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { direccionesParaBuscar("10.0.0.1", 33) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { direccionesParaBuscar("10.0.0.1", -1) }
    }

    @Test
    fun `sobrecarga con InterfazDeAnclaje delega en la de string mas prefijo`() {
        val interfaz = InterfazDeAnclaje("rndis0", "10.0.0.1", 30)

        assertEquals(direccionesParaBuscar("10.0.0.1", 30), direccionesParaBuscar(interfaz))
    }

    @Test
    fun `ipv4 a entero y de vuelta es la identidad`() {
        val ips = listOf("0.0.0.0", "10.101.33.64", "192.168.1.1", "255.255.255.255")

        for (ip in ips) assertEquals(ip, enteroAIpv4(ipv4AEntero(ip)))
    }

    @Test
    fun `ip con octeto fuera de rango truena claro`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ipv4AEntero("10.0.0.256") }
    }

    @Test
    fun `ip con menos de 4 octetos truena claro`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ipv4AEntero("10.0.0") }
    }
}
