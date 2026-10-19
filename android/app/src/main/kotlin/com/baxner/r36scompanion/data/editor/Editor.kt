package com.baxner.r36scompanion.data.editor

import kotlinx.serialization.Serializable

/*
 * Contrato del editor (fase 3). Lo escribe Opus; la implementación vive en este paquete y las
 * pantallas del editor solo usan estas interfaces. Ver docs/plan.md, "Diseño técnico de las
 * fases 3 a 5". Comprobado contra el motor con el Rojo Fuego real (2026-10-19): `pokemon.update`
 * sobre la casilla aplica al instante; con borrador (`pokemon.edit` → `update` sobre
 * `{source: "draft", slot: 0}` → `commit`) se aplica al hacer commit. Editar mote y nivel del
 * Kadabra lo dejó legal, cambió 22 bytes y la partida se recarga bien.
 *
 * Decisión: el motor tiene UNA partida cargada a la vez y la comparten los respaldos (que leen
 * resúmenes). Por eso la sesión de edición no deja la partida cargada: guarda los bytes actuales
 * en memoria y cada operación hace, con el turno de MotorPartidas, cargar → operar → exportar.
 * Cargar y exportar tardan milisegundos.
 *
 * Nombres: el motor da todo en inglés. Estos tipos llevan ids; las pantallas traducen con
 * `data/textos/Textos` (especies, movimientos, objetos de la 3.ª generación, habilidades,
 * naturalezas, lugares) y con tablas propias para medallas y bolsillos de la mochila.
 */

/** Dónde está un Pokémon en la partida. [caja] y [posicion] empiezan en 0. */
@Serializable
data class Casilla(val origen: OrigenDeCasilla, val posicion: Int, val caja: Int? = null)

@Serializable
enum class OrigenDeCasilla { EQUIPO, CAJA }

/** Un Pokémon con todo lo editable, como lo da `pokemon.details` (con su legalidad). */
@Serializable
data class PokemonEditable(
    val especie: Int?,
    val forma: Int = 0,
    val genero: String? = null,
    val naturaleza: Int,
    val habilidad: Int,
    /** Numeración de la 3.ª generación (`Textos.objetoGen3`). 0 = nada. */
    val objeto: Int,
    val pokeBall: Int,
    val amistad: Int,
    val idioma: Int,
    val esShiny: Boolean,
    val esHuevo: Boolean,
    val mote: String,
    val nivel: Int,
    val pid: Long,
    val idDeEntrenador: Int,
    val idSecreto: Int,
    val entrenadorOriginal: String,
    val versionDeOrigen: Int,
    val lugarDeCaptura: Int,
    val nivelDeCaptura: Int,
    val ivs: Estadisticas,
    val evs: Estadisticas,
    val estadisticas: Estadisticas,
    val movimientos: List<Movimiento>,
    val legalidad: Legalidad?,
)

@Serializable
data class Estadisticas(val ps: Int, val ataque: Int, val defensa: Int, val ataqueEspecial: Int, val defensaEspecial: Int, val velocidad: Int)

@Serializable
data class Movimiento(val id: Int, val pp: Int, val ppMaximos: Int)

/** Lo que dice la revisión de legalidad de PKHeX. Los mensajes vienen en inglés: la pantalla los resume. */
@Serializable
data class Legalidad(val valida: Boolean, val mensajes: List<String>)

/** Los cambios a aplicar a un Pokémon: null = no se toca (como `PokemonPatch`). */
@Serializable
data class CambioDePokemon(
    val especie: Int? = null,
    val naturaleza: Int? = null,
    val habilidad: Int? = null,
    val objeto: Int? = null,
    val pokeBall: Int? = null,
    val amistad: Int? = null,
    val esShiny: Boolean? = null,
    val mote: String? = null,
    val nivel: Int? = null,
    val ivs: CambioDeEstadisticas? = null,
    val evs: CambioDeEstadisticas? = null,
    /** Los cuatro movimientos por id (0 = vacío). */
    val movimientos: List<Int>? = null,
)

@Serializable
data class CambioDeEstadisticas(
    val ps: Int? = null,
    val ataque: Int? = null,
    val defensa: Int? = null,
    val ataqueEspecial: Int? = null,
    val defensaEspecial: Int? = null,
    val velocidad: Int? = null,
)

/** Lo que el juego permite elegir para ese Pokémon (`pokemon.options`), por id. */
data class OpcionesDePokemon(
    val especies: List<Int>,
    val habilidades: List<Int>,
    val movimientos: List<Int>,
    val objetos: List<Int>,
    val lugares: List<Int>,
    /** Campos que el juego no deja cambiar para este Pokémon: `gender`, `nature`, `ability`, `metLocation`. */
    val bloqueados: Set<String>,
)

data class ResumenDePokemon(val casilla: Casilla, val especie: Int?, val nivel: Int, val mote: String, val esShiny: Boolean, val esHuevo: Boolean, val editable: Boolean)

data class Caja(val numero: Int, val nombre: String, val casillas: Int, val pokemon: List<ResumenDePokemon>)

/** Un bolsillo de la mochila. [nombre] es el del motor (`Items`, `KeyItems`, `Balls`, `TMHMs`, `Berries`, `PCItems`). */
data class Bolsillo(val nombre: String, val objetos: List<ObjetoEnMochila>, val agregables: List<Int>)

/** [id] con la numeración de la 3.ª generación. */
data class ObjetoEnMochila(val id: Int, val cantidad: Int, val maximo: Int)

data class Medalla(val nombreEnIngles: String, val ganada: Boolean)

data class Entrenador(val nombre: String, val maximoDeLetras: Int, val idDeEntrenador: String, val dinero: Long?)

/**
 * Una partida abierta para editar, a partir de una versión respaldada. [base] es el SHA-256 de
 * esa versión: al guardar se le pasa a `EscritorDePartidas.escribir(…, base)`, que no escribe si
 * la partida de la consola ya no es esa.
 */
interface SesionDeEdicion {
    val base: String

    /** La partida con todos los cambios aplicados hasta ahora. */
    val bytesActuales: ByteArray

    val hayCambios: Boolean

    suspend fun equipo(): List<ResumenDePokemon>

    suspend fun cajas(): List<Caja>

    suspend fun detalle(casilla: Casilla): PokemonEditable

    suspend fun opciones(casilla: Casilla): OpcionesDePokemon

    /** Cómo quedaría el Pokémon con [cambio] (con su legalidad), sin aplicarlo. */
    suspend fun probar(casilla: Casilla, cambio: CambioDePokemon): PokemonEditable

    /** Aplica [cambio] y devuelve cómo quedó. */
    suspend fun aplicar(casilla: Casilla, cambio: CambioDePokemon): PokemonEditable

    suspend fun entrenador(): Entrenador

    suspend fun cambiarDinero(dinero: Long)

    suspend fun cambiarNombre(nombre: String)

    suspend fun medallas(): List<Medalla>

    suspend fun cambiarMedallas(ganadas: List<Boolean>)

    suspend fun mochila(): List<Bolsillo>

    /** Cantidad 0 = quitar el objeto. */
    suspend fun cambiarObjeto(bolsillo: String, id: Int, cantidad: Int)

    /** Vuelve a la partida de [base], sin ningún cambio. */
    fun deshacerTodo()

    /**
     * Antes de guardar: la partida editada se vuelve a cargar en el motor y se revisa que abra,
     * que el equipo y las cajas sigan ahí y la legalidad de cada Pokémon.
     */
    suspend fun revisarAntesDeGuardar(): RevisionDeGuardado
}

data class RevisionDeGuardado(
    val abre: Boolean,
    val pokemonIlegales: List<Pair<Casilla, Legalidad>>,
    val problemas: List<String>,
)

interface EditorDePartidas {
    /** Abre [bytes] (una versión respaldada) para editar. Falla con MotorException si el motor no la reconoce. */
    suspend fun abrir(bytes: ByteArray, nombreDeArchivo: String): SesionDeEdicion
}
