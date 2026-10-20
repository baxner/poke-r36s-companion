package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.banco.BancoScreenContent
import com.baxner.r36scompanion.ui.banco.BancoUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de la pestaña Banco (ver el encargo, punto 3 y 9): un Spearow Nv3 y un pendiente por conciliar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class BancoScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: BancoUiState, altura: Int = 1_300) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura.dp) {
                BancoScreenContent(
                    uiState = uiState,
                    textos = textos,
                    onDepositar = {},
                    onRetirar = {},
                    onIntercambiar = {},
                    onElegirCaja = {},
                    onConciliar = {},
                    onDescartarErrorDeConciliar = {},
                    onDescartarResultadoDeConciliar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/banco_$nombre.png")
    }

    @Test
    @Config(qualifiers = "+h1300dp")
    fun conSpearowYPendiente() = capturar(
        "con_spearow_y_pendiente",
        BancoUiState(
            pokemon = listOf(DatosDeMuestraFase4.pokemonSpearowEnBanco, DatosDeMuestraFase4.pokemonPendienteDeDeposito),
            cajaActual = 0,
            conectada = true,
        ),
    )

    @Test
    @Config(qualifiers = "+h900dp")
    fun vacioSinConsola() = capturar("vacio_sin_consola", BancoUiState(pokemon = emptyList(), cajaActual = 0, conectada = false), altura = 900)
}
