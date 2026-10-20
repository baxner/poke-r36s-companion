package com.baxner.r36scompanion.data.editor

import kotlinx.serialization.Serializable

/*
 * Formas crudas del motor para lo que usa el editor (ver
 * engine-bridge/node_modules/@pkhex-everywhere/engine/dist/generated/types.d.ts y client.d.ts del
 * árbol principal, solo lectura). Los nombres de campo son los del motor, en inglés; las
 * funciones `aXxx()`/`aXxxDto()` de más abajo traducen hacia y desde los tipos en español del
 * contrato (`Editor.kt`). Solo se declaran los campos que el contrato necesita: `Puente.json`
 * tiene `ignoreUnknownKeys = true`, así que el resto del JSON del motor se ignora sin problema.
 *
 * Estas funciones son puras (sin el motor de por medio) y se prueban con ejemplos de JSON real en
 * `TraduccionesDelMotorTest.kt`.
 */

@Serializable
data class PokemonHandleDto(val source: String, val slot: Int = 0, val box: Int? = null, val team: Int? = null)

@Serializable
data class StatValuesDto(
    val health: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val specialAttack: Int = 0,
    val specialDefense: Int = 0,
    val speed: Int = 0,
)

@Serializable
data class MoveSlotDto(val id: Int = 0, val pp: Int = 0, val maxPp: Int = 0)

@Serializable
data class LegalityDto(val valid: Boolean = true, val messages: List<String> = emptyList())

/** Lo que da `pokemon.details`. Ver `EditablePokemon` en types.d.ts. */
@Serializable
data class EditablePokemonDto(
    val species: Int? = null,
    val form: Int = 0,
    val gender: String? = null,
    val nature: Int = 0,
    val ability: Int = 0,
    val heldItem: Int = 0,
    val ball: Int = 0,
    val friendship: Int = 0,
    val language: Int = 0,
    val isShiny: Boolean = false,
    val isEgg: Boolean = false,
    val nickname: String = "",
    val level: Int = 0,
    val pid: Long = 0,
    val trainerId: Int = 0,
    val secretId: Int = 0,
    val originalTrainerName: String = "",
    val version: Int = 0,
    val metLocation: Int = 0,
    val metLevel: Int = 0,
    val ivs: StatValuesDto = StatValuesDto(),
    val evs: StatValuesDto = StatValuesDto(),
    val stats: StatValuesDto = StatValuesDto(),
    val moves: List<MoveSlotDto> = emptyList(),
    val legality: LegalityDto? = null,
)

/** Para mandar a `pokemon.update`. Ver `StatPatch` en types.d.ts. Todo nulo = no se toca. */
@Serializable
data class StatPatchDto(
    val health: Int? = null,
    val attack: Int? = null,
    val defense: Int? = null,
    val specialAttack: Int? = null,
    val specialDefense: Int? = null,
    val speed: Int? = null,
)

/** Para mandar a `pokemon.update`. Ver `PokemonPatch` en types.d.ts. Todo nulo = no se toca. */
@Serializable
data class PokemonPatchDto(
    val species: Int? = null,
    val nature: Int? = null,
    val ability: Int? = null,
    val heldItem: Int? = null,
    val ball: Int? = null,
    val friendship: Int? = null,
    val isShiny: Boolean? = null,
    val nickname: String? = null,
    val level: Int? = null,
    val ivs: StatPatchDto? = null,
    val evs: StatPatchDto? = null,
    val moves: List<Int>? = null,
)

@Serializable
data class ChoiceDto(val id: Int = 0, val name: String = "")

@Serializable
data class ItemChoiceDto(val id: Int = 0, val name: String = "")

/** Lo que da `pokemon.options`. Ver `PokemonOptions` en types.d.ts (sin `forms`: el contrato no edita la forma). */
@Serializable
data class PokemonOptionsDto(
    val species: List<ChoiceDto> = emptyList(),
    val abilities: List<ChoiceDto> = emptyList(),
    val metLocations: List<ChoiceDto> = emptyList(),
    val moves: List<ChoiceDto> = emptyList(),
    val heldItems: List<ItemChoiceDto> = emptyList(),
    val locked: List<String> = emptyList(),
)

/** Lo que da `party.get`/`box.get`. Ver `PokemonSummary` en types.d.ts. */
@Serializable
data class PokemonSummaryDto(
    val at: PokemonHandleDto,
    val speciesId: Int? = null,
    val editable: Boolean = true,
    val nickname: String = "",
    val level: Int = 0,
    val isShiny: Boolean = false,
    val isEgg: Boolean = false,
)

/** Lo que da `box.list`. Ver `BoxEntry` en types.d.ts. */
@Serializable
data class BoxEntryDto(val box: Int, val name: String? = null, val slots: Int = 0)

@Serializable
data class OwnedItemDto(val id: Int = 0, val count: Int = 0, val maxCount: Int = 0)

@Serializable
data class AddableItemDto(val id: Int = 0)

/** Un bolsillo de `inventory.get`. Ver `Pouch` en types.d.ts. */
@Serializable
data class PouchDto(val name: String = "", val items: List<OwnedItemDto> = emptyList(), val addable: List<AddableItemDto> = emptyList())

/** Para `inventory.setItem`. Ver `ItemHandle` en types.d.ts. */
@Serializable
data class ItemHandleDto(val pouch: String, val itemId: Int)

/** Lo que da `trainer.get`. Ver `TrainerCard` en types.d.ts. */
@Serializable
data class TrainerCardDto(val id: String = "", val name: String = "", val maxNameLength: Int = 0, val money: Long? = null)

/** Un elemento de `trainer.badges`. Ver `Badge` en types.d.ts. */
@Serializable
data class BadgeDto(val name: String = "", val earned: Boolean = false)

// --- Del motor (inglés) al contrato (español) ---

fun PokemonHandleDto.aCasilla(): Casilla = when (source) {
    "party" -> Casilla(origen = OrigenDeCasilla.EQUIPO, posicion = slot, caja = null)
    "box" -> Casilla(origen = OrigenDeCasilla.CAJA, posicion = slot, caja = box)
    else -> error("origen de casilla desconocido: $source")
}

fun StatValuesDto.aEstadisticas(): Estadisticas =
    Estadisticas(ps = health, ataque = attack, defensa = defense, ataqueEspecial = specialAttack, defensaEspecial = specialDefense, velocidad = speed)

fun MoveSlotDto.aMovimiento(): Movimiento = Movimiento(id = id, pp = pp, ppMaximos = maxPp)

fun LegalityDto.aLegalidad(): Legalidad = Legalidad(valida = valid, mensajes = messages)

fun EditablePokemonDto.aPokemonEditable(): PokemonEditable = PokemonEditable(
    especie = species,
    forma = form,
    genero = gender,
    naturaleza = nature,
    habilidad = ability,
    objeto = heldItem,
    pokeBall = ball,
    amistad = friendship,
    idioma = language,
    esShiny = isShiny,
    esHuevo = isEgg,
    mote = nickname,
    nivel = level,
    pid = pid,
    idDeEntrenador = trainerId,
    idSecreto = secretId,
    entrenadorOriginal = originalTrainerName,
    versionDeOrigen = version,
    lugarDeCaptura = metLocation,
    nivelDeCaptura = metLevel,
    ivs = ivs.aEstadisticas(),
    evs = evs.aEstadisticas(),
    estadisticas = stats.aEstadisticas(),
    movimientos = moves.map { it.aMovimiento() },
    legalidad = legality?.aLegalidad(),
)

fun PokemonOptionsDto.aOpcionesDePokemon(): OpcionesDePokemon = OpcionesDePokemon(
    especies = species.map { it.id },
    habilidades = abilities.map { it.id },
    movimientos = moves.map { it.id },
    objetos = heldItems.map { it.id },
    lugares = metLocations.map { it.id },
    bloqueados = locked.toSet(),
)

fun PokemonSummaryDto.aResumenDePokemon(): ResumenDePokemon = ResumenDePokemon(
    casilla = at.aCasilla(),
    especie = speciesId,
    nivel = level,
    mote = nickname,
    esShiny = isShiny,
    esHuevo = isEgg,
    editable = editable,
)

/** Junta `box.list` (metadatos, cajas vacías incluidas) con `box.get` (solo las casillas ocupadas) en la lista de [Caja] del contrato. */
fun aCajas(cajas: List<BoxEntryDto>, pokemonDeLasCajas: List<PokemonSummaryDto>): List<Caja> {
    val porNumeroDeCaja = pokemonDeLasCajas.mapNotNull { dto -> dto.at.box?.let { it to dto } }.groupBy({ it.first }, { it.second })
    return cajas.map { caja ->
        Caja(
            numero = caja.box,
            nombre = caja.name ?: "",
            casillas = caja.slots,
            pokemon = (porNumeroDeCaja[caja.box] ?: emptyList()).map { it.aResumenDePokemon() },
        )
    }
}

fun PouchDto.aBolsillo(): Bolsillo = Bolsillo(
    nombre = name,
    objetos = items.map { ObjetoEnMochila(id = it.id, cantidad = it.count, maximo = it.maxCount) },
    agregables = addable.map { it.id },
)

fun TrainerCardDto.aEntrenador(): Entrenador = Entrenador(nombre = name, maximoDeLetras = maxNameLength, idDeEntrenador = id, dinero = money)

fun BadgeDto.aMedalla(): Medalla = Medalla(nombreEnIngles = name, ganada = earned)

// --- Del contrato (español) al motor (inglés), para mandar en `pokemon.update` ---

fun Casilla.aHandleDto(): PokemonHandleDto = when (origen) {
    OrigenDeCasilla.EQUIPO -> PokemonHandleDto(source = "party", slot = posicion, box = null, team = null)
    OrigenDeCasilla.CAJA -> PokemonHandleDto(source = "box", slot = posicion, box = caja, team = null)
}

fun CambioDeEstadisticas.aPatchDto(): StatPatchDto = StatPatchDto(
    health = ps,
    attack = ataque,
    defense = defensa,
    specialAttack = ataqueEspecial,
    specialDefense = defensaEspecial,
    speed = velocidad,
)

fun CambioDePokemon.aPatchDto(): PokemonPatchDto = PokemonPatchDto(
    species = especie,
    nature = naturaleza,
    ability = habilidad,
    heldItem = objeto,
    ball = pokeBall,
    friendship = amistad,
    isShiny = esShiny,
    nickname = mote,
    level = nivel,
    ivs = ivs?.aPatchDto(),
    evs = evs?.aPatchDto(),
    moves = movimientos,
)
