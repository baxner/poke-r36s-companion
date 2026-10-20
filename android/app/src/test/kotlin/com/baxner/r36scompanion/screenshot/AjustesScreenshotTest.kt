package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.ajustes.AjustesScreenContent
import com.baxner.r36scompanion.ui.ajustes.AjustesUiState
import com.baxner.r36scompanion.ui.ajustes.ConfirmarOlvidarDesdeAjustesContent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de Ajustes (ver el encargo, punto 7 y 9, y el de la fase 5, punto 5: "Juegos en el teléfono"). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "+h1500dp")
class AjustesScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun capturar(nombre: String, uiState: AjustesUiState) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 1_500.dp) {
                AjustesScreenContent(
                    uiState = uiState,
                    mostrarConfirmarOlvidar = false,
                    onUsuarioChange = {},
                    onContrasenaChange = {},
                    onRespaldoAutomaticoChange = {},
                    onPedirOlvidarConsola = {},
                    onConfirmarOlvidarConsola = {},
                    onCancelarOlvidarConsola = {},
                    onAbrirIntroduccion = {},
                    onAbrirDiagnostico = {},
                    onAbrirLicencias = {},
                    onAbrirJuegosEnElTelefono = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/ajustes_$nombre.png")
    }

    @Test
    fun conConsolaFijada() = capturar(
        "con_consola",
        AjustesUiState(nombreDeConsola = "rg351mp", huellaFijada = "SHA256:AbCdEf1234567890", usuario = "ark", contrasena = "ark", respaldoAutomatico = true),
    )

    @Test
    fun sinConsolaFijada() = capturar("sin_consola", AjustesUiState())

    // El `AlertDialog` de "¿Olvidar esta consola?" no asienta en Robolectric: se captura el
    // contenido aparte (ver AGENTS.md).
    @Test
    fun dialogoConfirmarOlvidar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 420.dp) {
                Surface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarOlvidarDesdeAjustesContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_olvidar_desde_ajustes.png")
    }
}
