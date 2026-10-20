package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/*
 * Formas crudas del motor para la Pokédex global: `game.version` y `encounters.search` (ver
 * engine-bridge/node_modules/@pkhex-everywhere/engine/dist/generated/types.d.ts, `SaveVersion` y
 * `EncounterRow`, solo lectura).
 */

/** Lo que da `game.version`: el `GameVersion` numérico de PKHeX de la partida cargada, para pasárselo a `encounters.search`. */
@Serializable
data class SaveVersionDto(val version: String = "", val versionId: Int = 0, val generation: String = "", val generationId: Int = 0)

/** Una fila de `encounters.search`. */
@Serializable
data class EncounterRowDto(val speciesId: Int = 0, val location: String = "", val levelRange: String = "", val isEgg: Boolean = false)

internal fun <T> JsonElement.decodificar(serializador: KSerializer<T>): T = Puente.json.decodeFromJsonElement(serializador, this)
