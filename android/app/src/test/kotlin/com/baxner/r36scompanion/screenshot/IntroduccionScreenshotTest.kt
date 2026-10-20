package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.diagnostico.HechosDeDiagnostico
import com.baxner.r36scompanion.ui.diagnostico.pasosDeDiagnostico
import com.baxner.r36scompanion.ui.introduccion.IntroduccionScreenContent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de la Introducción, página por página (ver el encargo, punto 5 y 9). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class IntroduccionScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val pasos = pasosDeDiagnostico(
        HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.Conectada(Consola("10.101.33.156", 22, "SHA256:abc", "ssh-ed25519", "rg351mp"), esNueva = true),
            carpetaGbaExiste = true,
            partidasEncontradas = 11,
        ),
    )

    private fun capturar(nombre: String, pagina: Int) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame {
                IntroduccionScreenContent(
                    pasosDeDiagnostico = pasos,
                    onAbrirAjustesDeAnclaje = {},
                    onTerminar = {},
                    paginaInicial = pagina,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/introduccion_$nombre.png")
    }

    @Test
    fun paginaQueNecesitas() = capturar("1_que_necesitas", 0)

    @Test
    fun paginaEnLaConsola() = capturar("2_en_la_consola", 1)

    @Test
    fun paginaEnElTelefono() = capturar("3_en_el_telefono", 2)

    @Test
    fun paginaGuardaDentroDelJuego() = capturar("4_guarda_dentro_del_juego", 3)

    @Test
    @Config(qualifiers = "+h1700dp")
    fun paginaDiagnostico() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 1_700.dp) {
                IntroduccionScreenContent(
                    pasosDeDiagnostico = pasos,
                    onAbrirAjustesDeAnclaje = {},
                    onTerminar = {},
                    paginaInicial = 4,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/introduccion_5_diagnostico.png")
    }
}
