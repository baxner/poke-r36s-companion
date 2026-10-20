package com.baxner.r36scompanion.screenshot

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar
import com.baxner.r36scompanion.ui.jugar.ConfirmarSalirContent
import com.baxner.r36scompanion.ui.jugar.EtapaDeSeguirJugando
import com.baxner.r36scompanion.ui.jugar.SeguirJugandoScreenContent
import com.baxner.r36scompanion.ui.jugar.SeguirJugandoUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión visual de "seguir jugando en el teléfono" (ver el encargo de la fase 5, puntos 3 y 9):
 * avisos antes de jugar, jugando (la superficie del emulador sale negra en Robolectric, está
 * bien: no hay GPU de verdad), partida en el teléfono, y conflicto al regresar. Regenerar con
 * `./gradlew :app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class SeguirJugandoScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun capturar(nombre: String, uiState: SeguirJugandoUiState, altura: Dp = 900.dp) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura) {
                SeguirJugandoScreenContent(
                    uiState = uiState,
                    onContinuarApesarDeAvisos = {},
                    onCancelar = {},
                    onCancelarSalir = {},
                    onConfirmarSalir = {},
                    onDespues = {},
                    onRegresar = {},
                    onRegresarAunqueCambio = {},
                    onDescartarYQuedarseConLaDeLaConsola = {},
                    onReintentar = {},
                    onDescartarError = {},
                    onTenerSuperficie = { _: Surface?, _: Int -> },
                    onTeclas = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/jugar_$nombre.png")
    }

    @Test
    @Config(qualifiers = "+h900dp")
    fun avisosAntesDeJugar() = capturar(
        "avisos",
        SeguirJugandoUiState(
            etapa = EtapaDeSeguirJugando.AVISOS,
            nombreDeJuego = "Pokemon Edicion Verde Hoja",
            avisos = listOf(AvisoAntesDeJugar.AvanceSoloEnElEstado(estadoModificado = 2_000L, partidaModificada = 1_000L)),
        ),
        altura = 900.dp,
    )

    @Test
    @Config(qualifiers = "+h2000dp")
    fun jugando() = capturar(
        "jugando",
        SeguirJugandoUiState(
            etapa = EtapaDeSeguirJugando.JUGANDO,
            nombreDeJuego = "Pokemon Edicion Rojo Fuego",
            ultimoGuardadoEnElTelefono = 0L,
        ),
        altura = 2_000.dp,
    )

    @Test
    fun partidaEnElTelefonoConConexion() = capturar(
        "partida_en_telefono",
        SeguirJugandoUiState(etapa = EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO, nombreDeJuego = "Pokemon Edicion Rojo Fuego", puedeRegresar = true),
        altura = 500.dp,
    )

    @Test
    fun partidaEnElTelefonoSinConexion() = capturar(
        "partida_en_telefono_sin_conexion",
        SeguirJugandoUiState(etapa = EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO, nombreDeJuego = "Pokemon Edicion Rojo Fuego", puedeRegresar = false),
        altura = 500.dp,
    )

    @Test
    fun conflictoAlRegresar() = capturar(
        "conflicto",
        SeguirJugandoUiState(etapa = EtapaDeSeguirJugando.CONFLICTO, nombreDeJuego = "Pokemon Edicion Rojo Fuego"),
        altura = 700.dp,
    )

    @Test
    fun juegoAbiertoEnLaConsola() = capturar(
        "juego_abierto",
        SeguirJugandoUiState(etapa = EtapaDeSeguirJugando.JUEGO_ABIERTO, nombreDeJuego = "Pokemon Edicion Rojo Fuego"),
        altura = 500.dp,
    )

    // El `AlertDialog` de "¿Salir del juego?" no asienta en Robolectric: se captura el contenido
    // aparte (ver AGENTS.md).
    @Test
    fun dialogoConfirmarSalir() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 480.dp) {
                ComposeSurface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarSalirContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_confirmar_salir_de_jugar.png")
    }
}
