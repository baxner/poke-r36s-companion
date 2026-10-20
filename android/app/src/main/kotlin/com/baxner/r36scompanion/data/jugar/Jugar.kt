package com.baxner.r36scompanion.data.jugar

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.respaldos.Cambio
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import java.io.File
import kotlinx.coroutines.flow.Flow

/*
 * Contrato de la fase 5 (intercambio real) y de "seguir jugando en el teléfono". Lo escribe Opus.
 * El emulador es el módulo `:emulador` (mGBA 0.10.5 con el cable link, ver su `Sala`); aquí va lo
 * que lo rodea:
 *
 * - [RomsDelTelefono]: el emulador necesita la ROM del juego, que vive en la consola junto a la
 *   partida (`/roms/gba/<nombre>.gba` o `.zip`). Se copia una vez al teléfono.
 * - [Prestamos]: "seguir jugando". La partida se lleva al teléfono, se juega ahí (cada vez que el
 *   juego guarda se escribe a disco, por si Android cierra la app) y se regresa a la consola con
 *   `EscritorDePartidas`, cuya base es la versión de la que salió: si mientras tanto se jugó en la
 *   consola, es un conflicto y no se escribe nada sin preguntar.
 * - [IntercambiosReales]: dos partidas en dos GBA emulados con el cable; al terminar se escriben
 *   las dos con `EjecutorDeIntercambio` (si la segunda falla, la primera se regresa a como estaba).
 *
 * Nada de esto toca la consola salvo [RomsDelTelefono.traer] (solo lee) y los `regresar` /
 * `escribir` (con las reglas de seguridad de siempre). Una partida está, a lo más, en un préstamo
 * o en un intercambio real a la vez ([ErrorDeJuego.YaEstaEnElTelefono]).
 */

/** Una ROM de GBA ya copiada de la consola al teléfono. */
data class RomEnElTelefono(
    val juegoId: Long,
    val archivo: File,
    /** De dónde salió en la consola (`/roms/gba/<nombre>.gba` o `.zip`). */
    val rutaEnConsola: String,
    /** Tamaño y fecha (segundos UTC) que tenía en la consola: si cambian, se vuelve a copiar. */
    val tamanoEnConsola: Long,
    val modificadoEnConsola: Long,
    /** El código del juego en la cabecera (0xAC): `BPRE` Rojo Fuego, `BPGE` Verde Hoja, `BPEE` Esmeralda, `AXVE` Rubí, `AXPE` Zafiro. */
    val codigoDeJuego: String,
    /** El título de la cabecera (0xA0, 12 caracteres): `POKEMON FIRE`. */
    val tituloInterno: String,
    /** Lo que ocupa en el teléfono, en bytes. */
    val tamano: Long,
)

sealed class ErrorDeRom(mensaje: String) : Exception(mensaje) {
    /** No hay ROM junto a la partida: se buscó `<carpeta>/<nombre>.gba` y `.zip` (sin importar mayúsculas). */
    class NoEncontrada(val carpeta: String, val nombre: String) :
        ErrorDeRom("no está la ROM de $nombre en $carpeta")

    /** Está en un formato que la app no abre (`.7z`, `.rar`…): en la consola tiene que estar como `.gba` o `.zip`. */
    class FormatoNoSoportado(val ruta: String, val extension: String) :
        ErrorDeRom("la ROM está en .$extension: $ruta")

    /** El archivo no es una ROM de GBA (o el `.zip` no trae una). */
    class NoEsDeGba(val ruta: String) : ErrorDeRom("no es una ROM de Game Boy Advance: $ruta")

    /** No cabe en el teléfono. */
    class SinEspacio(val necesita: Long, val libre: Long) : ErrorDeRom("no hay espacio: hacen falta $necesita bytes")
}

interface RomsDelTelefono {
    /** Las ROMs que hay en el teléfono, para mostrarlas en Ajustes y poder borrarlas. */
    fun guardadas(): Flow<List<RomEnElTelefono>>

    /** La ROM del juego si ya está en el teléfono (sin revisar la consola). */
    suspend fun rom(juegoId: Long): RomEnElTelefono?

    /**
     * La copia de la consola al teléfono si no está o si en la consola cambió (otro tamaño u otra
     * fecha); si no, devuelve la que ya está sin volver a copiar. La ROM se busca junto a la
     * partida del juego: misma carpeta y mismo nombre que [JuegoRespaldado.rutaDePartida], `.gba`
     * o `.zip` con un `.gba` adentro, sin importar mayúsculas. Se escribe a un temporal y se revisa
     * la cabecera (byte 3 = 0xEA, como `GBAIsROM` de mGBA) antes de quedar como buena: nunca queda
     * una ROM a medias. Solo lee de la consola.
     */
    suspend fun traer(sesion: SesionDeConsola, juego: JuegoRespaldado): RomEnElTelefono

    suspend fun borrar(juegoId: Long)
}

/** Lo que conviene saber antes de llevarse una partida al teléfono. */
sealed interface AvisoAntesDeJugar {
    /**
     * El juego tiene un `.state.auto` de RetroArch (fechas de la consola, segundos UTC). Con
     * `savestate_auto_load` la consola retoma desde ahí, así que lo jugado después del último
     * guardado dentro del juego está en ese estado y **no** en la partida: el teléfono empieza
     * desde lo último que se guardó dentro del juego. No se puede saber por las fechas (RetroArch
     * escribe la partida y el estado juntos al cerrar el juego): se avisa siempre que el estado sea
     * de la misma sesión o más nuevo que la partida (hasta 5 minutos antes). Para traerlo todo: en
     * la consola, guardar dentro del juego y volver a respaldar.
     */
    data class AvanceSoloEnElEstado(val estadoModificado: Long, val partidaModificada: Long?) : AvisoAntesDeJugar

    /** La partida nunca se guardó dentro del juego (todo 0xFF) o no existe: el juego empieza de cero. */
    data object PartidaVacia : AvisoAntesDeJugar

    /** La partida no la reconoce el motor (un hack): se puede jugar, pero no se revisa ni se edita. */
    data object NoReconocida : AvisoAntesDeJugar
}

/** Una partida que está en el teléfono para seguir jugando. */
data class PartidaPrestada(
    val juegoId: Long,
    /**
     * SHA-256 de la partida de la consola de la que salió (la última versión respaldada de tipo
     * PARTIDA); null si el juego no tenía partida. Si al regresar la consola tiene otra cosa, es
     * un conflicto.
     */
    val base: String?,
    /** Cuándo se llevó (ms UTC, del `Reloj`). */
    val desde: Long,
    /** SHA-256 de la partida como va en el teléfono (igual a [base] si todavía no guarda). */
    val actual: String?,
    /** Cuántas veces guardó el juego en el teléfono desde que se llevó. */
    val vecesGuardada: Int,
    /** La última vez que guardó en el teléfono (ms UTC); null si no ha guardado. */
    val ultimaVezGuardada: Long?,
)

sealed class ErrorDeJuego(mensaje: String) : Exception(mensaje) {
    /** Esa partida ya está en el teléfono, en un préstamo o en un intercambio real sin terminar. */
    class YaEstaEnElTelefono(val juegoId: Long) : ErrorDeJuego("la partida ya está en el teléfono")

    /** Ya hay un intercambio real sin terminar: solo puede haber uno a la vez. */
    class HayOtroIntercambio(val id: Long) : ErrorDeJuego("hay otro intercambio real sin terminar")

    /** No hay préstamo ni intercambio con ese juego o ese id. */
    class NoEstaEnElTelefono(val id: Long) : ErrorDeJuego("no está en el teléfono")
}

interface Prestamos {
    fun prestadas(): Flow<List<PartidaPrestada>>

    suspend fun prestada(juegoId: Long): PartidaPrestada?

    /** Avisos sobre la última versión respaldada (ver [AvisoAntesDeJugar]); vacío si no hay nada que decir. */
    suspend fun avisosAntesDeLlevar(juegoId: Long): List<AvisoAntesDeJugar>

    /**
     * Empieza el préstamo con la última versión respaldada de la partida del juego (la más reciente
     * de tipo PARTIDA; vacía si no hay). Si ya estaba prestada, devuelve ese préstamo tal cual. Lanza
     * [ErrorDeJuego.YaEstaEnElTelefono] si la partida está en un intercambio real sin terminar.
     */
    suspend fun llevar(juegoId: Long): PartidaPrestada

    /** La partida como va en el teléfono (lo último que guardó el juego, o la que se llevó). */
    suspend fun partida(juegoId: Long): ByteArray

    /** Lo que el juego acaba de guardar en el teléfono. Se escribe a disco sin dejarlo a medias. */
    suspend fun guardar(juegoId: Long, bytes: ByteArray): PartidaPrestada

    /**
     * La regresa a la consola con `EscritorDePartidas` (base = [PartidaPrestada.base]: si se jugó
     * en la consola mientras tanto lanza `ErrorDeEscritura.Conflicto` y no escribe nada) y termina el
     * préstamo. Si en el teléfono no guardó nada, solo termina el préstamo y devuelve null.
     */
    suspend fun regresar(sesion: SesionDeConsola, juegoId: Long): ResultadoDeEscritura?

    /**
     * Como [regresar] sin revisar la base: para cuando hubo conflicto y el usuario eligió la del
     * teléfono. Lo que había en la consola queda respaldado antes de escribir (lo hace el escritor).
     */
    suspend fun regresarAunqueCambio(sesion: SesionDeConsola, juegoId: Long): ResultadoDeEscritura?

    /** Termina el préstamo sin escribir en la consola. La partida del teléfono se guarda aparte, no se borra. */
    suspend fun descartar(juegoId: Long)
}

enum class EstadoDeIntercambioReal {
    /** Los dos juegos están (o estaban, si la app se cerró) corriendo en el teléfono. */
    JUGANDO,

    /** Ya se dejó de jugar: falta escribir las dos partidas en la consola o descartarlas. */
    POR_ESCRIBIR,
}

/** Un intercambio real sin terminar. Al escribirlo o descartarlo deja de existir. */
data class IntercambioReal(
    val id: Long,
    val juegoA: Long,
    /** SHA-256 de la versión de la consola de la que salió cada partida; null si no tenía. */
    val baseA: String?,
    val juegoB: Long,
    val baseB: String?,
    val empezado: Long,
    val vecesGuardadaA: Int,
    val vecesGuardadaB: Int,
    val estado: EstadoDeIntercambioReal,
)

/** Qué cambió en cada partida desde que salió de la consola, para confirmar antes de escribir. */
data class CambiosDeIntercambioReal(
    val resumenA: ResumenGuardado?,
    val resumenB: ResumenGuardado?,
    /** `diferencias(antes, después)` de cada una (ver `data/respaldos/Diferencias.kt`). */
    val cambiosA: List<Cambio>,
    val cambiosB: List<Cambio>,
)

interface IntercambiosReales {
    /** El que quedó sin terminar (por ejemplo, la app se cerró a medias); null si no hay. */
    fun pendiente(): Flow<IntercambioReal?>

    /**
     * Empieza uno con la última versión respaldada de la partida de cada juego. Lanza
     * [ErrorDeJuego.HayOtroIntercambio] si ya hay uno, y [ErrorDeJuego.YaEstaEnElTelefono] si una
     * de las dos está prestada.
     */
    suspend fun empezar(juegoA: Long, juegoB: Long): IntercambioReal

    /** Las dos partidas como van en el teléfono (A, B). */
    suspend fun partidas(id: Long): Pair<ByteArray, ByteArray>

    /** Lo que el juego [jugador] (0 = A, 1 = B) acaba de guardar. Se escribe a disco sin dejarlo a medias. */
    suspend fun guardar(id: Long, jugador: Int, bytes: ByteArray): IntercambioReal

    /** Ya no se juega más: pasa a [EstadoDeIntercambioReal.POR_ESCRIBIR]. */
    suspend fun terminarDeJugar(id: Long): IntercambioReal

    /** Qué cambió en cada partida (con el motor; resumen null si el motor no la reconoce). */
    suspend fun cambios(id: Long): CambiosDeIntercambioReal

    /**
     * Escribe las dos partidas en la consola con `EjecutorDeIntercambio` (bases = las del
     * intercambio) y lo termina. Si ninguna guardó, no escribe nada y solo lo termina. Si solo una
     * guardó, escribe solo esa con `EscritorDePartidas` (el otro juego no cambió).
     */
    suspend fun escribir(sesion: SesionDeConsola, id: Long)

    /** Lo termina sin escribir en la consola. Las partidas del teléfono se guardan aparte, no se borran. */
    suspend fun descartar(id: Long)
}
