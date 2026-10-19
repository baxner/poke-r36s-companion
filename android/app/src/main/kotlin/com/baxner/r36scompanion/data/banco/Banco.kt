package com.baxner.r36scompanion.data.banco

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import kotlinx.coroutines.flow.Flow

/*
 * Contrato del banco de Pokémon (fase 4). Lo escribe Opus. Ver docs/plan.md, "Diseño técnico de
 * las fases 3 a 5" > Banco: sacar un Pokémon = `pokemon.export(at)` (sus 80 bytes de caja tal
 * cual) + vaciar la casilla con `PartidaGen3.conCasillaVacia`, comprobado con el Rojo Fuego real
 * (solo falta ese Pokémon; todo lo demás idéntico); meterlo = `box.previewFile` (legalidad en el
 * juego de destino) + `box.addFromFile`.
 *
 * La regla de oro: **un Pokémon nunca se pierde ni se duplica**, aunque la app se cierre o la
 * consola se desconecte a la mitad. Mover entre la consola y el banco son dos escrituras en dos
 * lugares, así que va en dos fases con un estado intermedio guardado:
 *
 * - Depositar: [prepararDeposito] guarda el Pokémon en el banco como PENDIENTE_DE_DEPOSITO y
 *   devuelve la partida sin él → la app la escribe con `EscritorDePartidas` → [confirmar]. Si la
 *   escritura falla → [cancelar] (el Pokémon sigue en la partida y sale del banco).
 * - Retirar: [prepararRetiro] marca el Pokémon como PENDIENTE_DE_RETIRO y devuelve la partida con
 *   él → se escribe → [confirmar] (sale del banco). Si falla → [cancelar] (vuelve a estar EN_BANCO).
 * - [pendientes] + [conciliar]: si la app se cerró en medio, al volver a ver la consola se mira
 *   la partida real y se decide por lo que dice ella (¿está o no el Pokémon en esa casilla?).
 *
 * En la v1 solo se deposita desde las cajas (no desde el equipo), y solo partidas que el motor
 * reconoce (no hacks).
 */

enum class EstadoEnBanco { EN_BANCO, PENDIENTE_DE_DEPOSITO, PENDIENTE_DE_RETIRO }

data class PokemonEnBanco(
    val id: Long,
    /** Los 80 bytes de caja del juego (`.pk3`), exactos: así conserva su legalidad. */
    val bytes: ByteArray,
    val especie: Int?,
    val nivel: Int,
    val mote: String,
    val esShiny: Boolean,
    val esHuevo: Boolean,
    val entrenadorOriginal: String,
    val idDeEntrenador: Int,
    /** `GameVersion` de PKHeX del juego donde se atrapó. */
    val versionDeOrigen: Int,
    val legalidad: Legalidad?,
    val estado: EstadoEnBanco,
    /** De qué juego y casilla salió (para mostrarlo y para [conciliar]). */
    val juegoDeOrigen: Long?,
    val casillaDeOrigen: Casilla?,
    val depositadoEn: Long,
    /** Para ordenar el banco en cajas, como el PC del juego. */
    val caja: Int,
    val posicion: Int,
)

/** Lo que hay que escribir en la consola para que un movimiento quede hecho. */
data class MovimientoPreparado(
    val pokemonId: Long,
    val juegoId: Long,
    /** La partida como debe quedar en la consola. */
    val partidaNueva: ByteArray,
    /** SHA-256 de la partida de la que se partió: es la `base` de `EscritorDePartidas.escribir`. */
    val base: String,
    /** Dónde queda (al retirar) o de dónde salió (al depositar) en la partida. */
    val casilla: Casilla,
    /** La revisión de legalidad del Pokémon en el juego de destino (al retirar), o null. */
    val legalidadEnDestino: Legalidad?,
    /** Lo que el juego cambia al recibirlo (`box.previewFile`), en inglés del motor: la pantalla lo resume. */
    val cambios: List<String>,
)

interface Banco {
    fun pokemon(): Flow<List<PokemonEnBanco>>

    /**
     * Saca de [partida] (bytes de la versión respaldada que se ve en pantalla, del juego [juegoId])
     * el Pokémon de [casilla], que tiene que ser de una caja. Lo guarda como PENDIENTE_DE_DEPOSITO
     * y devuelve la partida sin él, ya comprobada con el motor (solo falta ese Pokémon).
     */
    suspend fun prepararDeposito(juegoId: Long, partida: ByteArray, nombreDeArchivo: String, casilla: Casilla): MovimientoPreparado

    /**
     * Mete [pokemonId] en la primera casilla libre de las cajas de [partida]. Falla si no hay
     * espacio o si el juego no puede tener ese Pokémon. Lo marca PENDIENTE_DE_RETIRO.
     */
    suspend fun prepararRetiro(pokemonId: Long, juegoId: Long, partida: ByteArray, nombreDeArchivo: String): MovimientoPreparado

    /** La partida se escribió en la consola: el movimiento queda firme. */
    suspend fun confirmar(movimiento: MovimientoPreparado)

    /** La escritura falló: todo vuelve a como estaba antes de preparar. */
    suspend fun cancelar(movimiento: MovimientoPreparado)

    suspend fun pendientes(): List<PokemonEnBanco>

    /**
     * Decide un pendiente que quedó a medias mirando la partida real de la consola
     * ([partidaEnConsola], la de su juego de origen o destino): si el movimiento se ve hecho en
     * ella, se confirma; si no, se cancela.
     */
    suspend fun conciliar(pokemonId: Long, partidaEnConsola: ByteArray, nombreDeArchivo: String)

    suspend fun mover(pokemonId: Long, caja: Int, posicion: Int)
}
