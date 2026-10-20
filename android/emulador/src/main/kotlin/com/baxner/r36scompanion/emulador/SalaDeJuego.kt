package com.baxner.r36scompanion.emulador

import android.view.Surface
import java.io.File

/**
 * Uno o dos Game Boy Advance emulados con mGBA 0.10.5 (ver `src/main/cpp/emulador.c`).
 *
 * - **Uno**: "seguir jugando en el teléfono".
 * - **Dos con cable**: el intercambio real. Los dos juegos quedan conectados con el cable link
 *   de mGBA, como dos consolas con su cable: en los dos hay que ir al Centro Pokémon, al Club de
 *   Cable, y cambiar como en el juego. El jugador 1 es el primero de la lista.
 *
 * La partida se le da al juego en memoria, del mismo tamaño que la del núcleo de RetroArch de la
 * R36S, y [partidaGuardada] devuelve **solo lo que el juego terminó de guardar** (nunca una a
 * medio escribir): es lo que se regresa a la consola, con las reglas de `EscritorDePartidas`.
 *
 * No es segura entre hilos por sí sola salvo [teclas] y las lecturas: abrir, arrancar, pausar,
 * poner pantalla y cerrar van desde un solo hilo (el principal o el del ViewModel).
 */
class SalaDeJuego private constructor(private var puntero: Long, val jugadores: Int) : AutoCloseable {

    /** Una ROM de GBA (archivo en el teléfono) y su partida (vacía si es nueva). */
    class Juego(val rom: File, val partida: ByteArray)

    /** Las diez teclas del GBA, con su bit en el registro KEYINPUT. */
    enum class Tecla(val bit: Int) {
        A(0), B(1), SELECT(2), START(3), DERECHA(4), IZQUIERDA(5), ARRIBA(6), ABAJO(7), R(8), L(9);

        val mascara: Int get() = 1 shl bit
    }

    class ErrorAlAbrir(mensaje: String) : Exception(mensaje)

    private var arrancada = false

    /** Empieza a correr los juegos. */
    fun arrancar() {
        val p = vivo()
        check(!arrancada) { "la sala ya arrancó" }
        if (!Nativo.arrancar(p)) throw ErrorAlAbrir("no arrancó el emulador")
        arrancada = true
    }

    fun pausar() = Nativo.pausar(vivo(), true)

    fun reanudar() = Nativo.pausar(vivo(), false)

    /** Las teclas apretadas ahora en el juego [jugador] (0 o 1), como máscara de [Tecla]. */
    fun teclas(jugador: Int, mascara: Int) = Nativo.teclas(vivo(), jugador, mascara)

    /**
     * Dónde dibujar el juego [jugador]: la pantalla del GBA (240 × 160) ampliada [escala] veces sin
     * suavizar. null deja de dibujar (llámalo en `surfaceDestroyed` antes de regresar).
     */
    fun pantalla(jugador: Int, superficie: Surface?, escala: Int = 1) = Nativo.pantalla(vivo(), jugador, superficie, escala)

    /** De cuál juego sale el sonido; null para silencio. */
    fun sonidoDe(jugador: Int?) = Nativo.sonido(vivo(), jugador ?: -1)

    /** Si hay salida de sonido. Si se cortó (audífonos, Bluetooth), [reabrirSonido] la vuelve a abrir. */
    val conSonido: Boolean get() = Nativo.conSonido(vivo())

    /** Vuelve a abrir el sonido si se cortó; true si quedó con sonido. Barato si no se cortó. */
    fun reabrirSonido(): Boolean = Nativo.reabrirSonido(vivo())

    /** Cuántas veces ha guardado el juego [jugador] desde que arrancó (0: no ha guardado). */
    fun vecesQueGuardo(jugador: Int): Int = Nativo.versionDePartida(vivo(), jugador)

    /** La última partida que el juego [jugador] terminó de guardar; null si no ha guardado. */
    fun partidaGuardada(jugador: Int): ByteArray? = Nativo.partida(vivo(), jugador)

    /** Cuadros que lleva el juego [jugador] (para pruebas y para saber que no se trabó). */
    fun cuadros(jugador: Int): Long = Nativo.cuadros(vivo(), jugador)

    /** Estado del cable y de los registros del puerto serie, para diagnosticar. */
    fun diagnostico(): String = Nativo.diagnostico(vivo())

    /**
     * Detiene los juegos y devuelve, de cada uno, la última partida que terminó de guardar (null si
     * no guardó). Después de esto la sala ya no sirve.
     */
    fun terminar(): List<ByteArray?> {
        val p = vivo()
        Nativo.cerrar(p)
        val partidas = (0 until jugadores).map { Nativo.partida(p, it) }
        Nativo.liberar(p)
        puntero = 0
        return partidas
    }

    /** Como [terminar], sin las partidas. */
    override fun close() {
        if (puntero != 0L) terminar()
    }

    private fun vivo(): Long {
        check(puntero != 0L) { "la sala ya se cerró" }
        return puntero
    }

    companion object {
        /** Una sala para un juego (seguir jugando) o para dos (con [conCable], el intercambio real). */
        fun abrir(juegos: List<Juego>, conCable: Boolean = juegos.size == 2): SalaDeJuego {
            require(juegos.size in 1..2) { "una sala tiene uno o dos juegos" }
            require(!conCable || juegos.size == 2) { "el cable necesita dos juegos" }
            val puntero = Nativo.crearSala(conCable)
            if (puntero == 0L) throw ErrorAlAbrir("sin memoria para el emulador")
            for (juego in juegos) {
                val resultado = Nativo.agregarJuego(puntero, juego.rom.absolutePath, juego.partida)
                if (resultado != 0) {
                    Nativo.liberar(puntero)
                    throw ErrorAlAbrir(
                        when (resultado) {
                            -3 -> "no se pudo leer la ROM ${juego.rom.name}"
                            -4 -> "${juego.rom.name} no es una ROM de Game Boy Advance"
                            -2 -> "sin memoria para el emulador"
                            else -> "no se pudo cargar ${juego.rom.name} ($resultado)"
                        },
                    )
                }
            }
            return SalaDeJuego(puntero, juegos.size)
        }
    }
}

/** Las funciones de `emulador.c`. */
internal object Nativo {
    init {
        System.loadLibrary("emulador")
    }

    @JvmStatic external fun crearSala(conCable: Boolean): Long
    @JvmStatic external fun agregarJuego(sala: Long, rom: String, partida: ByteArray?): Int
    @JvmStatic external fun arrancar(sala: Long): Boolean
    @JvmStatic external fun pausar(sala: Long, pausa: Boolean)
    @JvmStatic external fun teclas(sala: Long, jugador: Int, mascara: Int)
    @JvmStatic external fun pantalla(sala: Long, jugador: Int, superficie: Surface?, escala: Int)
    @JvmStatic external fun sonido(sala: Long, jugador: Int)
    @JvmStatic external fun reabrirSonido(sala: Long): Boolean
    @JvmStatic external fun conSonido(sala: Long): Boolean
    @JvmStatic external fun versionDePartida(sala: Long, jugador: Int): Int
    @JvmStatic external fun partida(sala: Long, jugador: Int): ByteArray?
    @JvmStatic external fun cuadros(sala: Long, jugador: Int): Long
    @JvmStatic external fun diagnostico(sala: Long): String
    @JvmStatic external fun cerrar(sala: Long)
    @JvmStatic external fun liberar(sala: Long)
}
