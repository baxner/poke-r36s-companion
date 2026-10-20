package com.baxner.r36scompanion.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MedallasGen3Test {
    @Test
    fun `medallas de Kanto, Rojo Fuego y Verde Hoja`() {
        assertEquals(
            listOf("Roca", "Cascada", "Trueno", "Arcoíris", "Alma", "Pantano", "Volcán", "Tierra"),
            listOf("Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth").map(::nombreDeMedalla),
        )
    }

    @Test
    fun `medallas de Hoenn, Rubi Zafiro y Esmeralda`() {
        assertEquals(
            listOf("Piedra", "Puño", "Dinamo", "Calor", "Equilibrio", "Pluma", "Mente", "Lluvia"),
            listOf("Stone", "Knuckle", "Dynamo", "Heat", "Balance", "Feather", "Mind", "Rain").map(::nombreDeMedalla),
        )
    }

    @Test
    fun `una medalla desconocida se muestra tal cual`() {
        assertEquals("Mystery", nombreDeMedalla("Mystery"))
    }
}
