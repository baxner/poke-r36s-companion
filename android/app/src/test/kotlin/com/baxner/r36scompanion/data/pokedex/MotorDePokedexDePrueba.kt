package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.editor.PokemonSummaryDto
import com.baxner.r36scompanion.data.gen3.PartidaGen3
import com.baxner.r36scompanion.data.gen3.especieYNivelDeCaja
import com.baxner.r36scompanion.data.motor.Motor
import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * [Motor] falso para probar [PokedexGlobalConRespaldos]: lee bytes reales de Gen 3 para
 * `party.get`/`box.get` (como `MotorDeCajasReales` de `data/banco/`, por la misma razón: la
 * Pokédex global llama a [PartidaGen3] directo sobre los mismos bytes que carga en el motor), y
 * además `game.version`/`encounters.search` (configurables) para `dondeAtrapar`.
 */
class MotorDePokedexDePrueba(var versionId: Int = 10) : Motor {
    private var cargada: PartidaGen3? = null

    /** `(versionId, especie) -> filas`; sin entrada, `encounters.search` da vacío. */
    var encuentros: Map<Pair<Int, Int>, List<EncounterRowDto>> = emptyMap()

    override suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement = when (metodo) {
        "game.load" -> {
            cargada = PartidaGen3.leer(Puente.bytesDe(args[0])) ?: throw MotorException("invalid-save", "MotorDePokedexDePrueba no reconoce estos bytes")
            JsonNull
        }

        "game.close" -> {
            cargada = null
            JsonNull
        }

        "game.version" -> Puente.json.encodeToJsonElement(SaveVersionDto.serializer(), SaveVersionDto(versionId = versionId, generation = "Gen3", generationId = 3))

        "party.get" -> Puente.json.encodeToJsonElement(ListSerializer(PokemonSummaryDto.serializer()), emptyList())

        "box.get" -> {
            val partida = exigirCargada()
            val resumenes = buildList {
                for (caja in 0 until PartidaGen3.Cajas) {
                    for (posicion in 0 until PartidaGen3.CasillasPorCaja) {
                        val (especie, nivel) = partida.especieYNivelDeCaja(caja, posicion) ?: continue
                        add(PokemonSummaryDto(at = PokemonHandleDto("box", posicion, caja), speciesId = especie, level = nivel, editable = true))
                    }
                }
            }
            Puente.json.encodeToJsonElement(ListSerializer(PokemonSummaryDto.serializer()), resumenes)
        }

        "encounters.search" -> {
            exigirCargada()
            val version = args[0].jsonPrimitive.int
            val especie = args[1].jsonPrimitive.int
            Puente.json.encodeToJsonElement(ListSerializer(EncounterRowDto.serializer()), encuentros[version to especie] ?: emptyList())
        }

        else -> throw MotorException("unknown-call", "MotorDePokedexDePrueba no implementa $metodo")
    }

    private fun exigirCargada(): PartidaGen3 = cargada ?: throw MotorException("no-save", "no hay partida cargada")
}
