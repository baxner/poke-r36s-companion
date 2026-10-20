package com.baxner.r36scompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContadorDeVisibilidadTest {
    @Test
    fun laPrimeraActividadQuedaVisible() {
        val contador = ContadorDeVisibilidad()
        assertTrue(contador.alIniciarUnaActividad())
    }

    @Test
    fun unaSegundaActividadNoVuelveAAvisar() {
        val contador = ContadorDeVisibilidad()
        assertTrue(contador.alIniciarUnaActividad())
        assertFalse("ya estaba visible: una segunda pantalla no es una nueva aparición", contador.alIniciarUnaActividad())
    }

    @Test
    fun soloQuedaInvisibleCuandoSeDetieneLaUltima() {
        val contador = ContadorDeVisibilidad()
        contador.alIniciarUnaActividad()
        contador.alIniciarUnaActividad() // p. ej. una rotación: la nueva actividad arranca antes de que acabe de irse del todo
        assertFalse("todavía queda una", contador.alDetenerUnaActividad())
        assertTrue("ya no queda ninguna", contador.alDetenerUnaActividad())
    }

    @Test
    fun noBajaDeCero() {
        val contador = ContadorDeVisibilidad()
        assertTrue(contador.alDetenerUnaActividad())
        assertTrue(contador.alDetenerUnaActividad())
        assertTrue(contador.alIniciarUnaActividad())
    }

    @Test
    fun ciclosRepetidosQuedanConsistentes() {
        val contador = ContadorDeVisibilidad()
        repeat(3) {
            assertTrue(contador.alIniciarUnaActividad())
            assertTrue(contador.alDetenerUnaActividad())
        }
    }
}
