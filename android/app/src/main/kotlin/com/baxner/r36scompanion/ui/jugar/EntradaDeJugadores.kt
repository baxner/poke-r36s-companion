package com.baxner.r36scompanion.ui.jugar

import android.view.KeyEvent
import com.baxner.r36scompanion.emulador.TeclaGba
import kotlin.math.atan2
import kotlin.math.sqrt

/*
 * Entrada de los jugadores para el emulador (ver el encargo, punto 1): de la posición de un dedo
 * en la cruceta en pantalla, o de un `KeyEvent`/`MotionEvent` de un mando o del teclado, a
 * [TeclaGba]. Todo esto es puro (sin Android más que las constantes de [KeyEvent] y
 * [android.view.MotionEvent], que son solo enteros: no hace falta Robolectric para probarlo).
 */

/** Fracción del radio de la cruceta que cuenta como zona muerta (el centro, donde no se aprieta nada). */
private const val ZonaMuertaDeLaCruceta = 0.15f

/**
 * De la posición ([x], [y]) de un dedo dentro de una cruceta cuadrada de lado [lado] (mismas
 * unidades que [x]/[y], con el origen en la esquina superior izquierda) a las direcciones
 * apretadas: ocho sectores de 45° con diagonales, y una zona muerta redonda en el centro. Vacío si
 * [lado] no es positivo o si el dedo cae en la zona muerta.
 */
fun teclasDeCruceta(x: Float, y: Float, lado: Float): Set<TeclaGba> {
    if (lado <= 0f) return emptySet()
    val centro = lado / 2f
    val dx = x - centro
    val dy = y - centro
    val distancia = sqrt(dx * dx + dy * dy)
    if (distancia < centro * ZonaMuertaDeLaCruceta) return emptySet()

    // Ángulo matemático estándar (0° = derecha, antihorario); la pantalla crece hacia abajo, así
    // que se invierte `dy` para que "arriba" sea 90°.
    var angulo = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
    if (angulo < 0) angulo += 360.0

    return when {
        angulo >= 337.5 || angulo < 22.5 -> setOf(TeclaGba.DERECHA)
        angulo < 67.5 -> setOf(TeclaGba.ARRIBA, TeclaGba.DERECHA)
        angulo < 112.5 -> setOf(TeclaGba.ARRIBA)
        angulo < 157.5 -> setOf(TeclaGba.ARRIBA, TeclaGba.IZQUIERDA)
        angulo < 202.5 -> setOf(TeclaGba.IZQUIERDA)
        angulo < 247.5 -> setOf(TeclaGba.ABAJO, TeclaGba.IZQUIERDA)
        angulo < 292.5 -> setOf(TeclaGba.ABAJO)
        else -> setOf(TeclaGba.ABAJO, TeclaGba.DERECHA)
    }
}

/**
 * La [TeclaGba] de un botón de mando o del teclado, por su `keyCode` (ver el encargo, punto 1):
 * la cruceta y las flechas del teclado comparten las mismas constantes `KEYCODE_DPAD_*`. Null si
 * [keyCode] no es ninguno de los que la app reconoce.
 */
fun teclaDelMando(keyCode: Int): TeclaGba? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> TeclaGba.ARRIBA
    KeyEvent.KEYCODE_DPAD_DOWN -> TeclaGba.ABAJO
    KeyEvent.KEYCODE_DPAD_LEFT -> TeclaGba.IZQUIERDA
    KeyEvent.KEYCODE_DPAD_RIGHT -> TeclaGba.DERECHA
    KeyEvent.KEYCODE_BUTTON_A -> TeclaGba.A
    KeyEvent.KEYCODE_BUTTON_Y -> TeclaGba.A
    KeyEvent.KEYCODE_BUTTON_B -> TeclaGba.B
    KeyEvent.KEYCODE_BUTTON_X -> TeclaGba.B
    KeyEvent.KEYCODE_BUTTON_L1 -> TeclaGba.L
    KeyEvent.KEYCODE_BUTTON_R1 -> TeclaGba.R
    KeyEvent.KEYCODE_BUTTON_START -> TeclaGba.START
    KeyEvent.KEYCODE_BUTTON_SELECT -> TeclaGba.SELECT
    // Teclado: Z/X como B/A (como muchos emuladores), A/S como L/R, Enter/Retroceso como START/SELECT.
    KeyEvent.KEYCODE_Z -> TeclaGba.B
    KeyEvent.KEYCODE_X -> TeclaGba.A
    KeyEvent.KEYCODE_A -> TeclaGba.L
    KeyEvent.KEYCODE_S -> TeclaGba.R
    KeyEvent.KEYCODE_ENTER -> TeclaGba.START
    KeyEvent.KEYCODE_NUMPAD_ENTER -> TeclaGba.START
    KeyEvent.KEYCODE_DEL -> TeclaGba.SELECT
    else -> null
}

/**
 * Direcciones de un eje analógico: el stick (`MotionEvent.AXIS_X`/`AXIS_Y`, con [zonaMuerta] 0.5)
 * o la cruceta del mando (`AXIS_HAT_X`/`AXIS_HAT_Y`, que ya da -1/0/1: [zonaMuerta] 0). Puede dar
 * dos direcciones a la vez (diagonal).
 */
fun direccionesDelEje(x: Float, y: Float, zonaMuerta: Float): Set<TeclaGba> {
    val direcciones = mutableSetOf<TeclaGba>()
    if (x > zonaMuerta) direcciones += TeclaGba.DERECHA
    if (x < -zonaMuerta) direcciones += TeclaGba.IZQUIERDA
    if (y > zonaMuerta) direcciones += TeclaGba.ABAJO
    if (y < -zonaMuerta) direcciones += TeclaGba.ARRIBA
    return direcciones
}

/**
 * Junta la pantalla táctil y los mandos (físicos o teclado) en la máscara de teclas de cada
 * jugador (ver el encargo, punto 1). La táctil va solo al jugador que se controla ahora
 * ([controlarConTactil]); cada mando (por su `deviceId`) se asigna a un jugador la primera vez que
 * manda algo, en el orden en que llega: el primero a jugador 0, el segundo a jugador 1. Con un solo
 * jugador, todos los mandos van a él. No es Compose ni depende de ninguna vista: lo usan
 * [PantallaDelGba] (mandos) y [ControlesEnPantalla] (táctil), y se prueba con JUnit normal.
 */
class EntradaDeJugadores(private val numeroDeJugadores: Int) {
    private var jugadorTactil = 0
    private var mascaraTactil = 0

    private val ordenDeDispositivos = mutableListOf<Int>()
    private val botonesPorDispositivo = mutableMapOf<Int, Int>()
    private val ejePorDispositivo = mutableMapOf<Int, Int>()
    private val crucetaPorDispositivo = mutableMapOf<Int, Int>()

    /** A cuál jugador van los toques de [ControlesEnPantalla] (p. ej. al cambiar de pantalla controlada en el intercambio real). */
    fun controlarConTactil(jugador: Int) {
        jugadorTactil = jugador
    }

    /** La máscara actual de la pantalla táctil (ver [ControlesEnPantalla]). */
    fun tactil(mascara: Int) {
        mascaraTactil = mascara
    }

    /** Un botón de un mando (o del teclado) se apretó o soltó. */
    fun botonDeMando(deviceId: Int, tecla: TeclaGba, presionada: Boolean) {
        registrarDispositivo(deviceId)
        val actual = botonesPorDispositivo[deviceId] ?: 0
        botonesPorDispositivo[deviceId] = if (presionada) actual or tecla.mascara else actual and tecla.mascara.inv()
    }

    /** El stick analógico de un mando cambió (ver [direccionesDelEje]). */
    fun ejeDeMando(deviceId: Int, direcciones: Set<TeclaGba>) {
        registrarDispositivo(deviceId)
        ejePorDispositivo[deviceId] = TeclaGba.mascara(direcciones)
    }

    /** La cruceta física de un mando cambió (ver [direccionesDelEje]). */
    fun crucetaDeMando(deviceId: Int, direcciones: Set<TeclaGba>) {
        registrarDispositivo(deviceId)
        crucetaPorDispositivo[deviceId] = TeclaGba.mascara(direcciones)
    }

    private fun registrarDispositivo(deviceId: Int) {
        if (deviceId !in ordenDeDispositivos) ordenDeDispositivos += deviceId
    }

    private fun jugadorDeDispositivo(deviceId: Int): Int {
        if (numeroDeJugadores <= 1) return 0
        return ordenDeDispositivos.indexOf(deviceId).coerceIn(0, numeroDeJugadores - 1)
    }

    /** La máscara de teclas apretadas ahora por [jugador] (0 o 1): lo que se le manda a [com.baxner.r36scompanion.emulador.Sala.teclas]. */
    fun mascara(jugador: Int): Int {
        val deTactil = if (jugador == jugadorTactil) mascaraTactil else 0
        val deMandos = ordenDeDispositivos
            .filter { jugadorDeDispositivo(it) == jugador }
            .fold(0) { acumulado, id ->
                acumulado or (botonesPorDispositivo[id] ?: 0) or (ejePorDispositivo[id] ?: 0) or (crucetaPorDispositivo[id] ?: 0)
            }
        return deTactil or deMandos
    }
}
