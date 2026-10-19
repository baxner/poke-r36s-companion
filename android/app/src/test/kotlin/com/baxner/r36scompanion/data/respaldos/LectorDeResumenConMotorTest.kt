package com.baxner.r36scompanion.data.respaldos

import com.baxner.r36scompanion.data.motor.BadgeCountDto
import com.baxner.r36scompanion.data.motor.GameProgressDto
import com.baxner.r36scompanion.data.motor.PlayTimeDto
import com.baxner.r36scompanion.data.motor.PokedexCountDto
import com.baxner.r36scompanion.data.motor.PokemonSummaryDto
import com.baxner.r36scompanion.data.motor.ResumenDePartida
import com.baxner.r36scompanion.data.motor.SaveFormatDto
import com.baxner.r36scompanion.data.motor.SaveSummaryDto
import com.baxner.r36scompanion.data.motor.TrainerCardDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [traducirResumen] es la única parte de [LectorDeResumenConMotor] que no depende del motor de
 * verdad (WebView): lo demás lo cubre `MotorWebViewTest` en el emulador (ver AGENTS.md).
 */
class LectorDeResumenConMotorTest {

    @Test
    fun `traduce un resumen completo`() {
        val resumen = ResumenDePartida(
            partida = SaveSummaryDto(
                fileName = "Pokemon Edicion Rojo Fuego.srm",
                version = "FireRed",
                generation = 3,
                hasEvents = true,
                format = SaveFormatDto(id = "radicalred", name = "Radical Red", baseGame = "FireRed"),
            ),
            entrenador = TrainerCardDto(id = "12345/00001", name = "Rojo", money = 3000, rival = "Gary"),
            progreso = GameProgressDto(
                playTime = PlayTimeDto(hours = 34, minutes = 12, seconds = 5),
                badges = BadgeCountDto(earned = 5, total = 8),
                pokedex = PokedexCountDto(seen = 60, caught = 45),
            ),
            equipo = listOf(
                PokemonSummaryDto(speciesId = 65, species = "Alakazam", nickname = "Pensador", level = 40, isShiny = true),
                PokemonSummaryDto(speciesId = 7, species = "Squirtle", nickname = null, level = 5, isEgg = true),
            ),
        )

        val resultado = traducirResumen(resumen)

        assertEquals(
            ResumenGuardado(
                version = "FireRed",
                entrenador = "Rojo",
                idDeEntrenador = "12345/00001",
                dinero = 3000,
                horas = 34,
                minutos = 12,
                segundos = 5,
                medallas = 5,
                medallasTotales = 8,
                vistos = 60,
                atrapados = 45,
                equipo = listOf(
                    PokemonCorto(especie = "Alakazam", especieId = 65, nivel = 40, mote = "Pensador", shiny = true, huevo = false),
                    PokemonCorto(especie = "Squirtle", especieId = 7, nivel = 5, mote = null, shiny = false, huevo = true),
                ),
                formato = "radicalred",
            ),
            resultado,
        )
    }

    @Test
    fun `campos opcionales ausentes del motor quedan en null, no revientan`() {
        val resumen = ResumenDePartida(
            partida = SaveSummaryDto(version = "Emerald", generation = 3),
            entrenador = TrainerCardDto(id = "1/0", name = "Rojo"),
            progreso = GameProgressDto(playTime = null, badges = null, pokedex = null),
            equipo = emptyList(),
        )

        val resultado = traducirResumen(resumen)

        assertEquals("Emerald", resultado.version)
        assertNull(resultado.dinero)
        assertNull(resultado.horas)
        assertNull(resultado.medallas)
        assertNull(resultado.vistos)
        assertNull(resultado.formato)
        assertEquals(emptyList<PokemonCorto>(), resultado.equipo)
    }
}
