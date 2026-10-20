package com.baxner.r36scompanion.ui.ajustes

import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NombreDeRomTest {
    private fun rom(rutaEnConsola: String) = RomEnElTelefono(
        juegoId = 3,
        archivo = File("/datos/roms/3.gba"),
        rutaEnConsola = rutaEnConsola,
        tamanoEnConsola = 16L shl 20,
        modificadoEnConsola = 0,
        codigoDeJuego = "BPRS",
        tituloInterno = "POKEMON FIRE",
        tamano = 16L shl 20,
    )

    @Test
    fun saleDelNombreDeLaConsolaNoDelArchivoDelTelefono() {
        assertEquals("Rojo Fuego", nombreDeRom(rom("/roms/gba/Pokemon Edicion Rojo Fuego.gba")))
    }

    @Test
    fun unJuegoQueNoEsPokemonQuedaConSuNombre() {
        assertEquals("Advance Wars (USA)", nombreDeRom(rom("/roms/gba/Advance Wars (USA).zip")))
    }
}
