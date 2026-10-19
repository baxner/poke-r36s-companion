package com.baxner.r36scompanion.data.respaldos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NombresDeArchivoTest {

    @Test
    fun `reconoce una partida srm`() {
        assertEquals(
            TipoDeArchivo.PARTIDA to "Pokemon Edicion Rojo Fuego",
            tipoYNombreDeArchivo("Pokemon Edicion Rojo Fuego.srm"),
        )
    }

    @Test
    fun `reconoce un estado manual, con y sin numero`() {
        assertEquals(TipoDeArchivo.ESTADO to "Pokemon Edicion Rojo Fuego", tipoYNombreDeArchivo("Pokemon Edicion Rojo Fuego.state"))
        assertEquals(TipoDeArchivo.ESTADO to "Pokemon Edicion Rojo Fuego", tipoYNombreDeArchivo("Pokemon Edicion Rojo Fuego.state1"))
        assertEquals(TipoDeArchivo.ESTADO to "Pokemon Edicion Rojo Fuego", tipoYNombreDeArchivo("Pokemon Edicion Rojo Fuego.state12"))
    }

    @Test
    fun `reconoce el estado automatico y no lo confunde con un estado manual`() {
        assertEquals(
            TipoDeArchivo.ESTADO_AUTOMATICO to "Yu-Gi-Oh! GX - Duel Academy (USA)",
            tipoYNombreDeArchivo("Yu-Gi-Oh! GX - Duel Academy (USA).state.auto"),
        )
    }

    @Test
    fun `nombres reales con parentesis comas y acentos`() {
        assertEquals(
            TipoDeArchivo.PARTIDA to "Pokemon - Version Rouge Feu (F)",
            tipoYNombreDeArchivo("Pokemon - Version Rouge Feu (F).srm"),
        )
        assertEquals(
            TipoDeArchivo.PARTIDA to "Sound of Thunder, A (USA) (En,Fr,De,Es,It)",
            tipoYNombreDeArchivo("Sound of Thunder, A (USA) (En,Fr,De,Es,It).srm"),
        )
    }

    @Test
    fun `ignora ROMs y archivos sueltos`() {
        assertNull(tipoYNombreDeArchivo("Pokemon Edicion Rojo Fuego.gba"))
        assertNull(tipoYNombreDeArchivo("Pokemon Edicion Rojo Fuego.gba.zip"))
        assertNull(tipoYNombreDeArchivo("gamelist.xml"))
        assertNull(tipoYNombreDeArchivo(".pr36c"))
        assertNull(tipoYNombreDeArchivo("SHA256SUMS"))
    }

    @Test
    fun `pareceDePokemon ignora mayusculas y acentos`() {
        assertTrue(pareceDePokemon("Pokemon Edicion Rojo Fuego"))
        assertTrue(pareceDePokemon("POKEMON LETS GO EEVEE"))
        assertTrue(pareceDePokemon("Pokémon Esmeralda"))
        assertFalse(pareceDePokemon("Yu-Gi-Oh! GX - Duel Academy (USA)"))
        assertFalse(pareceDePokemon("Avatar - The Legend of Aang"))
    }

    @Test
    fun `nombreBaseDeRom quita carpeta y solo la extension`() {
        assertEquals("Pokemon Edicion Rojo Fuego", nombreBaseDeRom("/roms/gba/Pokemon Edicion Rojo Fuego.gba"))
        assertEquals("Yu-Gi-Oh! GX - Duel Academy (USA)", nombreBaseDeRom("Yu-Gi-Oh! GX - Duel Academy (USA).gba"))
        assertEquals("sin extension", nombreBaseDeRom("sin extension"))
    }

    @Test
    fun `formatoDeFechaParaCarpeta es estable, en UTC y sin caracteres prohibidos en nombres de archivo`() {
        // 2026-10-19T14:30:05Z
        val ms = 1792420205_000L
        val formato = formatoDeFechaParaCarpeta(ms)

        assertEquals("2026-10-19_14-30-05", formato)
        assertFalse(formato.contains(':'))
        assertFalse(formato.contains('/'))
    }

    @Test
    fun `esPartidaVacia detecta todo 0xFF y no un archivo vacio`() {
        assertTrue(esPartidaVacia(ByteArray(131072) { 0xFF.toByte() }))
        assertFalse(esPartidaVacia(ByteArray(131072)))
        assertFalse(esPartidaVacia(ByteArray(0)))
    }
}
