package com.baxner.r36scompanion.emulador

import android.view.Surface
import java.io.File

/**
 * Uno o dos Game Boy Advance emulados: lo que la app necesita del emulador. En la app es
 * [SalaDeJuego] (mGBA de verdad); en las pruebas de pantallas, uno falso.
 *
 * - **Un juego**: "seguir jugando en el teléfono".
 * - **Dos con cable**: el intercambio real. Los dos juegos quedan conectados con el cable link,
 *   como dos consolas con su cable: en los dos hay que ir al Centro Pokémon, al Club de Cable, y
 *   cambiar como en el juego. El jugador 1 (índice 0) es el primero de la lista.
 *
 * [partidaGuardada] devuelve **solo lo que el juego terminó de guardar** (nunca una a medio
 * escribir), del mismo tamaño que la partida que se le dio: es lo que se regresa a la consola.
 *
 * [teclas] y las lecturas se pueden llamar desde cualquier hilo; arrancar, pausar, poner pantalla
 * y terminar, desde uno solo (el principal o el del ViewModel).
 */
interface Sala : AutoCloseable {
    val jugadores: Int

    /** Empieza a correr los juegos. */
    fun arrancar()

    fun pausar()

    fun reanudar()

    /** Las teclas apretadas ahora en el juego [jugador] (0 o 1), como máscara de [TeclaGba]. */
    fun teclas(jugador: Int, mascara: Int)

    /**
     * Dónde dibujar el juego [jugador]: la pantalla del GBA (240 × 160) ampliada [escala] veces sin
     * suavizar. Para que se vea nítida, la superficie debe medir justo 240·escala × 160·escala
     * píxeles. null deja de dibujar: llámalo en `surfaceDestroyed` antes de regresar.
     */
    fun pantalla(jugador: Int, superficie: Surface?, escala: Int = 1)

    /** De cuál juego sale el sonido; null para silencio. */
    fun sonidoDe(jugador: Int?)

    /** Si hay salida de sonido. Si se cortó (audífonos, Bluetooth), [reabrirSonido] la vuelve a abrir. */
    val conSonido: Boolean

    /** Vuelve a abrir el sonido si se cortó; true si quedó con sonido. Barato si no se cortó. */
    fun reabrirSonido(): Boolean

    /** Cuántas veces ha guardado el juego [jugador] desde que arrancó (0: no ha guardado). */
    fun vecesQueGuardo(jugador: Int): Int

    /** La última partida que el juego [jugador] terminó de guardar; null si no ha guardado. */
    fun partidaGuardada(jugador: Int): ByteArray?

    /** Cuadros que lleva el juego [jugador] (60 por segundo): para saber que no se trabó. */
    fun cuadros(jugador: Int): Long

    /** Estado del cable y de los registros del puerto serie, para diagnosticar. */
    fun diagnostico(): String

    /**
     * Detiene los juegos y devuelve, de cada uno, la última partida que terminó de guardar (null si
     * no guardó). Después de esto la sala ya no sirve.
     */
    fun terminar(): List<ByteArray?>

    /** Como [terminar], sin las partidas. No hace nada si ya terminó. */
    override fun close()
}

/** Una ROM de GBA (archivo en el teléfono) y su partida (vacía si es nueva). */
class JuegoParaSala(val rom: File, val partida: ByteArray)

/** Abre salas: en la app, mGBA ([SalaDeJuego.abrir]); en las pruebas, salas falsas. */
fun interface AbridorDeSalas {
    /** Lanza [ErrorAlAbrirSala] si una ROM no se puede abrir. */
    fun abrir(juegos: List<JuegoParaSala>, conCable: Boolean): Sala
}

class ErrorAlAbrirSala(mensaje: String) : Exception(mensaje)

/** Las diez teclas del GBA, con su bit en el registro KEYINPUT. */
enum class TeclaGba(val bit: Int) {
    A(0), B(1), SELECT(2), START(3), DERECHA(4), IZQUIERDA(5), ARRIBA(6), ABAJO(7), R(8), L(9);

    val mascara: Int get() = 1 shl bit

    companion object {
        fun mascara(teclas: Collection<TeclaGba>): Int = teclas.fold(0) { m, t -> m or t.mascara }
    }
}
