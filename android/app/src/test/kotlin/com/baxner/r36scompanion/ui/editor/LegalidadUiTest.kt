package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalidadUiTest {
    private val casilla = Casilla(OrigenDeCasilla.EQUIPO, 0)

    @Test
    fun `sin mapa (no se pudo revisar) es null para cualquier casilla`() {
        assertNull(legalidadDe(null, casilla))
    }

    @Test
    fun `una casilla que no esta en el mapa de ilegales es legal`() {
        val legal = legalidadDe(emptyMap(), casilla)
        assertTrue(legal!!.valida)
    }

    @Test
    fun `una casilla que si esta en el mapa trae su legalidad`() {
        val ilegal = Legalidad(valida = false, mensajes = listOf("Level exceeds max."))
        assertEquals(ilegal, legalidadDe(mapOf(casilla to ilegal), casilla))
    }

    @Test
    fun `sin revisar cuando no hay legalidad`() {
        assertEquals("Sin revisar", resumenDeLegalidad(null))
    }

    @Test
    fun `legal cuando el motor dice que es valido`() {
        assertEquals("Legal", resumenDeLegalidad(Legalidad(valida = true, mensajes = emptyList())))
    }

    @Test
    fun `reconoce motivos comunes por palabra clave, en ingles`() {
        assertEquals(
            "el nivel no cuadra con su especie y origen",
            resumenDeLegalidad(Legalidad(valida = false, mensajes = listOf("Level exceeds max for Encounter Type."))),
        )
        assertEquals(
            "tiene un movimiento que no podría aprender",
            resumenDeLegalidad(Legalidad(valida = false, mensajes = listOf("Invalid Move at index 1."))),
        )
        assertEquals(
            "esa evolución necesita un intercambio",
            resumenDeLegalidad(Legalidad(valida = false, mensajes = listOf("Evolution requires a trade."))),
        )
    }

    @Test
    fun `reconoce motivos comunes aunque el mensaje ya venga en espanol`() {
        assertEquals(
            "el nivel no cuadra con su especie y origen",
            resumenDeLegalidad(Legalidad(valida = false, mensajes = listOf("Nivel fuera de rango para su especie y origen."))),
        )
    }

    @Test
    fun `un motivo que no reconoce da un resumen generico, nunca el ingles crudo`() {
        assertEquals(
            "no sería posible en el juego real",
            resumenDeLegalidad(Legalidad(valida = false, mensajes = listOf("PID mismatch with an unrelated internal flag."))),
        )
    }

    @Test
    fun `invalida sin mensajes tambien da el resumen generico`() {
        assertEquals("no sería posible en el juego real", resumenDeLegalidad(Legalidad(valida = false, mensajes = emptyList())))
    }
}
