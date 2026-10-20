package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.editor.EditablePokemonDto
import com.baxner.r36scompanion.data.editor.LegalityDto
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.editor.PokemonPatchDto
import com.baxner.r36scompanion.data.editor.PokemonSummaryDto
import com.baxner.r36scompanion.data.motor.Motor
import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * [Motor] falso para probar [IntercambiosConMotor]: no toca bytes reales de Gen 3 en absoluto
 * (`transfer.*` nunca pasa por [com.baxner.r36scompanion.data.gen3.PartidaGen3]), así que le
 * basta su propio mundo de mentiras (especie, nivel y objeto, en JSON) para "mía" (`game.load`) y
 * "compañera" (`transfer.open`). Solo entiende casillas de caja (`source = "box"`): es lo único
 * que ejercitan las pruebas de intercambio.
 */
class MotorFalsoDeIntercambio(var mia: EstadoDeIntercambioDePrueba = EstadoDeIntercambioDePrueba()) : Motor {
    private var compañera: EstadoDeIntercambioDePrueba? = null
    private var cargada = false

    /** Casillas (del lado que sea) que el traspaso rechaza, con el motivo (`TransferRefusal`). */
    var rechazos: Map<PokemonHandleDto, String> = emptyMap()

    override suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement = when (metodo) {
        "game.load" -> {
            mia = decodeEstado(args[0])
            cargada = true
            JsonNull
        }

        "game.close" -> {
            cargada = false
            compañera = null
            JsonNull
        }

        "transfer.open" -> {
            exigirCargada()
            compañera = decodeEstado(args[0])
            JsonNull
        }

        "transfer.close" -> {
            compañera = null
            JsonNull
        }

        "transfer.details" -> {
            val handle = decodeHandle(args[0])
            val cual = textoDe(args[1])
            val pokemon = buscar(cual, handle) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
            Puente.json.encodeToJsonElement(
                EditablePokemonDto.serializer(),
                EditablePokemonDto(species = pokemon.especie, level = pokemon.nivel, heldItem = pokemon.heldItem, isEgg = pokemon.isEgg),
            )
        }

        "transfer.export" -> {
            val handle = decodeHandle(args[0])
            val cual = textoDe(args[1])
            val pokemon = buscar(cual, handle) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
            buildJsonObject {
                put("bytes", Puente.bytes(pokemon.aBytesDeArchivo()))
                put("fileName", JsonPrimitive("pokemon.pk3"))
            }
        }

        "transfer.preview" -> {
            exigirAbierta()
            Puente.json.encodeToJsonElement(TransferPreviewDto.serializer(), previsualizar(decodeOferta(args[0])))
        }

        "transfer.commit" -> {
            exigirAbierta()
            confirmarTraspaso(decodeOferta(args[0]))
        }

        else -> throw MotorException("unknown-call", "MotorFalsoDeIntercambio no implementa $metodo")
    }

    private fun previsualizar(oferta: OfertaCruda): TransferPreviewDto {
        val ofertas = mutableListOf<OfferedPokemonDto>()
        val rechazados = mutableListOf<RefusedPokemonDto>()
        oferta.send.forEachIndexed { i, handle -> ofrecerOrechazar("send", handle, "mine", oferta, i, ofertas, rechazados) }
        oferta.receive.forEachIndexed { i, handle -> ofrecerOrechazar("receive", handle, "partner", oferta, i, ofertas, rechazados) }
        return TransferPreviewDto(offers = ofertas, refused = rechazados)
    }

    private fun ofrecerOrechazar(
        direccion: String,
        handle: PokemonHandleDto,
        cual: String,
        oferta: OfertaCruda,
        indice: Int,
        ofertas: MutableList<OfferedPokemonDto>,
        rechazados: MutableList<RefusedPokemonDto>,
    ) {
        val motivo = rechazos[handle]
        if (motivo != null) {
            rechazados += RefusedPokemonDto(direction = direccion, at = handle, reason = motivo)
            return
        }
        val pokemon = buscar(cual, handle) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
        val arrival = oferta.arrivals.find { it.at == handle }
        val especieFinal = arrival?.patch?.species ?: pokemon.especie
        ofertas += OfferedPokemonDto(
            direction = direccion,
            route = listOf("link"),
            from = PokemonSummaryDto(at = handle, speciesId = pokemon.especie, level = pokemon.nivel, isEgg = pokemon.isEgg),
            arrives = PokemonSummaryDto(at = PokemonHandleDto("box", indice, 0), speciesId = especieFinal, level = pokemon.nivel, isEgg = pokemon.isEgg),
            changes = if (arrival != null) {
                listOf(TransferChangeDto(field = "species", before = pokemon.especie.toString(), after = especieFinal.toString(), reason = "tradeEvolution"))
            } else {
                emptyList()
            },
            saveChanges = if (arrival != null) listOf(TransferSaveChangeDto(kind = "pokedexCaught", save = "receiver", label = "#$especieFinal")) else emptyList(),
            legality = LegalityDto(valid = true),
        )
    }

    private fun confirmarTraspaso(oferta: OfertaCruda): JsonElement {
        if (oferta.send.any { rechazos.containsKey(it) } || oferta.receive.any { rechazos.containsKey(it) }) {
            throw MotorException("transfer-refused", "hay Pokémon que el traspaso rechaza")
        }
        val companeraOriginal = compañera ?: throw MotorException("no-transfer", "no hay traspaso abierto")

        val saliendoDeMia = oferta.send.map { handle -> handle to (buscarEn(mia, handle) ?: throw MotorException("not-found", "$handle")) }
        val saliendoDeCompañera = oferta.receive.map { handle -> handle to (buscarEn(companeraOriginal, handle) ?: throw MotorException("not-found", "$handle")) }

        val miaSinEnviados = mia.pokemon.filterNot { p -> oferta.send.any { it.box == p.caja && it.slot == p.posicion } }
        val companeraSinRecibidos = companeraOriginal.pokemon.filterNot { p -> oferta.receive.any { it.box == p.caja && it.slot == p.posicion } }

        val llegandoACompañera = saliendoDeMia.mapIndexed { i, (handle, p) -> aLlegada(oferta, handle, p, destino = companeraSinRecibidos.size + i) }
        val llegandoAMia = saliendoDeCompañera.mapIndexed { i, (handle, p) -> aLlegada(oferta, handle, p, destino = miaSinEnviados.size + i) }

        mia = EstadoDeIntercambioDePrueba(miaSinEnviados + llegandoAMia)
        compañera = EstadoDeIntercambioDePrueba(companeraSinRecibidos + llegandoACompañera)

        return buildJsonObject {
            put("save", buildJsonObject { put("bytes", Puente.bytes(mia.aBytes())) })
            put("partner", buildJsonObject { put("bytes", Puente.bytes(compañera!!.aBytes())) })
            put("arrived", JsonArray(emptyList()))
        }
    }

    private fun aLlegada(oferta: OfertaCruda, handle: PokemonHandleDto, p: PokemonDeIntercambioDePrueba, destino: Int): PokemonDeIntercambioDePrueba {
        val arrival = oferta.arrivals.find { it.at == handle }
        return p.copy(caja = 0, posicion = destino, especie = arrival?.patch?.species ?: p.especie, heldItem = arrival?.patch?.heldItem ?: p.heldItem)
    }

    private fun buscarEn(estado: EstadoDeIntercambioDePrueba, handle: PokemonHandleDto): PokemonDeIntercambioDePrueba? =
        estado.pokemon.firstOrNull { it.caja == handle.box && it.posicion == handle.slot }

    private fun buscar(cual: String, handle: PokemonHandleDto): PokemonDeIntercambioDePrueba? = when (cual) {
        "mine" -> buscarEn(mia, handle)
        "partner" -> compañera?.let { buscarEn(it, handle) }
        else -> null
    }

    private fun decodeHandle(json: JsonElement): PokemonHandleDto = Puente.json.decodeFromJsonElement(PokemonHandleDto.serializer(), json)

    private fun decodeEstado(json: JsonElement): EstadoDeIntercambioDePrueba =
        Puente.json.decodeFromString(EstadoDeIntercambioDePrueba.serializer(), Puente.bytesDe(json).toString(Charsets.UTF_8))

    private fun decodeOferta(json: JsonElement): OfertaCruda {
        val objeto = json.jsonObject
        val send = objeto.getValue("send").jsonArray.map { decodeHandle(it) }
        val receive = objeto.getValue("receive").jsonArray.map { decodeHandle(it) }
        val arrivals = (objeto["arrivals"] as? JsonArray)?.map { llegada ->
            val lo = llegada.jsonObject
            LlegadaCruda(
                at = decodeHandle(lo.getValue("at")),
                patch = Puente.json.decodeFromJsonElement(PokemonPatchDto.serializer(), lo.getValue("patch")),
            )
        } ?: emptyList()
        return OfertaCruda(send, receive, arrivals)
    }

    private fun textoDe(json: JsonElement): String = json.jsonPrimitive.content

    private fun exigirCargada() {
        if (!cargada) throw MotorException("no-save", "no hay partida cargada")
    }

    private fun exigirAbierta() {
        exigirCargada()
        if (compañera == null) throw MotorException("no-transfer", "no hay traspaso abierto")
    }
}

private data class LlegadaCruda(val at: PokemonHandleDto, val patch: PokemonPatchDto)
private data class OfertaCruda(val send: List<PokemonHandleDto>, val receive: List<PokemonHandleDto>, val arrivals: List<LlegadaCruda>)

@Serializable
data class PokemonDeIntercambioDePrueba(val caja: Int, val posicion: Int, val especie: Int, val nivel: Int = 10, val heldItem: Int = 0, val isEgg: Boolean = false)

@Serializable
data class EstadoDeIntercambioDePrueba(val pokemon: List<PokemonDeIntercambioDePrueba> = emptyList())

/** Los "bytes" de un Pokémon exportado en este mundo de mentiras: especie, nivel y objeto, como JSON. */
@Serializable
data class PokemonFileDeIntercambioDePrueba(val especie: Int, val nivel: Int, val heldItem: Int, val isEgg: Boolean = false)

fun EstadoDeIntercambioDePrueba.aBytes(): ByteArray = Puente.json.encodeToString(EstadoDeIntercambioDePrueba.serializer(), this).toByteArray(Charsets.UTF_8)

private fun PokemonDeIntercambioDePrueba.aBytesDeArchivo(): ByteArray =
    Puente.json.encodeToString(PokemonFileDeIntercambioDePrueba.serializer(), PokemonFileDeIntercambioDePrueba(especie, nivel, heldItem, isEgg)).toByteArray(Charsets.UTF_8)
