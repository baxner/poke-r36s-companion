package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.juego.ConfirmarRestaurarContent
import com.baxner.r36scompanion.ui.juego.JuegoScreenContent
import com.baxner.r36scompanion.ui.juego.JuegoUiState
import com.baxner.r36scompanion.ui.juego.historialDePartida
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión visual del detalle de un juego (ver el encargo, punto 4 y 9): Rojo Fuego completo (con
 * historial y estados), Verde Hoja vacía y un hack. Regenerar con `./gradlew :app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class JuegoScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: JuegoUiState, altura: Dp = 1_000.dp, versionAConfirmar: Long? = null) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura) {
                JuegoScreenContent(
                    uiState = uiState,
                    textos = textos,
                    versionAConfirmar = versionAConfirmar,
                    onPedirRestaurar = {},
                    onConfirmarRestaurar = {},
                    onCancelarRestaurar = {},
                    onDescartarResultado = {},
                    onDescartarErrorDeRestaurar = {},
                    onDescartarErrorDeExportar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/juego_$nombre.png")
    }

    // El `height` del frame no basta solo: Robolectric recorta a la altura del dispositivo
    // simulado si no se agranda también con `@Config(qualifiers = "+hNNNNdp")` (ver
    // InicioScreenshotTest y DeviceDetailScreenshotTest de Vigía).
    @Test
    @Config(qualifiers = "+h4800dp")
    fun rojoFuegoCompleto() = capturar(
        "rojo_fuego",
        JuegoUiState(
            juego = DatosDeMuestra.juegoRojoFuego,
            historialDePartida = historialDePartida(DatosDeMuestra.historialDeRojoFuego, textos),
            versionesDeEstados = DatosDeMuestra.estadosDeRojoFuego,
            conectada = true,
        ),
        altura = 4_800.dp,
    )

    @Test
    fun verdeHojaVacia() = capturar(
        "verde_hoja_vacia",
        JuegoUiState(
            juego = DatosDeMuestra.juegoVerdeHoja,
            historialDePartida = historialDePartida(DatosDeMuestra.historialDeVerdeHoja, textos),
            versionesDeEstados = DatosDeMuestra.estadosDeVerdeHoja,
            conectada = false,
        ),
        altura = 900.dp,
    )

    @Test
    fun hackSoloRespaldo() = capturar(
        "hack",
        JuegoUiState(
            juego = DatosDeMuestra.juegoHack,
            historialDePartida = historialDePartida(DatosDeMuestra.historialDelHack, textos),
            conectada = true,
        ),
        altura = 700.dp,
    )

    // El `AlertDialog` de "Restaurar esta versión" no asienta en Robolectric: se captura el
    // contenido aparte (ver AGENTS.md y TarjetaDeConexionScreenshotTest).
    @Test
    fun dialogoConfirmarRestaurar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 480.dp) {
                Surface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarRestaurarContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_confirmar_restaurar.png")
    }
}
