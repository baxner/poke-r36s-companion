package com.baxner.r36scompanion.data.textos

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TextosDelMotorTest {
    @Test
    fun ningunCodigoDelMotorSaleEnIngles() {
        val rechazos = listOf("noRoute", "slotLocked", "eggAcrossGenerations", "languageMismatch", "speciesNotInGame", "lastPartyMember", "noRoom", "bagFull", "conversionFailed", "notInGame", "fused", "holdingItem", "codigoNuevo")
        rechazos.forEach { assertFalse("$it quedó en inglés", TextosDelMotor.rechazo(it).contains(it)) }
        assertEquals("Cambia de especie (evoluciona)", TextosDelMotor.campoQueCambia("species"))
        assertEquals("Se registra como atrapado en la Pokédex del juego que lo recibe", TextosDelMotor.cambioEnPartida("pokedexCaught"))
    }

    @Test
    fun formasDeEncuentro() {
        assertEquals("En la hierba", TextosDelMotor.formaDeEncuentro("Wild Encounter (FR) Grass", false))
        assertEquals("Pescando con la Supercaña", TextosDelMotor.formaDeEncuentro("Wild Encounter (FR) Super Rod", false))
        assertEquals("Encuentro fijo o regalo", TextosDelMotor.formaDeEncuentro("Static Encounter", false))
        assertNull(TextosDelMotor.formaDeEncuentro("Egg", true))
    }

    @Test
    fun lugaresDesdeSuNombreEnIngles() {
        val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }
        assertEquals("Ruta 1", textos.lugarGen3DesdeIngles("Route 1"))
        assertEquals("Pueblo Paleta", textos.lugarGen3DesdeIngles("Pallet Town"))
        assertEquals("Camino Candente", textos.lugarGen3DesdeIngles("Kindle Road"))
        assertNull(textos.lugarGen3DesdeIngles("Un lugar que no existe"))
    }
}
