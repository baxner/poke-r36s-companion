package com.baxner.r36scompanion.ui.banco

import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeEscritura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextosDeBancoTest {
    @Test
    fun `confirmar un deposito queda EN_BANCO - se confirmo`() {
        assertEquals("El depósito se confirmó: ya quedó en el banco.", explicarConciliacion(EstadoEnBanco.PENDIENTE_DE_DEPOSITO, siguePresente = true))
    }

    @Test
    fun `un deposito que desaparece del todo - no llego a pasar`() {
        assertEquals(
            "El depósito no llegó a pasar: sigue en tu partida, no en el banco.",
            explicarConciliacion(EstadoEnBanco.PENDIENTE_DE_DEPOSITO, siguePresente = false),
        )
    }

    @Test
    fun `un retiro que desaparece del banco - se confirmo`() {
        assertEquals(
            "El retiro se confirmó: ya quedó en tu partida, no en el banco.",
            explicarConciliacion(EstadoEnBanco.PENDIENTE_DE_RETIRO, siguePresente = false),
        )
    }

    @Test
    fun `un retiro que sigue EN_BANCO - no llego a pasar`() {
        assertEquals("El retiro no llegó a pasar: sigue en el banco.", explicarConciliacion(EstadoEnBanco.PENDIENTE_DE_RETIRO, siguePresente = true))
    }

    @Test
    fun `los textos de confirmacion explican que el Pokemon cambia de lugar y que hay que cerrar el juego`() {
        assertTrue(TextoConfirmarDeposito.contains("banco"))
        assertTrue(TextoConfirmarDeposito.contains("Cierra el juego"))
        assertTrue(TextoConfirmarRetiro.contains("consola"))
        assertTrue(TextoConfirmarRetiro.contains("Cierra el juego"))
    }

    @Test
    fun `mensajeDeErrorDeEscritura explica el juego abierto en palabras simples`() {
        val mensaje = mensajeDeErrorDeEscritura(ErrorDeEscritura.JuegoAbierto("Pokemon Edicion Rojo Fuego.srm"))

        assertTrue(mensaje.contains("Pokemon Edicion Rojo Fuego.srm"))
        assertTrue(mensaje.contains("Cierra"))
    }

    @Test
    fun `mensajeDeErrorDeEscritura explica el conflicto por partida cambiada`() {
        val mensaje = mensajeDeErrorDeEscritura(ErrorDeEscritura.Conflicto(esperado = "a", encontrado = "b"))

        assertTrue(mensaje.contains("cambió"))
    }
}
