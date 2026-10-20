package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextosDeJugarTest {
    private val ahora = 1_000_000_000L

    @Test
    fun sinGuardarTodavia() {
        assertEquals("Aún no guardas en el teléfono", textoDeUltimoGuardadoEnElTelefono(ahora, null))
    }

    @Test
    fun guardadoHaceUnMomento() {
        assertEquals("Guardado en el teléfono hace un momento", textoDeUltimoGuardadoEnElTelefono(ahora, ahora - 10_000))
    }

    @Test
    fun guardadoHaceMinutos() {
        assertEquals("Guardado en el teléfono hace 5 min", textoDeUltimoGuardadoEnElTelefono(ahora, ahora - 5 * 60_000))
    }

    @Test
    fun guardadoHaceHoras() {
        assertEquals("Guardado en el teléfono hace 2 h", textoDeUltimoGuardadoEnElTelefono(ahora, ahora - 2 * 60 * 60_000))
    }

    @Test
    fun guardadoHaceUnDia() {
        assertEquals("1 día", textoDeHaceTiempo(ahora, ahora - 25 * 60 * 60_000))
    }

    @Test
    fun guardadoHaceVariosDias() {
        assertEquals("3 días", textoDeHaceTiempo(ahora, ahora - 3L * 24 * 60 * 60_000))
    }

    @Test
    fun nuncaNegativo() {
        assertEquals("un momento", textoDeHaceTiempo(ahora, ahora + 60_000))
    }

    @Test
    fun pokedexNacionalFireRedConEsmeralda() {
        assertTrue(necesitaPokedexNacional("FireRed", "Emerald"))
        assertTrue(necesitaPokedexNacional("Ruby", "LeafGreen"))
    }

    @Test
    fun pokedexNacionalNoHaceFaltaEntreDosDeHoenn() {
        assertEquals(false, necesitaPokedexNacional("Ruby", "Sapphire"))
    }

    @Test
    fun pokedexNacionalNoHaceFaltaEntreDosDeKanto() {
        assertEquals(false, necesitaPokedexNacional("FireRed", "LeafGreen"))
    }

    @Test
    fun mensajesDeAvisos() {
        assertTrue(mensajeDeAvisoAntesDeJugar(AvisoAntesDeJugar.PartidaVacia).contains("vacía"))
        assertTrue(mensajeDeAvisoAntesDeJugar(AvisoAntesDeJugar.NoReconocida).contains("hack"))
        val avanceEnElEstado = AvisoAntesDeJugar.AvanceSoloEnElEstado(estadoModificado = ahora, partidaModificada = ahora - 1_000)
        assertTrue(mensajeDeAvisoAntesDeJugar(avanceEnElEstado).contains("estado automático"))
    }
}
