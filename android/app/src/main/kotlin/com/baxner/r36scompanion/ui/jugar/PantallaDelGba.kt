package com.baxner.r36scompanion.ui.jugar

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView

/**
 * La pantalla de un jugador del emulador (ver el encargo, punto 2): un [SurfaceView] de
 * exactamente `240·e × 160·e` píxeles (240×160 es el tamaño real del GBA; `e` es el mayor entero
 * que quepa en el espacio que [modifier] le dé, mínimo 1), centrado sobre negro. Se mide con
 * [Modifier.layout] en píxeles (nunca `dp`) para que no haya redondeos: una fracción de píxel de
 * diferencia se ve como una franja borrosa.
 *
 * [alTenerSuperficie] se llama con la superficie y la escala en `surfaceCreated`/`surfaceChanged`,
 * y con `null` en `surfaceDestroyed` (antes de que la función regrese: ver
 * [com.baxner.r36scompanion.emulador.Sala.pantalla]).
 *
 * Si se da [entrada], la vista pide el foco y manda a [EntradaDeJugadores] los botones y ejes de
 * los mandos físicos y del teclado que lleguen mientras tenga el foco (ver el encargo, punto 2,
 * "Mandos físicos"); la pantalla en Robolectric no tiene mandos de verdad, así que esto no se ve
 * en las capturas, pero sí compila y corre.
 */
@Composable
fun PantallaDelGba(
    jugador: Int,
    alTenerSuperficie: (Surface?, Int) -> Unit,
    modifier: Modifier = Modifier,
    entrada: EntradaDeJugadores? = null,
    alCambiarEntradaDeMando: () -> Unit = {},
) {
    AndroidView(
        modifier = modifier
            .background(Color.Black)
            .layout { medible, restricciones ->
                val anchoMaximo = restricciones.maxWidth
                val altoMaximo = restricciones.maxHeight
                val escala = maxOf(1, minOf(anchoMaximo / AnchoDelGba, altoMaximo / AltoDelGba))
                val ancho = AnchoDelGba * escala
                val alto = AltoDelGba * escala
                val colocable = medible.measure(Constraints.fixed(ancho, alto))
                layout(anchoMaximo, altoMaximo) {
                    colocable.place((anchoMaximo - ancho) / 2, (altoMaximo - alto) / 2)
                }
            },
        factory = { context -> SuperficieDeJuego(context) },
        update = { vista ->
            vista.jugador = jugador
            vista.entradaDeJugadores = entrada
            vista.alTenerSuperficie = alTenerSuperficie
            vista.alCambiarEntradaDeMando = alCambiarEntradaDeMando
        },
        onRelease = { vista ->
            vista.alTenerSuperficie = { _, _ -> }
            vista.alCambiarEntradaDeMando = {}
        },
    )
}

private const val AnchoDelGba = 240
private const val AltoDelGba = 160

/**
 * El [SurfaceView] de verdad detrás de [PantallaDelGba]: aparte de avisar cuándo tiene superficie,
 * si tiene foco manda los botones y ejes de mandos/teclado a [entradaDeJugadores] (ver el encargo,
 * punto 2). `jugador` solo se guarda para que quien lea el código recuerde de cuál jugador es esta
 * vista; la máscara en sí la junta [EntradaDeJugadores] con el `deviceId` de cada mando.
 */
private class SuperficieDeJuego(context: Context) : SurfaceView(context) {
    var jugador: Int = 0
    var entradaDeJugadores: EntradaDeJugadores? = null
    var alTenerSuperficie: (Surface?, Int) -> Unit = { _, _ -> }
    var alCambiarEntradaDeMando: () -> Unit = {}
    private var escalaActual = 1

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    escalaActual = (width / AnchoDelGba).coerceAtLeast(1)
                    alTenerSuperficie(holder.surface, escalaActual)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, anchoPx: Int, altoPx: Int) {
                    escalaActual = (anchoPx / AnchoDelGba).coerceAtLeast(1)
                    alTenerSuperficie(holder.surface, escalaActual)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    alTenerSuperficie(null, escalaActual)
                }
            },
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = manejarTecla(event, presionada = true) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = manejarTecla(event, presionada = false) || super.onKeyUp(keyCode, event)

    private fun manejarTecla(event: KeyEvent, presionada: Boolean): Boolean {
        val tecla = teclaDelMando(event.keyCode) ?: return false
        entradaDeJugadores?.botonDeMando(event.deviceId, tecla, presionada)
        alCambiarEntradaDeMando()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val esMandoAnalogico = event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!esMandoAnalogico) return super.onGenericMotionEvent(event)
        val entrada = entradaDeJugadores ?: return true
        entrada.ejeDeMando(event.deviceId, direccionesDelEje(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), zonaMuerta = 0.5f))
        entrada.crucetaDeMando(event.deviceId, direccionesDelEje(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y), zonaMuerta = 0f))
        alCambiarEntradaDeMando()
        return true
    }
}
