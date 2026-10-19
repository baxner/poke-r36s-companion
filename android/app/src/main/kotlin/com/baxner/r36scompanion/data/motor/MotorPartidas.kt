package com.baxner.r36scompanion.data.motor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

/**
 * El motor con tipos, para lo que hace la app con una partida: cargarla, leer su resumen y
 * exportarla. El motor tiene una sola partida cargada a la vez, así que todo pasa por
 * [conPartida], que la carga, corre el bloque y la cierra, sin que otra llamada se meta en medio.
 */
class MotorPartidas(private val motor: Motor) {
    private val turno = Mutex()

    /**
     * Carga [bytes] y corre [bloque] con esa partida. [formato] es el id de un formato de hack
     * (`radicalred`, …) o null para la detección de PKHeX. Si el motor no la reconoce, lanza
     * [MotorException] con `invalid-save` (o `format-choice-required` con los candidatos).
     */
    suspend fun <T> conPartida(bytes: ByteArray, nombre: String, formato: String? = null, bloque: suspend SesionDePartida.() -> T): T =
        turno.withLock {
            motor.llamar("game.load", Puente.bytes(bytes), Puente.texto(nombre), Puente.texto(formato))
            try {
                SesionDePartida(motor).bloque()
            } finally {
                runCatching { motor.llamar("game.close") }
            }
        }

    /** Las versiones con las que el motor puede crear una partida en blanco (útil en pruebas). */
    suspend fun versionesEnBlanco(): List<VersionDto> = turno.withLock {
        motor.llamar("game.blankVersions").como(ListSerializer(VersionDto.serializer()))
    }

    /** Crea una partida en blanco de [versionId] y devuelve sus bytes (útil en pruebas). */
    suspend fun partidaEnBlanco(versionId: Int): ByteArray = turno.withLock {
        motor.llamar("game.loadBlank", Puente.numero(versionId))
        try {
            Puente.bytesDe(motor.llamar("game.export").jsonObject.getValue("bytes"))
        } finally {
            runCatching { motor.llamar("game.close") }
        }
    }
}

/** Lo que se puede hacer con la partida cargada en [MotorPartidas.conPartida]. */
class SesionDePartida internal constructor(private val motor: Motor) {

    suspend fun resumen(): ResumenDePartida {
        val partida = motor.llamar("game.get").como(SaveSummaryDto.serializer())
        val entrenador = motor.llamar("trainer.get").como(TrainerCardDto.serializer())
        val progreso = motor.llamar("game.progress").como(GameProgressDto.serializer())
        val equipo = motor.llamar("party.get").como(ListSerializer(PokemonSummaryDto.serializer()))
        return ResumenDePartida(partida, entrenador, progreso, equipo)
    }

    /** La partida completa con todos los cambios hechos hasta ahora, lista para escribir. */
    suspend fun exportar(): ByteArray = Puente.bytesDe(motor.llamar("game.export").jsonObject.getValue("bytes"))

    /** Para lo que todavía no tiene función con tipos: la llamada cruda al motor. */
    suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement = motor.llamar(metodo, *args)
}

data class ResumenDePartida(
    val partida: SaveSummaryDto,
    val entrenador: TrainerCardDto,
    val progreso: GameProgressDto,
    val equipo: List<PokemonSummaryDto>,
)

// Formas del motor (ver engine-bridge/node_modules/@pkhex-everywhere/engine/dist/generated/types.d.ts).
// Solo los campos que usa la app; el resto se ignora (Puente.json tiene ignoreUnknownKeys).

@Serializable
data class SaveSummaryDto(
    val fileName: String? = null,
    val version: String,
    val generation: Int,
    val hasEvents: Boolean = false,
    val format: SaveFormatDto? = null,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class SaveFormatDto(val id: String, val name: String, val baseGame: String? = null)

@Serializable
data class TrainerCardDto(
    /** `TID/SID` como lo muestra el juego. */
    val id: String,
    val name: String,
    val money: Long? = null,
    val rival: String? = null,
)

@Serializable
data class GameProgressDto(val playTime: PlayTimeDto? = null, val badges: BadgeCountDto? = null, val pokedex: PokedexCountDto? = null)

@Serializable
data class PlayTimeDto(val hours: Int, val minutes: Int, val seconds: Int)

@Serializable
data class BadgeCountDto(val earned: Int, val total: Int)

@Serializable
data class PokedexCountDto(val seen: Int, val caught: Int)

@Serializable
data class PokemonSummaryDto(
    val speciesId: Int? = null,
    val species: String,
    val nickname: String? = null,
    val level: Int = 0,
    val isShiny: Boolean = false,
    val isEgg: Boolean = false,
    val isUnknown: Boolean = false,
    val editable: Boolean = true,
)

@Serializable
data class VersionDto(val id: Int, val name: String)

internal fun <T> JsonElement.como(serializador: KSerializer<T>): T {
    if (this == JsonNull) throw MotorException("respuesta-vacia", "el motor no devolvió nada")
    return Puente.json.decodeFromJsonElement(serializador, this)
}
