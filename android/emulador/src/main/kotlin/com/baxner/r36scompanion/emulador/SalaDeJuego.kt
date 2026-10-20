package com.baxner.r36scompanion.emulador

import android.view.Surface

/**
 * La [Sala] de verdad: mGBA 0.10.5 (ver `src/main/cpp/emulador.c`). La partida se le da al juego
 * en memoria, del mismo tamaño que la del núcleo de RetroArch de la R36S (128 KB), y el ritmo lo
 * marca el sonido (o el reloj si no hay sonido).
 */
class SalaDeJuego private constructor(private var puntero: Long, override val jugadores: Int) : Sala {

    private var arrancada = false

    override fun arrancar() {
        val p = vivo()
        check(!arrancada) { "la sala ya arrancó" }
        if (!Nativo.arrancar(p)) throw ErrorAlAbrirSala("no arrancó el emulador")
        arrancada = true
    }

    override fun pausar() = Nativo.pausar(vivo(), true)

    override fun reanudar() = Nativo.pausar(vivo(), false)

    override fun teclas(jugador: Int, mascara: Int) = Nativo.teclas(vivo(), jugador, mascara)

    override fun pantalla(jugador: Int, superficie: Surface?, escala: Int) = Nativo.pantalla(vivo(), jugador, superficie, escala)

    override fun sonidoDe(jugador: Int?) = Nativo.sonido(vivo(), jugador ?: -1)

    override val conSonido: Boolean get() = Nativo.conSonido(vivo())

    override fun reabrirSonido(): Boolean = Nativo.reabrirSonido(vivo())

    override fun vecesQueGuardo(jugador: Int): Int = Nativo.versionDePartida(vivo(), jugador)

    override fun partidaGuardada(jugador: Int): ByteArray? = Nativo.partida(vivo(), jugador)

    override fun cuadros(jugador: Int): Long = Nativo.cuadros(vivo(), jugador)

    override fun diagnostico(): String = Nativo.diagnostico(vivo())

    override fun terminar(): List<ByteArray?> {
        val p = vivo()
        Nativo.cerrar(p)
        val partidas = (0 until jugadores).map { Nativo.partida(p, it) }
        Nativo.liberar(p)
        puntero = 0
        return partidas
    }

    override fun close() {
        if (puntero != 0L) terminar()
    }

    private fun vivo(): Long {
        check(puntero != 0L) { "la sala ya se cerró" }
        return puntero
    }

    companion object {
        /** Abre mGBA con uno o dos juegos; con [conCable], conectados por el cable link. */
        fun abrir(juegos: List<JuegoParaSala>, conCable: Boolean = juegos.size == 2): SalaDeJuego {
            require(juegos.size in 1..2) { "una sala tiene uno o dos juegos" }
            require(!conCable || juegos.size == 2) { "el cable necesita dos juegos" }
            val puntero = Nativo.crearSala(conCable)
            if (puntero == 0L) throw ErrorAlAbrirSala("sin memoria para el emulador")
            for (juego in juegos) {
                val resultado = Nativo.agregarJuego(puntero, juego.rom.absolutePath, juego.partida)
                if (resultado != 0) {
                    Nativo.liberar(puntero)
                    throw ErrorAlAbrirSala(
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

        /** El [AbridorDeSalas] de la app. */
        val abridor = AbridorDeSalas { juegos, conCable -> abrir(juegos, conCable) }
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
