package com.baxner.r36scompanion.data.editor

import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las funciones puras de `TraduccionesDelMotor.kt`, con JSON de verdad (las formas comprobadas
 * contra el motor real, ver el KDoc de `Editor.kt`), no con los DTO construidos a mano: así
 * también queda cubierto que los nombres de campo de los DTO coinciden con los del motor.
 */
class TraduccionesDelMotorTest {

    // --- Casilla / PokemonHandle ---

    @Test
    fun `una casilla del equipo`() {
        val dto = Puente.json.decodeFromString<PokemonHandleDto>("""{"source":"party","slot":0,"box":null,"team":null}""")
        assertEquals(Casilla(OrigenDeCasilla.EQUIPO, 0, null), dto.aCasilla())
    }

    @Test
    fun `una casilla de caja`() {
        val dto = Puente.json.decodeFromString<PokemonHandleDto>("""{"source":"box","slot":5,"box":2,"team":null}""")
        assertEquals(Casilla(OrigenDeCasilla.CAJA, 5, 2), dto.aCasilla())
    }

    @Test
    fun `la casilla se manda de vuelta al motor igual`() {
        assertEquals(PokemonHandleDto("party", 0, null, null), Casilla(OrigenDeCasilla.EQUIPO, 0, null).aHandleDto())
        assertEquals(PokemonHandleDto("box", 5, 2, null), Casilla(OrigenDeCasilla.CAJA, 5, 2).aHandleDto())
    }

    // --- EditablePokemon ---

    @Test
    fun `pokemon_details completo, como el Kadabra probado con el motor real`() {
        val json = """
            {
              "species": 64, "form": 0, "gender": "male", "nature": 3, "ability": 22,
              "heldItem": 0, "ball": 4, "friendship": 70, "language": 7,
              "isShiny": false, "isEgg": false, "nickname": "Kadabra", "level": 28,
              "pid": 3200063177, "trainerId": 12345, "secretId": 1,
              "originalTrainerName": "ROJO", "version": 10,
              "metLocation": 25, "metLevel": 14,
              "ivs": {"health": 31, "attack": 12, "defense": 20, "specialAttack": 31, "specialDefense": 18, "speed": 25},
              "evs": {"health": 0, "attack": 4, "defense": 0, "specialAttack": 252, "specialDefense": 0, "speed": 252},
              "stats": {"health": 78, "attack": 45, "defense": 55, "specialAttack": 92, "specialDefense": 60, "speed": 85},
              "moves": [
                {"id": 93, "name": "Psychic", "pp": 10, "maxPp": 10, "isUnknown": false},
                {"id": 50, "name": "Disable", "pp": 15, "maxPp": 15, "isUnknown": false},
                {"id": 0, "name": "", "pp": 0, "maxPp": 0, "isUnknown": false},
                {"id": 0, "name": "", "pp": 0, "maxPp": 0, "isUnknown": false}
              ],
              "legality": {"valid": true, "messages": []}
            }
        """.trimIndent()

        val resultado = Puente.json.decodeFromString<EditablePokemonDto>(json).aPokemonEditable()

        assertEquals(
            PokemonEditable(
                especie = 64,
                forma = 0,
                genero = "male",
                naturaleza = 3,
                habilidad = 22,
                objeto = 0,
                pokeBall = 4,
                amistad = 70,
                idioma = 7,
                esShiny = false,
                esHuevo = false,
                mote = "Kadabra",
                nivel = 28,
                pid = 3200063177L,
                idDeEntrenador = 12345,
                idSecreto = 1,
                entrenadorOriginal = "ROJO",
                versionDeOrigen = 10,
                lugarDeCaptura = 25,
                nivelDeCaptura = 14,
                ivs = Estadisticas(31, 12, 20, 31, 18, 25),
                evs = Estadisticas(0, 4, 0, 252, 0, 252),
                estadisticas = Estadisticas(78, 45, 55, 92, 60, 85),
                movimientos = listOf(Movimiento(93, 10, 10), Movimiento(50, 15, 15), Movimiento(0, 0, 0), Movimiento(0, 0, 0)),
                legalidad = Legalidad(valida = true, mensajes = emptyList()),
            ),
            resultado,
        )
    }

    @Test
    fun `pokemon details con campos ausentes no revienta, queda en los valores por defecto`() {
        val resultado = Puente.json.decodeFromString<EditablePokemonDto>("{}").aPokemonEditable()

        assertNull(resultado.especie)
        assertNull(resultado.genero)
        assertNull(resultado.legalidad)
        assertEquals("", resultado.mote)
        assertEquals(0, resultado.nivel)
        assertEquals(emptyList<Movimiento>(), resultado.movimientos)
        assertEquals(Estadisticas(0, 0, 0, 0, 0, 0), resultado.ivs)
    }

    // --- PokemonOptions ---

    @Test
    fun `opciones de un pokemon, solo con ids (los nombres en ingles se ignoran)`() {
        val json = """
            {
              "species": [{"id": 63, "name": "Abra"}, {"id": 64, "name": "Kadabra"}, {"id": 65, "name": "Alakazam"}],
              "abilities": [{"id": 22, "name": "Synchronize"}, {"id": 32, "name": "Inner Focus"}],
              "forms": [],
              "metLocations": [{"id": 25, "name": "Route 5"}],
              "moves": [{"id": 93, "name": "Psychic"}, {"id": 50, "name": "Disable"}],
              "heldItems": [{"id": 0, "name": "(none)"}, {"id": 19, "name": "Antidote"}],
              "locked": ["nature", "ability"]
            }
        """.trimIndent()

        val resultado = Puente.json.decodeFromString<PokemonOptionsDto>(json).aOpcionesDePokemon()

        assertEquals(
            OpcionesDePokemon(
                especies = listOf(63, 64, 65),
                habilidades = listOf(22, 32),
                movimientos = listOf(93, 50),
                objetos = listOf(0, 19),
                lugares = listOf(25),
                bloqueados = setOf("nature", "ability"),
            ),
            resultado,
        )
    }

    // --- Mochila ---

    @Test
    fun `un bolsillo de la mochila, Pocion con el id de la 3a generacion`() {
        val json = """
            {
              "name": "Items",
              "items": [
                {"id": 13, "index": 13, "name": "Potion", "count": 3, "maxCount": 99},
                {"id": 19, "index": 19, "name": "Antidote", "count": 1, "maxCount": 99}
              ],
              "addable": [{"id": 20, "index": 20, "name": "Burn Heal", "maxCount": 99}]
            }
        """.trimIndent()

        val resultado = Puente.json.decodeFromString<PouchDto>(json).aBolsillo()

        assertEquals(
            Bolsillo(
                nombre = "Items",
                objetos = listOf(ObjetoEnMochila(13, 3, 99), ObjetoEnMochila(19, 1, 99)),
                agregables = listOf(20),
            ),
            resultado,
        )
    }

    // --- Medallas ---

    @Test
    fun `las medallas en orden del juego`() {
        val json = """[{"name":"Boulder","earned":true},{"name":"Cascade","earned":false}]"""

        val resultado = Puente.json.decodeFromString<List<BadgeDto>>(json).map { it.aMedalla() }

        assertEquals(listOf(Medalla("Boulder", true), Medalla("Cascade", false)), resultado)
    }

    // --- Entrenador ---

    @Test
    fun `tarjeta de entrenador con dinero`() {
        val json = """{"id":"12345/00001","name":"Rojo","maxNameLength":7,"money":3000}"""

        val resultado = Puente.json.decodeFromString<TrainerCardDto>(json).aEntrenador()

        assertEquals(Entrenador(nombre = "Rojo", maximoDeLetras = 7, idDeEntrenador = "12345/00001", dinero = 3000L), resultado)
    }

    @Test
    fun `tarjeta de entrenador sin dinero (el juego no lo tiene)`() {
        val json = """{"id":"1/0","name":"Red","maxNameLength":7,"money":null}"""

        val resultado = Puente.json.decodeFromString<TrainerCardDto>(json).aEntrenador()

        assertNull(resultado.dinero)
    }

    // --- Resumen de Pokémon (equipo) ---

    @Test
    fun `un pokemon del equipo`() {
        val json = """{"at":{"source":"party","slot":0,"box":null,"team":null},"speciesId":65,"editable":true,"nickname":"Pensador","level":40,"isShiny":true,"isEgg":false}"""

        val resultado = Puente.json.decodeFromString<PokemonSummaryDto>(json).aResumenDePokemon()

        assertEquals(
            ResumenDePokemon(Casilla(OrigenDeCasilla.EQUIPO, 0, null), especie = 65, nivel = 40, mote = "Pensador", esShiny = true, esHuevo = false, editable = true),
            resultado,
        )
    }

    // --- Cajas: box.list + box.get combinados ---

    @Test
    fun `las cajas juntan box-list (metadatos) con box-get (solo lo ocupado)`() {
        val metadatos = Puente.json.decodeFromString<List<BoxEntryDto>>(
            """[{"box":0,"name":"CAJA 1","slots":30},{"box":1,"name":null,"slots":30}]""",
        )
        val pokemon = Puente.json.decodeFromString<List<PokemonSummaryDto>>(
            """
            [
              {"at":{"source":"box","slot":0,"box":0,"team":null},"speciesId":10,"editable":true,"nickname":"CATERPIE","level":7,"isShiny":false,"isEgg":false},
              {"at":{"source":"box","slot":5,"box":0,"team":null},"speciesId":25,"editable":true,"nickname":"Sparky","level":12,"isShiny":true,"isEgg":false}
            ]
            """.trimIndent(),
        )

        val resultado = aCajas(metadatos, pokemon)

        assertEquals(
            listOf(
                Caja(
                    numero = 0,
                    nombre = "CAJA 1",
                    casillas = 30,
                    pokemon = listOf(
                        ResumenDePokemon(Casilla(OrigenDeCasilla.CAJA, 0, 0), 10, 7, "CATERPIE", false, false, true),
                        ResumenDePokemon(Casilla(OrigenDeCasilla.CAJA, 5, 0), 25, 12, "Sparky", true, false, true),
                    ),
                ),
                Caja(numero = 1, nombre = "", casillas = 30, pokemon = emptyList()),
            ),
            resultado,
        )
    }

    // --- CambioDePokemon -> PokemonPatch: solo lo que de verdad cambia ---

    @Test
    fun `un cambio vacio no manda nada`() {
        val json = Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), CambioDePokemon().aPatchDto())
        assertEquals(emptySet<String>(), json.jsonObject.keys)
    }

    @Test
    fun `solo mote y nivel viajan cuando es lo unico que cambio`() {
        val cambio = CambioDePokemon(nivel = 50, mote = "Kadabra")

        val json = Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), cambio.aPatchDto()).jsonObject

        assertEquals(setOf("nickname", "level"), json.keys)
        assertEquals("Kadabra", json.getValue("nickname").jsonPrimitive.content)
        assertEquals(50, json.getValue("level").jsonPrimitive.int)
    }

    @Test
    fun `los ivs parciales solo mandan los estadisticas que cambiaron`() {
        val cambio = CambioDePokemon(ivs = CambioDeEstadisticas(ps = 31, velocidad = 31))

        val json = Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), cambio.aPatchDto()).jsonObject

        assertEquals(setOf("ivs"), json.keys)
        val ivs = json.getValue("ivs").jsonObject
        assertEquals(setOf("health", "speed"), ivs.keys)
        assertEquals(31, ivs.getValue("health").jsonPrimitive.int)
        assertEquals(31, ivs.getValue("speed").jsonPrimitive.int)
    }

    @Test
    fun `un cambio con casi todo (menos ivs,evs) manda todos esos campos`() {
        val cambio = CambioDePokemon(
            especie = 65,
            naturaleza = 3,
            habilidad = 22,
            objeto = 0,
            pokeBall = 4,
            amistad = 255,
            esShiny = true,
            mote = "Alakazam",
            nivel = 36,
            movimientos = listOf(94, 95, 0, 0),
        )

        val json = Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), cambio.aPatchDto()).jsonObject

        assertEquals(
            setOf("species", "nature", "ability", "heldItem", "ball", "friendship", "isShiny", "nickname", "level", "moves"),
            json.keys,
        )
        assertTrue(json.getValue("isShiny").jsonPrimitive.content.toBoolean())
    }
}
