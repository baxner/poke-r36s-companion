package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.consola.AjustesRetroArch
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.diagnostico.DiagnosticoScreenContent
import com.baxner.r36scompanion.ui.diagnostico.HechosDeDiagnostico
import com.baxner.r36scompanion.ui.diagnostico.pasosDeDiagnostico
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de Diagnóstico (ver el encargo, punto 6 y 9): todo sano, y con avisos/errores reales. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "+h1700dp")
class DiagnosticoScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun capturar(nombre: String, hechos: HechosDeDiagnostico) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 1_700.dp) {
                DiagnosticoScreenContent(pasos = pasosDeDiagnostico(hechos))
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/diagnostico_$nombre.png")
    }

    @Test
    fun todoBien() = capturar(
        "todo_bien",
        HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.Conectada(
                Consola("10.101.33.156", 22, "SHA256:abc", "ssh-ed25519", "rg351mp"),
                esNueva = false,
            ),
            carpetaGbaExiste = true,
            partidasEncontradas = 11,
            ajustesRetroArch = listOf(
                AjustesRetroArch(
                    archivo = "/home/ark/.config/retroarch/retroarch.cfg",
                    cargaEstadoAutomatico = false,
                    guardaEstadoAutomatico = true,
                    partidasJuntoALaRom = true,
                    estadosJuntoALaRom = true,
                    carpetaDePartidas = null,
                    carpetaDeEstados = null,
                    intervaloDeAutoguardado = 0,
                ),
            ),
        ),
    )

    @Test
    fun conAvisosYErrores() = capturar(
        "con_avisos",
        HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.NoEncontrada,
        ),
    )
}
