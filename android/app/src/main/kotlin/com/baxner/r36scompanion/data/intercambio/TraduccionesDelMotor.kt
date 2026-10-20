package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.editor.LegalityDto
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.editor.PokemonPatchDto
import com.baxner.r36scompanion.data.editor.PokemonSummaryDto
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/*
 * Formas crudas del motor para `transfer.*` (ver
 * engine-bridge/node_modules/@pkhex-everywhere/engine/dist/generated/types.d.ts: `TransferOffer`,
 * `TransferPreview`, `OfferedPokemon`, `RefusedPokemon`, `TransferResult`, `TransferChange`,
 * `TransferSaveChange`, solo lectura). Reutiliza `PokemonHandleDto`, `PokemonSummaryDto`,
 * `PokemonPatchDto` y `LegalityDto` de `data/editor/TraduccionesDelMotor.kt`.
 *
 * `transfer.preview`/`transfer.commit` se mandan como JSON armado a mano (ver [aJsonDeOferta]),
 * no como un DTO serializable con los bytes de las llegadas adentro: el puente del motor
 * reconoce `{"$bytes": "<base64>"}` a cualquier profundidad del JSON (ver
 * `engine-bridge/src/puente.js`, `codificar`/`decodificar`), así que basta construir el objeto a
 * mano con `Puente.bytes(...)` donde corresponda, igual que hace `SesionDePartida.exportar()`
 * con la respuesta.
 */

/** Un cambio que hace el traspaso en el propio Pokémon (`TransferChange`). */
@Serializable
data class TransferChangeDto(val field: String = "", val before: String? = null, val after: String? = null, val reason: String = "")

/** Un cambio que el traspaso hace en una de las dos partidas (`TransferSaveChange`). */
@Serializable
data class TransferSaveChangeDto(val kind: String = "", val save: String = "", val label: String = "")

/** Un Pokémon de la oferta que sí se puede mover (`OfferedPokemon`). */
@Serializable
data class OfferedPokemonDto(
    val direction: String,
    val route: List<String> = emptyList(),
    val from: PokemonSummaryDto,
    val arrives: PokemonSummaryDto,
    val changes: List<TransferChangeDto> = emptyList(),
    val saveChanges: List<TransferSaveChangeDto> = emptyList(),
    val legality: LegalityDto,
)

/** Un Pokémon de la oferta que el traspaso rechaza (`RefusedPokemon`). */
@Serializable
data class RefusedPokemonDto(val direction: String, val at: PokemonHandleDto, val reason: String = "")

/** Lo que da `transfer.preview`. */
@Serializable
data class TransferPreviewDto(val offers: List<OfferedPokemonDto> = emptyList(), val refused: List<RefusedPokemonDto> = emptyList())

/** `'send'`/`'receive'` del motor → [Direccion] del contrato. */
fun String.aDireccion(): Direccion = when (this) {
    "send" -> Direccion.DE_A_HACIA_B
    "receive" -> Direccion.DE_B_HACIA_A
    else -> error("dirección de traspaso desconocida: $this")
}

/** En inglés del motor, como dice el contrato: la pantalla lo resume. */
fun TransferChangeDto.aTexto(): String = "$field: ${before ?: "—"} → ${after ?: "—"} ($reason)"

fun TransferSaveChangeDto.aTexto(): String = "$kind ($save): $label"

/** Lo que hay que mandar como una llegada de [aJsonDeOferta]: `at` es la casilla en el offer (`send`/`receive`), no en el destino. */
data class LlegadaConBytes(val at: PokemonHandleDto, val bytes: ByteArray, val patch: PokemonPatchDto)

/** Construye el JSON de un `TransferOffer` a mano (ver el KDoc de arriba sobre por qué no usar un DTO serializable con bytes adentro). */
fun aJsonDeOferta(envia: List<PokemonHandleDto>, recibe: List<PokemonHandleDto>, llegadas: List<LlegadaConBytes>): JsonElement = buildJsonObject {
    put("send", JsonArray(envia.map { Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), it) }))
    put("receive", JsonArray(recibe.map { Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), it) }))
    if (llegadas.isNotEmpty()) {
        put(
            "arrivals",
            JsonArray(
                llegadas.map { llegada ->
                    buildJsonObject {
                        put("at", Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), llegada.at))
                        put("bytes", Puente.bytes(llegada.bytes))
                        put("patch", Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), llegada.patch))
                    }
                },
            ),
        )
    }
}

internal fun <T> JsonElement.decodificar(serializador: KSerializer<T>): T = Puente.json.decodeFromJsonElement(serializador, this)

/** El campo [campo] de este objeto JSON (para navegar `{save: {bytes: ...}}` sin un DTO completo). */
internal fun JsonElement.campo(campo: String): JsonElement = jsonObject.getValue(campo)

/** El campo `bytes` de un `ExportedPokemon`/`ExportedSave` (mismo patrón que `SesionDePartida.exportar()`). */
internal fun JsonElement.bytesDelCampo(campo: String = "bytes"): ByteArray = Puente.bytesDe(jsonObject.getValue(campo))
