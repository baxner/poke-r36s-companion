package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NombresDeObjetosTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    @Test
    fun `objeto equipado 0 es Ninguno, no un hueco en blanco`() {
        assertEquals("Ninguno", nombreDeObjetoEquipable(0, textos))
        assertEquals("Restos", nombreDeObjetoEquipable(200, textos))
    }

    @Test
    fun `movimiento 0 es una casilla vacia`() {
        assertEquals("(Ninguno)", nombreDeMovimientoEditable(0, textos))
        assertEquals("Placaje", nombreDeMovimientoEditable(33, textos))
    }

    @Test
    fun `especie sin id es un signo de interrogacion`() {
        assertEquals("?", nombreDeEspecieEditable(null, textos))
        assertEquals("Kadabra", nombreDeEspecieEditable(64, textos))
    }

    @Test
    fun `poke ball usa la tabla de objetos gen3`() {
        assertEquals("Poké Ball", nombreDePokeBall(4, textos))
        assertEquals("Master Ball", nombreDePokeBall(1, textos))
    }

    @Test
    fun `naturaleza conocida`() {
        assertEquals(25, (0..24).count { nombreDeNaturalezaEditable(it, textos) != "Naturaleza #$it" })
    }
}
