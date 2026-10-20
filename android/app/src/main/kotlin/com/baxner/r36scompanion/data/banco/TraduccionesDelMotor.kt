package com.baxner.r36scompanion.data.banco

import com.baxner.r36scompanion.data.editor.LegalityDto
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/*
 * Formas crudas del motor para lo que usa el banco: `box.previewFile` y `box.addFromFile` (ver
 * engine-bridge/node_modules/@pkhex-everywhere/engine/dist/generated/types.d.ts, `FilePreview`,
 * `AddedPokemon`, `TransferChange`, solo lectura). `pokemon.export` y `pokemon.details` ya los
 * cubre `data/editor/TraduccionesDelMotor.kt` (`EditablePokemonDto`) y `SesionDePartida.exportar()`
 * (el patrón de leer el campo `bytes` a mano, aquí [bytesDelCampo]): se reutilizan.
 */

@Serializable
data class PokemonPreviewDto(
    val speciesId: Int? = null,
    val level: Int = 0,
    val nickname: String = "",
    val isShiny: Boolean = false,
    val isEgg: Boolean = false,
)

/** Un cambio que haría la conversión al meter un archivo (`box.previewFile`/`box.addFromFile`). */
@Serializable
data class TransferChangeDto(val field: String = "", val before: String? = null, val after: String? = null, val reason: String = "")

/** Lo que da `box.previewFile`. */
@Serializable
data class FilePreviewDto(
    val pokemon: PokemonPreviewDto = PokemonPreviewDto(),
    val unofficial: Boolean = false,
    val changes: List<TransferChangeDto> = emptyList(),
    val legality: LegalityDto? = null,
)

/** Lo que da `box.addFromFile`. Reusa `PokemonHandleDto` del editor (mismo `PokemonHandle` del motor). */
@Serializable
data class AddedPokemonDto(val id: String = "", val at: PokemonHandleDto)

/** En inglés del motor, como dice el contrato: la pantalla lo resume. */
fun TransferChangeDto.aTexto(): String = "$field: ${before ?: "—"} → ${after ?: "—"} ($reason)"

internal fun <T> JsonElement.decodificar(serializador: KSerializer<T>): T = Puente.json.decodeFromJsonElement(serializador, this)

/** El campo `bytes` de un `ExportedPokemon`/`ExportedSave` (ver `SesionDePartida.exportar()`, mismo patrón). */
internal fun JsonElement.bytesDelCampo(campo: String): ByteArray = Puente.bytesDe(jsonObject.getValue(campo))
