package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import com.baxner.r36scompanion.ui.ajustes.ConfirmarBorrarRomContent
import com.baxner.r36scompanion.ui.ajustes.JuegosEnElTelefonoScreenContent
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
 * Revisión visual de Ajustes → "Juegos en el teléfono" (ver el encargo de la fase 5, punto 5):
 * la lista con tamaño en MB, y vacía. Regenerar con `./gradlew :app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class JuegosEnElTelefonoScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val romRojoFuego = RomEnElTelefono(
        juegoId = 1L,
        archivo = File("Pokemon Edicion Rojo Fuego.gba"),
        rutaEnConsola = "/roms/gba/Pokemon Edicion Rojo Fuego.gba",
        tamanoEnConsola = 16 * 1024 * 1024L,
        modificadoEnConsola = 1_000L,
        codigoDeJuego = "BPRE",
        tituloInterno = "POKEMON FIRE",
        tamano = 16 * 1024 * 1024L,
    )
    private val romEsmeralda = RomEnElTelefono(
        juegoId = 2L,
        archivo = File("Pokemon Edicion Esmeralda.gba"),
        rutaEnConsola = "/roms/gba/Pokemon Edicion Esmeralda.gba",
        tamanoEnConsola = 32 * 1024 * 1024L,
        modificadoEnConsola = 1_000L,
        codigoDeJuego = "BPEE",
        tituloInterno = "POKEMON EMER",
        tamano = 32 * 1024 * 1024L,
    )

    private fun capturar(nombre: String, roms: List<RomEnElTelefono>, borrando: Long? = null, aConfirmar: Long? = null, altura: Dp = 700.dp) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura) {
                JuegosEnElTelefonoScreenContent(
                    roms = roms,
                    borrando = borrando,
                    aConfirmar = aConfirmar,
                    onPedirBorrar = {},
                    onConfirmarBorrar = {},
                    onCancelarBorrar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/juegos_en_el_telefono_$nombre.png")
    }

    @Test
    fun conDosJuegos() = capturar("con_juegos", listOf(romRojoFuego, romEsmeralda))

    @Test
    fun vacia() = capturar("vacia", emptyList(), altura = 400.dp)

    // El `AlertDialog` de "¿Borrar esta copia?" no asienta en Robolectric: se captura el
    // contenido aparte (ver AGENTS.md).
    @Test
    fun dialogoConfirmarBorrar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 420.dp) {
                Surface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarBorrarRomContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_confirmar_borrar_rom.png")
    }
}
