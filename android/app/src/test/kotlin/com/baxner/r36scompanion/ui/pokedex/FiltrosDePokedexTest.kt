package com.baxner.r36scompanion.ui.pokedex

import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.pokedex.EntradaDePokedex
import com.baxner.r36scompanion.data.pokedex.Ubicacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltrosDePokedexTest {
    // Bulbasaur: registrada pero ya no la tiene. Pikachu: registrada y la tiene. Squirtle: la
    // tiene (banco) pero nunca se registró. Charmander: ni registrada ni la tiene.
    private val bulbasaur = EntradaDePokedex(especie = 1, vistaEn = setOf(1L), atrapadaEn = setOf(1L), ubicaciones = emptyList())
    private val pikachu = EntradaDePokedex(
        especie = 25,
        vistaEn = setOf(1L),
        atrapadaEn = setOf(1L),
        ubicaciones = listOf(Ubicacion.EnPartida(1L, Casilla(OrigenDeCasilla.CAJA, 0, 0), nivel = 12, esShiny = false)),
    )
    private val squirtle = EntradaDePokedex(especie = 7, vistaEn = emptySet(), atrapadaEn = emptySet(), ubicaciones = listOf(Ubicacion.EnBanco(1L, 10, false)))
    private val charmander = EntradaDePokedex(especie = 4, vistaEn = emptySet(), atrapadaEn = emptySet(), ubicaciones = emptyList())
    private val todas = listOf(bulbasaur, pikachu, squirtle, charmander)

    @Test
    fun `TODAS no filtra nada`() {
        assertEquals(todas, FiltroDePokedex.TODAS.aplicar(todas))
    }

    @Test
    fun `ME_FALTAN son las que no tiene ahora, registradas o no`() {
        val resultado = FiltroDePokedex.ME_FALTAN.aplicar(todas)
        assertEquals(listOf(bulbasaur, charmander), resultado)
    }

    @Test
    fun `LAS_TENGO son las que sí tiene ahora, registradas o no`() {
        val resultado = FiltroDePokedex.LAS_TENGO.aplicar(todas)
        assertEquals(listOf(pikachu, squirtle), resultado)
    }

    @Test
    fun `REGISTRADAS_PERO_YA_NO es justo Bulbasaur`() {
        val resultado = FiltroDePokedex.REGISTRADAS_PERO_YA_NO.aplicar(todas)
        assertEquals(listOf(bulbasaur), resultado)
    }

    @Test
    fun `resumenDe cuenta registradas, tenidas y vistas`() {
        val resumen = resumenDe(todas)

        assertEquals(4, resumen.total)
        assertEquals(2, resumen.registradas) // bulbasaur y pikachu
        assertEquals(2, resumen.tenidas) // pikachu y squirtle
        assertEquals(2, resumen.vistas) // bulbasaur y pikachu
    }

    @Test
    fun `un pendiente del banco no cuenta como la tiene (EntradaDePokedex ya lo filtra)`() {
        // Esto lo garantiza PokedexGlobalConRespaldos (no arma Ubicacion.EnBanco para pendientes);
        // aquí solo se confirma que, si no hay ubicaciones, laTiene es false y el filtro responde.
        val sinUbicaciones = EntradaDePokedex(especie = 50, vistaEn = emptySet(), atrapadaEn = emptySet(), ubicaciones = emptyList())
        assertFalse(sinUbicaciones.laTiene)
        assertTrue(FiltroDePokedex.ME_FALTAN.aplicar(listOf(sinUbicaciones)).contains(sinUbicaciones))
    }
}
