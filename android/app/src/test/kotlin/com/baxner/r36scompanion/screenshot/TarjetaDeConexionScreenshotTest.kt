package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.inicio.ConfirmarOlvidarConsolaContent
import com.baxner.r36scompanion.ui.inicio.TarjetaDeConexion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión visual de la tarjeta de conexión de Inicio, uno por cada [EstadoDeConexion] (ver el
 * encargo, punto 9). Regenerar con:
 *
 *   cd android && JAVA_HOME=/usr/local/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
 *
 * Los PNG quedan en app/build/outputs/roborazzi/ (build/, no se versiona).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class TarjetaDeConexionScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val consola = Consola(host = "10.101.33.156", puerto = 22, huella = "SHA256:AbCdEf1234567890", algoritmoDeLlave = "ssh-ed25519", nombreDeEquipo = "rg351mp")

    private fun capturar(nombre: String, estado: EstadoDeConexion) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 320.dp) {
                TarjetaDeConexion(
                    estado = estado,
                    onReintentar = {},
                    onActivarAnclaje = {},
                    onPedirConfirmarOlvidar = {},
                    onIrAAjustes = {},
                    onVerDiagnostico = {},
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/tarjeta_$nombre.png")
    }

    @Test
    fun sinCable() = capturar("sin_cable", EstadoDeConexion.SinCable)

    @Test
    fun sinAnclaje() = capturar("sin_anclaje", EstadoDeConexion.SinAnclaje)

    @Test
    fun buscando() = capturar("buscando", EstadoDeConexion.Buscando)

    @Test
    fun noEncontrada() = capturar("no_encontrada", EstadoDeConexion.NoEncontrada)

    @Test
    fun conectadaNueva() = capturar("conectada_nueva", EstadoDeConexion.Conectada(consola, esNueva = true))

    @Test
    fun conectadaConocida() = capturar("conectada_conocida", EstadoDeConexion.Conectada(consola, esNueva = false))

    @Test
    fun huellaDistinta() = capturar("huella_distinta", EstadoDeConexion.HuellaDistinta(esperada = "SHA256:ViejaHuella", recibida = "SHA256:AbCdEf1234567890"))

    @Test
    fun credencialesIncorrectas() = capturar("credenciales_incorrectas", EstadoDeConexion.CredencialesIncorrectas)

    @Test
    fun fallo() = capturar("fallo", EstadoDeConexion.Fallo("no se pudo buscar la consola: la red no responde"))

    // El `AlertDialog` de "¿Sí es tu consola?" no asienta en Robolectric (ver AGENTS.md): se
    // captura el contenido del diálogo aparte, sin el `AlertDialog` que lo envuelve en la app real.
    @Test
    fun dialogoConfirmarOlvidar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 420.dp) {
                androidx.compose.material3.Surface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarOlvidarConsolaContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_confirmar_olvidar_consola.png")
    }
}
