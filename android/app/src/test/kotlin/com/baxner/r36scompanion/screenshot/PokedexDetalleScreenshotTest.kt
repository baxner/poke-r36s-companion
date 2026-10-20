package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.pokedex.PokedexDetalleScreenContent
import com.baxner.r36scompanion.ui.pokedex.PokedexDetalleUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual del detalle de una especie (ver el encargo, punto 2 y 9): Spearow (en el banco) y Kadabra (sin encuentro salvaje). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class PokedexDetalleScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: PokedexDetalleUiState, titulo: String) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 1_300.dp) {
                PokedexDetalleScreenContent(uiState = uiState, nombre = titulo, textos = textos)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/pokedex_detalle_$nombre.png")
    }

    @Test
    fun spearowEnElBanco() = capturar(
        "spearow",
        PokedexDetalleUiState(
            especie = DatosDeMuestraFase4.Spearow,
            cargando = false,
            entrada = DatosDeMuestraFase4.entradaDeSpearow,
            juegos = DatosDeMuestraFase4.juegosPorId,
            dondeAtrapar = DatosDeMuestraFase4.dondeAtraparSpearow,
        ),
        titulo = "Spearow",
    )

    @Test
    fun kadabraSinEncuentroSalvaje() = capturar(
        "kadabra",
        PokedexDetalleUiState(
            especie = DatosDeMuestraFase4.Kadabra,
            cargando = false,
            entrada = DatosDeMuestraFase4.entradaDeKadabra,
            juegos = DatosDeMuestraFase4.juegosPorId,
            dondeAtrapar = DatosDeMuestraFase4.dondeAtraparKadabra,
        ),
        titulo = "Kadabra",
    )
}
