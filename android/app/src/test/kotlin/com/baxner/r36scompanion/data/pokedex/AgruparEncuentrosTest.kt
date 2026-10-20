package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Con filas como las que dio `encounters.search` del motor real para el Rojo Fuego del usuario. */
class AgruparEncuentrosTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    @Test
    fun juntaNivelesPorLugarYFormaYSeparaLaCrianza() {
        val pidgey = listOf(
            EncounterRowDto(16, "Egg", "Littleroot Town", "5", isEgg = true),
            EncounterRowDto(16, "Wild Encounter (FR) Grass", "Route 1", "3"),
            EncounterRowDto(16, "Wild Encounter (FR) Grass", "Route 1", "2"),
            EncounterRowDto(16, "Wild Encounter (FR) Grass", "Route 1", "5"),
            EncounterRowDto(16, "Wild Encounter (FR) Grass", "Route 2", "3-4"),
        )
        val renglones = agruparEncuentros(pidgey, juegoId = 1, versionId = 4, traducirLugar = textos::lugarGen3DesdeIngles)
        assertEquals(3, renglones.size)
        val ruta1 = renglones.single { it.lugar == "Ruta 1" }
        assertEquals("2-5", ruta1.niveles)
        assertEquals("En la hierba", ruta1.forma)
        assertEquals("3-4", renglones.single { it.lugar == "Ruta 2" }.niveles)
        assertTrue("la crianza va en su propio renglón, sin el lugar raro del motor", renglones.single { it.esHuevo }.lugar.isEmpty())
    }

    @Test
    fun dratiniDeLaZonaSafariYElRegaloDeCiudadAzulona() {
        val dratini = listOf(
            EncounterRowDto(147, "Static Encounter", "Celadon City", "18"),
            EncounterRowDto(147, "Wild Encounter (FR) Super Rod", "Safari Zone (Kanto)", "15-25"),
        )
        val renglones = agruparEncuentros(dratini, juegoId = 1, versionId = 4, traducirLugar = textos::lugarGen3DesdeIngles)
        assertEquals("Encuentro fijo o regalo", renglones.single { it.niveles == "18" }.forma)
        val safari = renglones.single { it.forma == "Pescando con la Supercaña" }
        assertEquals("15-25", safari.niveles)
        assertTrue("el lugar quedó en español: ${safari.lugar}", safari.lugar != "Safari Zone (Kanto)")
        assertTrue(renglones.none { it.esHuevo })
    }

    @Test
    fun unLugarQueNoEstaEnLaTablaSeQuedaComoVenia() {
        val renglones = agruparEncuentros(listOf(EncounterRowDto(1, "Wild Encounter (FR) Grass", "Lugar Inventado", "4")), 1, 4) { null }
        assertEquals("Lugar Inventado", renglones.single().lugar)
    }
}
