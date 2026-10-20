package com.baxner.r36scompanion.data.motor

import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.editor.PokemonPatchDto
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class PuenteTest {
    @Test
    fun laPrimeraCasillaDelEquipoSeMandaConSuNumero() {
        // El motor rechazaba {"source":"party"} (sin slot): kotlinx no manda los valores por defecto si no se le pide.
        val json = Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), PokemonHandleDto(source = "party", slot = 0))
        assertEquals("""{"source":"party","slot":0}""", json.toString())
    }

    @Test
    fun enUnParcheLosCamposNulosNoSeMandan() {
        val json = Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), PokemonPatchDto(level = 54))
        assertEquals("""{"level":54}""", json.toString())
    }
}
