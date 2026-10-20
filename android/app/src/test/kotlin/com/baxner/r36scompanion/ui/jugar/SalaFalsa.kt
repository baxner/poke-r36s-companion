package com.baxner.r36scompanion.ui.jugar

import android.view.Surface
import com.baxner.r36scompanion.emulador.Sala

/**
 * [Sala] en memoria para las pruebas de "seguir jugando" y el intercambio real (ver AGENTS.md:
 * nunca contra el emulador de verdad). [simularGuardado] es lo que usan las pruebas para avisar
 * que el juego [jugador] "terminó de guardar" (lo que [SeguirJugandoViewModel]/
 * [IntercambioRealViewModel] sondean cada 500 ms con [vecesQueGuardo]/[partidaGuardada]).
 */
class SalaFalsa(override val jugadores: Int = 1) : Sala {
    var arrancada = false
        private set
    var pausada = false
        private set
    var terminada = false
        private set

    private val vecesGuardadas = IntArray(jugadores)
    private val ultimasPartidas = arrayOfNulls<ByteArray>(jugadores)
    private val mascarasDeTeclas = IntArray(jugadores)

    /** A cuál jugador se le pidió el sonido por última vez (null = silencio). */
    var sonidoDeJugador: Int? = 0
        private set

    override var conSonido: Boolean = true

    /** Si [reabrirSonido] debe "fallar" (devolver false) la próxima vez. */
    var fallaAlReabrirSonido = false

    override fun arrancar() {
        arrancada = true
    }

    override fun pausar() {
        pausada = true
    }

    override fun reanudar() {
        pausada = false
    }

    override fun teclas(jugador: Int, mascara: Int) {
        mascarasDeTeclas[jugador] = mascara
    }

    fun mascaraDe(jugador: Int): Int = mascarasDeTeclas[jugador]

    override fun pantalla(jugador: Int, superficie: Surface?, escala: Int) {
        // No hay pantalla de verdad en las pruebas: nada que hacer.
    }

    override fun sonidoDe(jugador: Int?) {
        sonidoDeJugador = jugador
    }

    override fun reabrirSonido(): Boolean {
        if (fallaAlReabrirSonido) return false
        conSonido = true
        return true
    }

    override fun vecesQueGuardo(jugador: Int): Int = vecesGuardadas[jugador]

    override fun partidaGuardada(jugador: Int): ByteArray? = ultimasPartidas[jugador]

    override fun cuadros(jugador: Int): Long = 0L

    override fun diagnostico(): String = "SalaFalsa: jugadores=$jugadores"

    override fun terminar(): List<ByteArray?> {
        terminada = true
        return ultimasPartidas.toList()
    }

    override fun close() {
        terminada = true
    }

    /** El juego [jugador] "terminó de guardar" [bytes]: el próximo sondeo lo va a notar. */
    fun simularGuardado(jugador: Int, bytes: ByteArray) {
        vecesGuardadas[jugador]++
        ultimasPartidas[jugador] = bytes
    }
}
