package com.baxner.r36scompanion.data.editor

import com.baxner.r36scompanion.data.motor.Motor
import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * [Motor] falso para probar [SesionDeEdicionConMotor] en la JVM: el motor de verdad solo corre en
 * el WebView (ver `MotorWebViewTest`, androidTest). Guarda una partida en memoria (equipo, cajas,
 * entrenador, medallas y mochila) y contesta las llamadas que usa el editor con el mismo JSON que
 * da el motor real: reutiliza los DTO de `TraduccionesDelMotor.kt`, así que las pruebas de
 * [EditorDePartidasConMotor] también ejercitan esa traducción de ida y vuelta.
 *
 * Los "bytes" de la partida son el JSON de [EstadoDePruebaDelMotor]: `game.load` lo decodifica y
 * `game.export` lo vuelve a codificar. Si nada cambió entre cargar y exportar, los bytes salen
 * idénticos; si algo cambió, distintos. Eso es lo único que le importa a [SesionDeEdicionConMotor].
 */
class MotorFalso(var estado: EstadoDePruebaDelMotor = partidaDePrueba()) : Motor {
    var cargas = 0
        private set
    var cierres = 0
        private set
    var exportaciones = 0
        private set

    /** Si es `true`, `trainer.badges` contesta `null` (como fuera de la 1a-3a generación o en un hack). */
    var sinMedallas = false

    private var cargada = false
    private var nombreDeLaPartidaCargada = "save.sav"
    private val fallosProgramados = mutableMapOf<String, MotorException>()

    /** La próxima vez que se llame a [metodo], falla con [falla] en vez de hacer lo de siempre. Se consume una sola vez. */
    fun fallarLaProximaVezEn(metodo: String, falla: MotorException) {
        fallosProgramados[metodo] = falla
    }

    /** Los bytes de [estado] ahora mismo, para abrir una sesión sin pasar por `game.load`/`game.export`. */
    fun bytesDeAhora(): ByteArray = estado.aBytes()

    override suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement {
        fallosProgramados.remove(metodo)?.let { throw it }
        return when (metodo) {
            "game.load" -> {
                val bytes = Puente.bytesDe(args[0])
                nombreDeLaPartidaCargada = (args.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: nombreDeLaPartidaCargada
                estado = try {
                    bytes.aEstado()
                } catch (e: Exception) {
                    throw MotorException("invalid-save", "MotorFalso no reconoce estos bytes: ${e.message}")
                }
                cargada = true
                cargas++
                JsonNull
            }

            "game.close" -> {
                cargada = false
                cierres++
                JsonNull
            }

            "game.export" -> {
                exigirCargada()
                exportaciones++
                buildJsonObject {
                    put("bytes", Puente.bytes(estado.aBytes()))
                    put("fileName", JsonPrimitive(nombreDeLaPartidaCargada))
                }
            }

            "party.get" -> {
                exigirCargada()
                Puente.json.encodeToJsonElement(
                    ListSerializer(PokemonSummaryDto.serializer()),
                    estado.equipo.mapIndexed { i, p -> p.aResumenJson(PokemonHandleDto("party", i, null, null)) },
                )
            }

            "box.get" -> {
                exigirCargada()
                Puente.json.encodeToJsonElement(
                    ListSerializer(PokemonSummaryDto.serializer()),
                    estado.cajaPokemon.map { it.pokemon.aResumenJson(PokemonHandleDto("box", it.posicion, it.caja, null)) },
                )
            }

            "box.list" -> {
                exigirCargada()
                Puente.json.encodeToJsonElement(
                    ListSerializer(BoxEntryDto.serializer()),
                    estado.cajasMeta.map { BoxEntryDto(box = it.numero, name = it.nombre, slots = it.casillas) },
                )
            }

            "pokemon.details" -> {
                exigirCargada()
                val mon = localizar(decodificarHandle(args[0])) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
                Puente.json.encodeToJsonElement(EditablePokemonDto.serializer(), mon.aDetalleJson())
            }

            "pokemon.options" -> {
                exigirCargada()
                localizar(decodificarHandle(args[0])) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
                Puente.json.encodeToJsonElement(PokemonOptionsDto.serializer(), opcionesDePrueba)
            }

            "pokemon.update" -> {
                exigirCargada()
                val handle = decodificarHandle(args[0])
                val patch = Puente.json.decodeFromJsonElement(PokemonPatchDto.serializer(), args[1])
                actualizar(handle, patch)
                JsonNull
            }

            "trainer.get" -> {
                exigirCargada()
                Puente.json.encodeToJsonElement(
                    TrainerCardDto.serializer(),
                    TrainerCardDto(
                        id = estado.entrenador.idDeEntrenador,
                        name = estado.entrenador.nombre,
                        maxNameLength = estado.entrenador.maximoDeLetras,
                        money = estado.entrenador.dinero,
                    ),
                )
            }

            "trainer.setMoney" -> {
                exigirCargada()
                val monto = args[0].jsonPrimitive.long
                estado = estado.copy(entrenador = estado.entrenador.copy(dinero = monto))
                JsonNull
            }

            "trainer.setName" -> {
                exigirCargada()
                val nombre = args[0].jsonPrimitive.content
                estado = estado.copy(entrenador = estado.entrenador.copy(nombre = nombre))
                JsonNull
            }

            "trainer.badges" -> {
                exigirCargada()
                if (sinMedallas) {
                    JsonNull
                } else {
                    Puente.json.encodeToJsonElement(ListSerializer(BadgeDto.serializer()), estado.medallas.map { BadgeDto(it.nombre, it.ganada) })
                }
            }

            "trainer.setBadges" -> {
                exigirCargada()
                val valores = args[0].jsonArray.map { it.jsonPrimitive.boolean }
                if (valores.size != estado.medallas.size) throw MotorException("out-of-range", "cantidad de medallas distinta")
                estado = estado.copy(medallas = estado.medallas.mapIndexed { i, m -> m.copy(ganada = valores[i]) })
                JsonNull
            }

            "inventory.get" -> {
                exigirCargada()
                Puente.json.encodeToJsonElement(
                    ListSerializer(PouchDto.serializer()),
                    estado.mochila.map { bolsillo ->
                        PouchDto(
                            name = bolsillo.nombre,
                            items = bolsillo.objetos.map { OwnedItemDto(id = it.id, count = it.cantidad, maxCount = it.maximo) },
                            addable = bolsillo.agregables.map { AddableItemDto(it) },
                        )
                    },
                )
            }

            "inventory.setItem" -> {
                exigirCargada()
                val handle = Puente.json.decodeFromJsonElement(ItemHandleDto.serializer(), args[0])
                val cantidad = args[1].jsonPrimitive.int
                cambiarObjeto(handle.pouch, handle.itemId, cantidad)
                JsonNull
            }

            else -> throw MotorException("unknown-call", "MotorFalso no implementa $metodo")
        }
    }

    private fun decodificarHandle(elemento: JsonElement): PokemonHandleDto = Puente.json.decodeFromJsonElement(PokemonHandleDto.serializer(), elemento)

    private fun exigirCargada() {
        if (!cargada) throw MotorException("no-save", "no hay partida cargada")
    }

    private fun localizar(handle: PokemonHandleDto): PokemonDePrueba? = when (handle.source) {
        "party" -> estado.equipo.getOrNull(handle.slot)
        "box" -> estado.cajaPokemon.firstOrNull { it.caja == handle.box && it.posicion == handle.slot }?.pokemon
        else -> null
    }

    private fun actualizar(handle: PokemonHandleDto, patch: PokemonPatchDto) {
        val actual = localizar(handle) ?: throw MotorException("not-found", "no hay Pokémon en esa casilla")
        val nuevo = actual.conPatch(patch)
        estado = when (handle.source) {
            "party" -> estado.copy(equipo = estado.equipo.mapIndexed { i, p -> if (i == handle.slot) nuevo else p })
            "box" -> estado.copy(
                cajaPokemon = estado.cajaPokemon.map { if (it.caja == handle.box && it.posicion == handle.slot) it.copy(pokemon = nuevo) else it },
            )
            else -> throw MotorException("not-found", "origen de casilla desconocido")
        }
    }

    private fun cambiarObjeto(pouch: String, itemId: Int, cantidad: Int) {
        estado = estado.copy(
            mochila = estado.mochila.map { bolsillo ->
                when {
                    bolsillo.nombre != pouch -> bolsillo
                    cantidad == 0 -> bolsillo.copy(objetos = bolsillo.objetos.filterNot { it.id == itemId })
                    bolsillo.objetos.any { it.id == itemId } ->
                        bolsillo.copy(objetos = bolsillo.objetos.map { if (it.id == itemId) it.copy(cantidad = cantidad) else it })
                    else -> bolsillo.copy(objetos = bolsillo.objetos + ObjetoDePrueba(id = itemId, cantidad = cantidad, maximo = 99))
                }
            },
        )
    }
}

// --- El estado de la partida de mentiras: lo justo para las pruebas del editor ---

@Serializable
data class EstadisticasDePrueba(
    val ps: Int = 0,
    val ataque: Int = 0,
    val defensa: Int = 0,
    val ataqueEspecial: Int = 0,
    val defensaEspecial: Int = 0,
    val velocidad: Int = 0,
)

@Serializable
data class PokemonDePrueba(
    val especie: Int? = 1,
    val genero: String = "male",
    val naturaleza: Int = 0,
    val habilidad: Int = 0,
    val objeto: Int = 0,
    val pokeBall: Int = 4,
    val amistad: Int = 70,
    val idioma: Int = 7,
    val esShiny: Boolean = false,
    val esHuevo: Boolean = false,
    val mote: String = "",
    val nivel: Int = 5,
    val pid: Long = 0,
    val idDeEntrenador: Int = 12345,
    val idSecreto: Int = 1,
    val entrenadorOriginal: String = "ROJO",
    val versionDeOrigen: Int = 10,
    val lugarDeCaptura: Int = 0,
    val nivelDeCaptura: Int = 5,
    val ivs: EstadisticasDePrueba = EstadisticasDePrueba(),
    val evs: EstadisticasDePrueba = EstadisticasDePrueba(),
    val movimientos: List<Int> = listOf(0, 0, 0, 0),
    /** Para probar `revisarAntesDeGuardar`: si es `false`, `pokemon.details` lo da con `legality.valid = false`. */
    val legal: Boolean = true,
    val mensajesIlegales: List<String> = emptyList(),
)

@Serializable
data class CasillaDeCajaDePrueba(val caja: Int, val posicion: Int, val pokemon: PokemonDePrueba)

@Serializable
data class CajaDePruebaMeta(val numero: Int, val nombre: String, val casillas: Int)

@Serializable
data class ObjetoDePrueba(val id: Int, val cantidad: Int, val maximo: Int)

@Serializable
data class BolsilloDePrueba(val nombre: String, val objetos: List<ObjetoDePrueba> = emptyList(), val agregables: List<Int> = emptyList())

@Serializable
data class EntrenadorDePrueba(
    val nombre: String = "Rojo",
    val maximoDeLetras: Int = 7,
    val idDeEntrenador: String = "12345/00001",
    val dinero: Long? = 3000,
)

@Serializable
data class MedallaDePrueba(val nombre: String, val ganada: Boolean)

@Serializable
data class EstadoDePruebaDelMotor(
    val equipo: List<PokemonDePrueba> = emptyList(),
    val cajasMeta: List<CajaDePruebaMeta> = emptyList(),
    val cajaPokemon: List<CasillaDeCajaDePrueba> = emptyList(),
    val entrenador: EntrenadorDePrueba = EntrenadorDePrueba(),
    val medallas: List<MedallaDePrueba> = emptyList(),
    val mochila: List<BolsilloDePrueba> = emptyList(),
)

/** Una partida de mentiras razonable: dos en el equipo, dos en la caja 1 (una ilegal), medallas de Kanto y una Poción. */
fun partidaDePrueba(): EstadoDePruebaDelMotor = EstadoDePruebaDelMotor(
    equipo = listOf(
        PokemonDePrueba(especie = 1, mote = "BULBASAUR", nivel = 12, pid = 111L),
        PokemonDePrueba(especie = 64, mote = "Kadabra", nivel = 28, pid = 3200063177L, naturaleza = 3, habilidad = 22),
    ),
    cajasMeta = listOf(CajaDePruebaMeta(0, "CAJA 1", 30), CajaDePruebaMeta(1, "CAJA 2", 30)),
    cajaPokemon = listOf(
        CasillaDeCajaDePrueba(caja = 0, posicion = 0, pokemon = PokemonDePrueba(especie = 10, mote = "CATERPIE", nivel = 7, pid = 222L)),
        CasillaDeCajaDePrueba(
            caja = 0,
            posicion = 1,
            pokemon = PokemonDePrueba(
                especie = 25,
                mote = "PIKACHU",
                nivel = 99,
                pid = 333L,
                legal = false,
                mensajesIlegales = listOf("Nivel fuera de rango para su especie y origen."),
            ),
        ),
    ),
    entrenador = EntrenadorDePrueba(),
    medallas = listOf("Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth").map { MedallaDePrueba(it, ganada = false) },
    mochila = listOf(BolsilloDePrueba(nombre = "Items", objetos = listOf(ObjetoDePrueba(id = 13, cantidad = 3, maximo = 99)), agregables = listOf(19, 20))),
)

private val opcionesDePrueba = PokemonOptionsDto(
    species = listOf(ChoiceDto(63, "Abra"), ChoiceDto(64, "Kadabra"), ChoiceDto(65, "Alakazam")),
    abilities = listOf(ChoiceDto(22, "Synchronize"), ChoiceDto(32, "Inner Focus")),
    metLocations = listOf(ChoiceDto(25, "Route 5")),
    moves = listOf(ChoiceDto(93, "Psychic"), ChoiceDto(50, "Disable")),
    heldItems = listOf(ItemChoiceDto(0, "(none)"), ItemChoiceDto(19, "Antidote")),
    locked = listOf("nature"),
)

private fun EstadoDePruebaDelMotor.aBytes(): ByteArray = Puente.json.encodeToString(EstadoDePruebaDelMotor.serializer(), this).toByteArray(Charsets.UTF_8)

private fun ByteArray.aEstado(): EstadoDePruebaDelMotor = Puente.json.decodeFromString(EstadoDePruebaDelMotor.serializer(), toString(Charsets.UTF_8))

private fun EstadisticasDePrueba.aStatValuesDto(): StatValuesDto =
    StatValuesDto(health = ps, attack = ataque, defense = defensa, specialAttack = ataqueEspecial, specialDefense = defensaEspecial, speed = velocidad)

private fun PokemonDePrueba.aResumenJson(handle: PokemonHandleDto): PokemonSummaryDto =
    PokemonSummaryDto(at = handle, speciesId = especie, editable = true, nickname = mote, level = nivel, isShiny = esShiny, isEgg = esHuevo)

private fun PokemonDePrueba.aDetalleJson(): EditablePokemonDto = EditablePokemonDto(
    species = especie,
    form = 0,
    gender = genero,
    nature = naturaleza,
    ability = habilidad,
    heldItem = objeto,
    ball = pokeBall,
    friendship = amistad,
    language = idioma,
    isShiny = esShiny,
    isEgg = esHuevo,
    nickname = mote,
    level = nivel,
    pid = pid,
    trainerId = idDeEntrenador,
    secretId = idSecreto,
    originalTrainerName = entrenadorOriginal,
    version = versionDeOrigen,
    metLocation = lugarDeCaptura,
    metLevel = nivelDeCaptura,
    ivs = ivs.aStatValuesDto(),
    evs = evs.aStatValuesDto(),
    // MotorFalso no calcula estadísticas reales (no hace falta para probar el editor): usa los IVs tal cual.
    stats = ivs.aStatValuesDto(),
    moves = movimientos.map { MoveSlotDto(id = it, pp = if (it == 0) 0 else 10, maxPp = if (it == 0) 0 else 10) },
    legality = LegalityDto(valid = legal, messages = mensajesIlegales),
)

private fun PokemonDePrueba.conPatch(patch: PokemonPatchDto): PokemonDePrueba = copy(
    especie = patch.species ?: especie,
    naturaleza = patch.nature ?: naturaleza,
    habilidad = patch.ability ?: habilidad,
    objeto = patch.heldItem ?: objeto,
    pokeBall = patch.ball ?: pokeBall,
    amistad = patch.friendship ?: amistad,
    esShiny = patch.isShiny ?: esShiny,
    mote = patch.nickname ?: mote,
    nivel = patch.level ?: nivel,
    ivs = ivs.conPatch(patch.ivs),
    evs = evs.conPatch(patch.evs),
    movimientos = patch.moves ?: movimientos,
)

private fun EstadisticasDePrueba.conPatch(patch: StatPatchDto?): EstadisticasDePrueba {
    if (patch == null) return this
    return copy(
        ps = patch.health ?: ps,
        ataque = patch.attack ?: ataque,
        defensa = patch.defense ?: defensa,
        ataqueEspecial = patch.specialAttack ?: ataqueEspecial,
        defensaEspecial = patch.specialDefense ?: defensaEspecial,
        velocidad = patch.speed ?: velocidad,
    )
}
