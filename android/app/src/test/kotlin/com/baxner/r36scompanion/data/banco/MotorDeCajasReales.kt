package com.baxner.r36scompanion.data.banco

import com.baxner.r36scompanion.data.editor.EditablePokemonDto
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * [Motor] falso que sí entiende el binario real de una partida Gen 3 (vía [PartidaGen3] y
 * `especieYNivelDeCaja`, ver `data/gen3/PartidaSintetica.kt`). Hace falta para probar
 * `BancoConMotor.prepararDeposito`: ese método llama a `PartidaGen3.leer`/`conCasillaVacia`
 * directo sobre los MISMOS bytes que carga en el motor, así que el motor falso tiene que hablar
 * el mismo formato para que `pokemon.details`, `pokemon.export`, `party.get` y `box.get` tengan
 * sentido ahí. Solo lee: el depósito nunca manda `box.addFromFile` (eso lo prueba
 * [MotorDeCajaFalsa], para `prepararRetiro`).
 */
class MotorDeCajasReales : Motor {
    private var cargada: PartidaGen3? = null

    override suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement = when (metodo) {
        "game.load" -> {
            val bytes = Puente.bytesDe(args[0])
            cargada = PartidaGen3.leer(bytes) ?: throw MotorException("invalid-save", "MotorDeCajasReales no reconoce estos bytes")
            JsonNull
        }

        "game.close" -> {
            cargada = null
            JsonNull
        }

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

        "pokemon.details" -> {
            val partida = exigirCargada()
            val handle = Puente.json.decodeFromJsonElement(PokemonHandleDto.serializer(), args[0])
            val caja = handle.box ?: throw MotorException("bad-arguments", "sin caja")
            val (especie, nivel) = partida.especieYNivelDeCaja(caja, handle.slot) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
            Puente.json.encodeToJsonElement(
                EditablePokemonDto.serializer(),
                EditablePokemonDto(species = especie, level = nivel, nickname = "MOTORFALSO", originalTrainerName = "ROJO", trainerId = 12345, version = 10),
            )
        }

        "pokemon.export" -> {
            val partida = exigirCargada()
            val handle = Puente.json.decodeFromJsonElement(PokemonHandleDto.serializer(), args[0])
            val caja = handle.box ?: throw MotorException("bad-arguments", "sin caja")
            if (partida.especieYNivelDeCaja(caja, handle.slot) == null) throw MotorException("not-found", "no hay Pokémon en esa casilla")
            val bytes = partida.casillaDeCaja(caja, handle.slot)
            buildJsonObject {
                put("bytes", Puente.bytes(bytes))
                put("fileName", JsonPrimitive("pokemon.pk3"))
            }
        }

        else -> throw MotorException("unknown-call", "MotorDeCajasReales no implementa $metodo")
    }

    private fun exigirCargada(): PartidaGen3 = cargada ?: throw MotorException("no-save", "no hay partida cargada")
}
