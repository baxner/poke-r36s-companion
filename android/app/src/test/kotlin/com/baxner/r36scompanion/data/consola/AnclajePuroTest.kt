package com.baxner.r36scompanion.data.consola

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnclajePuroTest {

    @Test
    fun `rndis ncm y usb cuentan como anclaje`() {
        assertTrue(esNombreDeInterfazDeAnclaje("rndis0"))
        assertTrue(esNombreDeInterfazDeAnclaje("ncm0"))
        assertTrue(esNombreDeInterfazDeAnclaje("usb0"))
    }

    @Test
    fun `rmnet_usb0 no cuenta, aunque traiga usb en el nombre`() {
        assertFalse(esNombreDeInterfazDeAnclaje("rmnet_usb0"))
    }

    @Test
    fun `wlan0 y lo0 tampoco cuentan`() {
        assertFalse(esNombreDeInterfazDeAnclaje("wlan0"))
        assertFalse(esNombreDeInterfazDeAnclaje("lo"))
    }

    @Test
    fun `interfacesDeAnclajeDesde descarta las que estan abajo`() {
        val interfaces = listOf(
            InfoDeInterfazDeRed("rndis0", arriba = false, direccionesIpv4 = listOf(InfoDeInterfazDeRed.DireccionIpv4("10.0.0.1", 24))),
        )

        assertTrue(interfacesDeAnclajeDesde(interfaces).isEmpty())
    }

    @Test
    fun `interfacesDeAnclajeDesde descarta las que no son de anclaje aunque esten arriba`() {
        val interfaces = listOf(
            InfoDeInterfazDeRed("wlan0", arriba = true, direccionesIpv4 = listOf(InfoDeInterfazDeRed.DireccionIpv4("192.168.1.5", 24))),
        )

        assertTrue(interfacesDeAnclajeDesde(interfaces).isEmpty())
    }

    @Test
    fun `interfacesDeAnclajeDesde da una InterfazDeAnclaje por cada IPv4 de una interfaz de anclaje arriba`() {
        val interfaces = listOf(
            InfoDeInterfazDeRed(
                "rndis0",
                arriba = true,
                direccionesIpv4 = listOf(
                    InfoDeInterfazDeRed.DireccionIpv4("10.101.33.64", 24),
                    InfoDeInterfazDeRed.DireccionIpv4("169.254.1.2", 16),
                ),
            ),
            InfoDeInterfazDeRed("wlan0", arriba = true, direccionesIpv4 = listOf(InfoDeInterfazDeRed.DireccionIpv4("192.168.1.5", 24))),
        )

        val resultado = interfacesDeAnclajeDesde(interfaces)

        assertEquals(
            listOf(InterfazDeAnclaje("rndis0", "10.101.33.64", 24), InterfazDeAnclaje("rndis0", "169.254.1.2", 16)),
            resultado,
        )
    }

    @Test
    fun `estadoDelAnclaje esta activo solo si hay al menos una interfaz`() {
        val sinInterfaces = estadoDelAnclaje(cableConectado = true, interfaces = emptyList())
        val conInterfaz = estadoDelAnclaje(cableConectado = true, interfaces = listOf(InterfazDeAnclaje("rndis0", "10.0.0.1", 24)))

        assertFalse(sinInterfaces.anclajeActivo)
        assertTrue(conInterfaz.anclajeActivo)
        assertTrue(sinInterfaces.cableConectado)
    }

    @Test
    fun `cableConectadoDesde manda el USB_STATE cuando lo hay, aunque diga que no`() {
        assertFalse(cableConectadoDesde(usbStateConectado = false, bateriaEnchufeUsb = true))
        assertTrue(cableConectadoDesde(usbStateConectado = true, bateriaEnchufeUsb = false))
    }

    @Test
    fun `cableConectadoDesde cae al respaldo de BatteryManager si USB_STATE nunca se disparo`() {
        assertTrue(cableConectadoDesde(usbStateConectado = null, bateriaEnchufeUsb = true))
        assertFalse(cableConectadoDesde(usbStateConectado = null, bateriaEnchufeUsb = false))
    }

    @Test
    fun `cableConectadoDesde sin ninguna señal es sin cable`() {
        assertFalse(cableConectadoDesde(usbStateConectado = null, bateriaEnchufeUsb = null))
    }
}
