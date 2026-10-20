package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.editor.Estadisticas
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitesDeEdicionTest {
    @Test
    fun `un IV siempre cabe en 0 a 31`() {
        assertEquals(0, limitarIv(-5))
        assertEquals(0, limitarIv(0))
        assertEquals(31, limitarIv(31))
        assertEquals(31, limitarIv(99))
    }

    @Test
    fun `un EV cabe en 0 a 255 cuando los demas estan en 0`() {
        val vacias = Estadisticas(0, 0, 0, 0, 0, 0)
        assertEquals(0, limitarEv(vacias, CampoDeEstadistica.PS, -10))
        assertEquals(255, limitarEv(vacias, CampoDeEstadistica.PS, 255))
        assertEquals(255, limitarEv(vacias, CampoDeEstadistica.PS, 999))
    }

    @Test
    fun `el total de los seis EVs no pasa de 510`() {
        // 252 + 252 + 6 = 510 ya es el máximo clásico; subir ataque más se recorta a lo que falte.
        val actuales = Estadisticas(ps = 252, ataque = 252, defensa = 6, ataqueEspecial = 0, defensaEspecial = 0, velocidad = 0)
        assertEquals("ya no cabe nada más: los otros cinco ya suman 510", 0, limitarEv(actuales, CampoDeEstadistica.ATAQUE_ESPECIAL, 100))

        val conEspacio = Estadisticas(ps = 100, ataque = 100, defensa = 0, ataqueEspecial = 0, defensaEspecial = 0, velocidad = 0)
        // Los otros cinco (sin velocidad) suman 200; a velocidad le caben 310, pero pidió 400: se recorta a 255 (el tope por campo).
        assertEquals(255, limitarEv(conEspacio, CampoDeEstadistica.VELOCIDAD, 400))
        // Pedir 100 sí cabe dentro de los 310 disponibles.
        assertEquals(100, limitarEv(conEspacio, CampoDeEstadistica.VELOCIDAD, 100))
    }

    @Test
    fun `limitarEv no cuenta el campo que se esta editando contra si mismo`() {
        // Si ps ya tenía 200 y se vuelve a poner 200, no debe restarse dos veces.
        val actuales = Estadisticas(ps = 200, ataque = 0, defensa = 0, ataqueEspecial = 0, defensaEspecial = 0, velocidad = 0)
        assertEquals(200, limitarEv(actuales, CampoDeEstadistica.PS, 200))
        assertEquals(255, limitarEv(actuales, CampoDeEstadistica.PS, 300))
    }

    @Test
    fun `el dinero cabe en 0 a 999999`() {
        assertEquals(0L, limitarDinero(-100))
        assertEquals(999_999L, limitarDinero(999_999L))
        assertEquals(999_999L, limitarDinero(5_000_000L))
    }

    @Test
    fun `el nombre del entrenador se trunca a su maximo de letras`() {
        assertEquals("ROJO", limitarNombre("ROJO", 7))
        assertEquals("ROJOROJ", limitarNombre("ROJOROJO", 7))
        assertEquals("sin limite conocido, se deja tal cual", "ROJOROJO", limitarNombre("ROJOROJO", 0))
    }
}
