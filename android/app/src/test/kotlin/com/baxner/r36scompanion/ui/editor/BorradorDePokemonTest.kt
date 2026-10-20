package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.editor.CambioDeEstadisticas
import com.baxner.r36scompanion.data.editor.Estadisticas
import com.baxner.r36scompanion.data.editor.Movimiento
import com.baxner.r36scompanion.data.editor.PokemonEditable
import org.junit.Assert.assertEquals
import org.junit.Test

class BorradorDePokemonTest {
    private val estadisticas = Estadisticas(ps = 20, ataque = 15, defensa = 14, ataqueEspecial = 16, defensaEspecial = 14, velocidad = 18)

    private val detalle = PokemonEditable(
        especie = 64,
        naturaleza = 3,
        habilidad = 22,
        objeto = 200,
        pokeBall = 4,
        amistad = 70,
        idioma = 7,
        esShiny = false,
        esHuevo = false,
        mote = "KADABRA",
        nivel = 53,
        pid = 123L,
        idDeEntrenador = 12345,
        idSecreto = 1,
        entrenadorOriginal = "ROJO",
        versionDeOrigen = 4,
        lugarDeCaptura = 0,
        nivelDeCaptura = 10,
        ivs = estadisticas,
        evs = estadisticas,
        estadisticas = estadisticas,
        movimientos = listOf(Movimiento(93, 10, 10), Movimiento(50, 10, 10)),
        legalidad = null,
    )

    @Test
    fun `aBorrador rellena los movimientos a 4 con ceros`() {
        val borrador = detalle.aBorrador()
        assertEquals(listOf(93, 50, 0, 0), borrador.movimientos)
        assertEquals(64, borrador.especie)
        assertEquals("KADABRA", borrador.mote)
        assertEquals(53, borrador.nivel)
    }

    @Test
    fun `aBorrador no trunca cuando ya trae 4 o mas movimientos`() {
        val conCuatro = detalle.copy(movimientos = listOf(Movimiento(1, 1, 1), Movimiento(2, 1, 1), Movimiento(3, 1, 1), Movimiento(4, 1, 1)))
        assertEquals(listOf(1, 2, 3, 4), conCuatro.aBorrador().movimientos)
    }

    @Test
    fun `aCambio manda los seis campos de IVs y EVs`() {
        val borrador = detalle.aBorrador().copy(ivs = Estadisticas(31, 31, 31, 31, 31, 31))
        val cambio = borrador.aCambio()

        assertEquals(CambioDeEstadisticas(31, 31, 31, 31, 31, 31), cambio.ivs)
        assertEquals(64, cambio.especie)
        assertEquals(listOf(93, 50, 0, 0), cambio.movimientos)
    }
}
