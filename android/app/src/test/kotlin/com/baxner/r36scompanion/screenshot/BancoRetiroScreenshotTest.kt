package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.banco.MovimientoPreparado
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.banco.BancoRetiroScreenContent
import com.baxner.r36scompanion.ui.banco.BancoRetiroUiState
import com.baxner.r36scompanion.ui.banco.EtapaDeRetiro
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual del retiro del banco (ver el encargo, punto 3 y 9): el Spearow del banco de vuelta a Rojo Fuego. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class BancoRetiroScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: BancoRetiroUiState, altura: Int = 900) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura.dp) {
                BancoRetiroScreenContent(
                    uiState = uiState,
                    textos = textos,
                    onElegirJuego = {},
                    onConfirmar = {},
                    onCancelarVistaPrevia = {},
                    onDescartarError = {},
                    onTerminar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/banco_retiro_$nombre.png")
    }

    @Test
    fun elegirJuego() = capturar(
        "elegir_juego",
        BancoRetiroUiState(cargando = false, etapa = EtapaDeRetiro.ELEGIR_JUEGO, pokemon = DatosDeMuestraFase4.pokemonSpearowEnBanco, juegosEditables = DatosDeMuestraFase4.juegosEditables),
    )

    @Test
    fun vistaPrevia() = capturar(
        "vista_previa",
        BancoRetiroUiState(
            cargando = false,
            etapa = EtapaDeRetiro.VISTA_PREVIA,
            pokemon = DatosDeMuestraFase4.pokemonSpearowEnBanco,
            juegoElegido = DatosDeMuestra.juegoRojoFuego,
            movimiento = MovimientoPreparado(
                pokemonId = 1,
                juegoId = DatosDeMuestra.juegoRojoFuego.id,
                partidaNueva = ByteArray(0),
                base = "hash",
                casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 2, caja = 0),
                legalidadEnDestino = Legalidad(valida = true, mensajes = emptyList()),
                cambios = listOf("box: none → 0 (added to box)"),
            ),
        ),
        altura = 1_000,
    )

    @Test
    fun exito() = capturar("exito", BancoRetiroUiState(cargando = false, etapa = EtapaDeRetiro.EXITO, pokemon = DatosDeMuestraFase4.pokemonSpearowEnBanco), altura = 500)
}
