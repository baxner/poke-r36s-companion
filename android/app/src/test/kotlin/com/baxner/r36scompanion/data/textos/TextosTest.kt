package com.baxner.r36scompanion.data.textos

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Contra las tablas reales de `assets/textos/es/` (están versionadas). */
class TextosTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    @Test
    fun especiesPorNumeroDeLaPokedexNacional() {
        assertEquals("Bulbasaur", textos.especie(1))
        assertEquals("Kadabra", textos.especie(64))
        assertEquals("Alakazam", textos.especie(65))
        assertEquals("Deoxys", textos.especie(386))
        assertNull("el 0 es el huevo, no una especie", textos.especie(0))
    }

    @Test
    fun objetosConLaNumeracionDeLasPartidasDeGba() {
        // Lo que el motor dio para el equipo real de Rojo Fuego: 200 y 182.
        assertEquals("Restos", textos.objetoGen3(200))
        assertEquals("Repartir Exp", textos.objetoGen3(182))
        assertEquals("Roca del Rey", textos.objetoGen3(187))
        assertEquals("Escamadragón", textos.objetoGen3(201))
        assertNull("sin objeto", textos.objetoGen3(0))
    }

    @Test
    fun juegosEnEspanol() {
        assertEquals("Rojo Fuego", textos.juego("FireRed"))
        assertEquals("Verde Hoja", textos.juego("LeafGreen"))
        assertEquals("Esmeralda", textos.juego("Emerald"))
        assertEquals("Rubí", textos.juego("Ruby"))
        assertEquals("Zafiro", textos.juego("Sapphire"))
        assertEquals("Un juego que no conoce se queda como venía", "Unbound", textos.juego("Unbound"))
    }

    @Test
    fun naturalezasYMovimientos() {
        assertEquals(25, (0..24).mapNotNull { textos.naturaleza(it) }.size)
        assertEquals("Placaje", textos.movimiento(33))
        assertEquals("Danza Espada", textos.movimiento(14))
    }
}
