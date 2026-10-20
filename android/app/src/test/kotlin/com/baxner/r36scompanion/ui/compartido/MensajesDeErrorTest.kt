package com.baxner.r36scompanion.ui.compartido

import com.baxner.r36scompanion.data.jugar.ErrorDeJuego
import com.baxner.r36scompanion.data.jugar.ErrorDeRom
import org.junit.Assert.assertTrue
import org.junit.Test

class MensajesDeErrorJugarTest {
    @Test
    fun errorDeRomFormatoNoSoportado() {
        val mensaje = mensajeDeErrorDeRom(ErrorDeRom.FormatoNoSoportado("/roms/gba/Rojo Fuego.7z", "7z"))
        assertTrue(mensaje.contains(".7z"))
        assertTrue(mensaje.contains(".gba") || mensaje.contains(".zip"))
    }

    @Test
    fun errorDeRomNoEncontrada() {
        val mensaje = mensajeDeErrorDeRom(ErrorDeRom.NoEncontrada("/roms/gba", "Pokemon Edicion Rojo Fuego"))
        assertTrue(mensaje.contains("Pokemon Edicion Rojo Fuego"))
    }

    @Test
    fun errorDeJuegoYaEnElTelefono() {
        val mensaje = mensajeDeErrorDeJuego(ErrorDeJuego.YaEstaEnElTelefono(1L))
        assertTrue(mensaje.contains("teléfono"))
    }

    @Test
    fun errorDeJuegoHayOtroIntercambio() {
        val mensaje = mensajeDeErrorDeJuego(ErrorDeJuego.HayOtroIntercambio(7L))
        assertTrue(mensaje.contains("intercambio"))
    }
}
