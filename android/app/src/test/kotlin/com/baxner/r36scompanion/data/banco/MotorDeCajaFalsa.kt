package com.baxner.r36scompanion.data.banco

import com.baxner.r36scompanion.data.editor.LegalityDto
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.motor.Motor
import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * [Motor] falso para probar `BancoConMotor.prepararRetiro`: a diferencia de [MotorDeCajasReales],
 * este NO habla bytes reales de Gen 3 — `prepararRetiro` nunca llama a `PartidaGen3`, todo lo
 * mueve el motor (`box.previewFile` → `box.addFromFile` → `game.export`), así que le basta su
 * propio mundo de mentiras (especie + nivel en JSON) consistente entre lo que carga, lo que
 * recibe en `box.previewFile`/`addFromFile` y lo que exporta.
 */
class MotorDeCajaFalsa(var estado: EstadoDeCajaDePrueba = EstadoDeCajaDePrueba()) : Motor {
    /** Especies que ningún juego de esta partida falsa puede tener: `box.addFromFile` falla con `conversion-failed`. */
    var especiesRechazadas: Set<Int> = emptySet()
    private var cargada = false

    override suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement = when (metodo) {
        "game.load" -> {
            estado = Puente.json.decodeFromString(EstadoDeCajaDePrueba.serializer(), Puente.bytesDe(args[0]).toString(Charsets.UTF_8))
            cargada = true
            JsonNull
        }

        "game.close" -> {
            cargada = false
            JsonNull
        }

        "game.export" -> {
            exigirCargada()
            buildJsonObject {
                put("bytes", Puente.bytes(Puente.json.encodeToString(EstadoDeCajaDePrueba.serializer(), estado).toByteArray(Charsets.UTF_8)))
                put("fileName", JsonPrimitive("falsa.sav"))
            }
        }

        "box.previewFile" -> {
            exigirCargada()
            val archivo = decodificarArchivo(args[0])
            val rechazada = archivo.especie in especiesRechazadas
            Puente.json.encodeToJsonElement(
                FilePreviewDto.serializer(),
                FilePreviewDto(
                    pokemon = PokemonPreviewDto(speciesId = archivo.especie, level = archivo.nivel),
                    unofficial = rechazada,
                    changes = if (rechazada) emptyList() else listOf(TransferChangeDto(field = "originGame", before = "FireRed", after = "Falsa", reason = "transporter")),
                    legality = LegalityDto(valid = !rechazada, messages = if (rechazada) listOf("ningún juego puede tener esta especie") else emptyList()),
                ),
            )
        }

        "box.addFromFile" -> {
            exigirCargada()
            val archivo = decodificarArchivo(args[0])
            if (archivo.especie in especiesRechazadas) throw MotorException("conversion-failed", "ningún juego puede tener esta especie")
            val libre = primeraLibre() ?: throw MotorException("box-full", "no hay casillas libres")
            estado = estado.copy(ocupadas = estado.ocupadas + CasillaDePrueba(caja = libre.first, posicion = libre.second, especie = archivo.especie, nivel = archivo.nivel))
            Puente.json.encodeToJsonElement(AddedPokemonDto.serializer(), AddedPokemonDto(id = "p-1", at = PokemonHandleDto("box", libre.second, libre.first)))
        }

        else -> throw MotorException("unknown-call", "MotorDeCajaFalsa no implementa $metodo")
    }

    private fun decodificarArchivo(json: JsonElement): PokemonFileDePrueba =
        Puente.json.decodeFromString(PokemonFileDePrueba.serializer(), Puente.bytesDe(json).toString(Charsets.UTF_8))

    private fun primeraLibre(): Pair<Int, Int>? {
        for (caja in estado.cajas) {
            for (posicion in 0 until caja.casillas) {
                if (estado.ocupadas.none { it.caja == caja.numero && it.posicion == posicion }) return caja.numero to posicion
            }
        }
        return null
    }

    private fun exigirCargada() {
        if (!cargada) throw MotorException("no-save", "no hay partida cargada")
    }
}

@Serializable
data class CasillaDePrueba(val caja: Int, val posicion: Int, val especie: Int, val nivel: Int)

@Serializable
data class CajaDePruebaDeBanco(val numero: Int, val casillas: Int)

@Serializable
data class EstadoDeCajaDePrueba(
    val cajas: List<CajaDePruebaDeBanco> = listOf(CajaDePruebaDeBanco(0, 30)),
    val ocupadas: List<CasillaDePrueba> = emptyList(),
)

/** Los "bytes" de un Pokémon exportado en este mundo de mentiras: solo especie y nivel, como JSON. */
@Serializable
data class PokemonFileDePrueba(val especie: Int, val nivel: Int = 5)

fun PokemonFileDePrueba.aBytes(): ByteArray = Puente.json.encodeToString(PokemonFileDePrueba.serializer(), this).toByteArray(Charsets.UTF_8)

fun EstadoDeCajaDePrueba.aBytes(): ByteArray = Puente.json.encodeToString(EstadoDeCajaDePrueba.serializer(), this).toByteArray(Charsets.UTF_8)
