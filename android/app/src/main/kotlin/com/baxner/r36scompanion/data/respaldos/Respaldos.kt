package com.baxner.r36scompanion.data.respaldos

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/*
 * Contrato de los respaldos (fase 2). Lo escribe Opus; la implementación vive en este paquete y
 * las pantallas (inicio, partida, historial) y las fases siguientes (editor, intercambios,
 * banco) solo usan estas interfaces. Reglas: docs/plan.md, "Reglas de seguridad para no perder
 * una partida". En la consola del usuario las partidas `.srm` y los estados `.state`,
 * `.state1`…, `.state.auto` están junto a las ROMs (`/roms/gba/`), y RetroArch carga el
 * `.state.auto` al abrir el juego: por eso escribir una partida obliga a apartarlo.
 */

/** Qué es un archivo de un juego. */
enum class TipoDeArchivo {
    /** `.srm`: la partida del juego (lo que se guarda desde el menú del juego). */
    PARTIDA,

    /** `.state`, `.state1`…: estados que el usuario guarda y carga a mano. */
    ESTADO,

    /** `.state.auto`: el que RetroArch guarda al salir y carga solo al abrir el juego. */
    ESTADO_AUTOMATICO,
}

/** De dónde salió una versión. */
enum class OrigenDeVersion {
    /** Un respaldo normal de lo que había en la consola. */
    CONSOLA,

    /** Lo que había en la consola justo antes de que la app escribiera ahí. */
    ANTES_DE_ESCRIBIR,

    /** Lo que la app escribió en la consola (edición, intercambio, restauración). */
    ESCRITA_POR_LA_APP,
}

enum class EstadoDePartida {
    /** Una partida con datos. */
    CON_DATOS,

    /** Todo el archivo en `0xFF`: nunca se guardó dentro del juego (el avance, si hay, vive en los estados). */
    VACIA,

    /** No aplica (estados) o no se pudo saber. */
    NO_APLICA,
}

/** Un juego de la consola, con la ruta de su partida (exista o no todavía). */
data class JuegoRespaldado(
    val id: Long,
    /** Carpeta del sistema en la consola: `gba`. */
    val sistema: String,
    /** Nombre de la ROM sin extensión, igual que el de sus partidas: `Pokemon Edicion Rojo Fuego`. */
    val nombre: String,
    val rutaDePartida: String,
    /** El motor reconoció su partida, o el nombre dice que es de Pokémon (hacks que el motor no lee). */
    val esPokemon: Boolean,
    /** El motor puede leerla y editarla. */
    val editable: Boolean,
    val ultimoRespaldo: Long?,
    /** Resumen de la última versión de su partida, si es de Pokémon y el motor la leyó. */
    val resumen: ResumenGuardado?,
    val estadoDePartida: EstadoDePartida,
)

data class VersionRespaldada(
    val id: Long,
    val juegoId: Long,
    val tipo: TipoDeArchivo,
    val rutaEnConsola: String,
    val sha256: String,
    val tamano: Long,
    /** La fecha de modificación que tenía en la consola (segundos UTC). */
    val modificadoEnConsola: Long,
    /** Cuándo la respaldó la app (milisegundos UTC, del [Reloj]). */
    val respaldadaEn: Long,
    val origen: OrigenDeVersion,
    val estadoDePartida: EstadoDePartida,
    val resumen: ResumenGuardado?,
)

/** Lo que el motor dijo de una partida de Pokémon al respaldarla: sirve para listas, historial y "qué cambió". */
@Serializable
data class ResumenGuardado(
    /** `FireRed`, `Emerald`… como lo da el motor. */
    val version: String,
    val entrenador: String,
    /** `TID/SID`. */
    val idDeEntrenador: String,
    val dinero: Long? = null,
    val horas: Int? = null,
    val minutos: Int? = null,
    val segundos: Int? = null,
    val medallas: Int? = null,
    val medallasTotales: Int? = null,
    val vistos: Int? = null,
    val atrapados: Int? = null,
    val equipo: List<PokemonCorto> = emptyList(),
    /** Formato de hack con el que se leyó, o null para la detección normal de PKHeX. */
    val formato: String? = null,
)

@Serializable
data class PokemonCorto(val especie: String, val especieId: Int?, val nivel: Int, val mote: String? = null, val shiny: Boolean = false, val huevo: Boolean = false)

/** Un cambio entre dos versiones de una partida, para mostrar "qué cambió". */
sealed interface Cambio {
    data class Medallas(val antes: Int, val despues: Int) : Cambio
    data class TiempoDeJuego(val minutosMas: Int) : Cambio
    data class Atrapados(val antes: Int, val despues: Int) : Cambio
    data class EntroAlEquipo(val pokemon: PokemonCorto) : Cambio
    data class SalioDelEquipo(val pokemon: PokemonCorto) : Cambio
    data class SubioDeNivel(val especie: String, val antes: Int, val despues: Int) : Cambio
    data class Evoluciono(val antes: String, val despues: String) : Cambio
    data class Dinero(val antes: Long, val despues: Long) : Cambio
}

data class ProgresoDeRespaldo(val hechos: Int, val total: Int, val archivoActual: String?)

data class ResultadoDeRespaldo(
    /** Versiones nuevas (archivos que cambiaron o aparecieron). */
    val nuevas: List<VersionRespaldada>,
    /** Archivos que ya estaban respaldados tal cual. */
    val sinCambios: Int,
    /** Archivos que no se pudieron respaldar, con el motivo. */
    val fallidos: List<Pair<String, String>>,
)

/** Hora de la app, inyectable para que las pruebas no dependan de la fecha real. */
fun interface Reloj {
    fun ahoraMs(): Long
}

/** Lee el resumen de una partida con el motor (en Android, MotorPartidas; en pruebas, uno falso). */
fun interface LectorDeResumen {
    /** null si el motor no la reconoce. */
    suspend fun resumir(bytes: ByteArray, nombreDeArchivo: String): ResumenGuardado?
}

interface RepositorioDeRespaldos {
    fun juegos(): Flow<List<JuegoRespaldado>>

    fun versiones(juegoId: Long): Flow<List<VersionRespaldada>>

    suspend fun bytesDe(versionId: Long): ByteArray

    /**
     * Respalda de la consola las partidas y estados de GBA que sean nuevos o hayan cambiado (por
     * contenido, no solo por fecha). Si nada cambió no crea versiones. Un archivo que falla no
     * detiene a los demás.
     */
    suspend fun respaldarTodo(sesion: SesionDeConsola, progreso: (ProgresoDeRespaldo) -> Unit = {}): ResultadoDeRespaldo

    /** Un .zip con la última versión de cada archivo de [juegos] (todos si es null), con las rutas de la consola. */
    suspend fun exportarZip(destino: OutputStream, juegos: List<Long>? = null)
}

/** Lo que pasó al escribir una partida en la consola. */
data class ResultadoDeEscritura(
    /** La versión con lo que quedó escrito ([OrigenDeVersion.ESCRITA_POR_LA_APP]). */
    val versionEscrita: VersionRespaldada,
    /** La copia de lo que había antes ([OrigenDeVersion.ANTES_DE_ESCRIBIR]); null si no había partida. */
    val copiaPrevia: VersionRespaldada?,
    /** Estados automáticos que se apartaron (rutas nuevas en la consola), para poder devolverlos. */
    val estadosApartados: List<String>,
    /** Estados manuales del juego que siguen ahí: si el usuario carga uno, pierde lo escrito. */
    val estadosManuales: List<String>,
)

sealed class ErrorDeEscritura(mensaje: String) : Exception(mensaje) {
    /** RetroArch tiene abierto ese juego: su partida en memoria pisaría lo escrito. */
    class JuegoAbierto(val rom: String) : ErrorDeEscritura("el juego está abierto en la consola: $rom")

    /** La partida de la consola ya no es la que se editó (se jugó mientras tanto). Nada se escribió. */
    class Conflicto(val esperado: String, val encontrado: String?) :
        ErrorDeEscritura("la partida de la consola cambió desde que se leyó")
}

/**
 * Escribe partidas en la consola con las reglas de seguridad: el juego no puede estar abierto;
 * si se da [base], la partida de la consola tiene que ser esa (si no, [ErrorDeEscritura.Conflicto]);
 * se respalda lo que había; se apartan (no se borran) los `.state.auto` del juego a
 * `/roms/<sistema>/.pr36c/apartados/<fecha>/`; se escribe con [SesionDeConsola.escribirSeguro];
 * y se registra lo escrito como versión nueva.
 */
interface EscritorDePartidas {
    suspend fun escribir(sesion: SesionDeConsola, juegoId: Long, bytes: ByteArray, base: String?): ResultadoDeEscritura

    /** Vuelve a poner en la consola una versión respaldada, con las mismas reglas. */
    suspend fun restaurar(sesion: SesionDeConsola, versionId: Long): ResultadoDeEscritura
}
