package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.editor.Caja
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.banco.BancoDepositoScreenContent
import com.baxner.r36scompanion.ui.banco.BancoDepositoUiState
import com.baxner.r36scompanion.ui.banco.EtapaDeDeposito
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual del depósito en el banco (ver el encargo, punto 3 y 9): elegir juego → elegir Pokémon → confirmar → listo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class BancoDepositoScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: BancoDepositoUiState, altura: Int = 1_000) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura.dp) {
                BancoDepositoScreenContent(
                    uiState = uiState,
                    textos = textos,
                    onElegirJuego = {},
                    onElegirPokemon = { _, _ -> },
                    onConfirmar = {},
                    onCancelarConfirmacion = {},
                    onDescartarError = {},
                    onTerminar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/banco_deposito_$nombre.png")
    }

    @Test
    fun elegirJuego() = capturar(
        "elegir_juego",
        BancoDepositoUiState(cargando = false, etapa = EtapaDeDeposito.ELEGIR_JUEGO, juegosEditables = DatosDeMuestraFase4.juegosEditables),
    )

    @Test
    @Config(qualifiers = "+h1100dp")
    fun elegirPokemon() = capturar(
        "elegir_pokemon",
        BancoDepositoUiState(
            cargando = false,
            etapa = EtapaDeDeposito.ELEGIR_POKEMON,
            juegoElegido = DatosDeMuestra.juegoRojoFuego,
            cajas = listOf(
                Caja(numero = 0, nombre = "CAJA 1", casillas = 30, pokemon = listOf(DatosDeMuestraFase4.resumenDePokemonKadabra, DatosDeMuestraFase4.resumenDePokemonSeadra)),
            ),
        ),
        altura = 1_100,
    )

    @Test
    fun confirmar() = capturar(
        "confirmar",
        BancoDepositoUiState(
            cargando = false,
            etapa = EtapaDeDeposito.CONFIRMAR,
            juegoElegido = DatosDeMuestra.juegoRojoFuego,
            pokemonElegido = DatosDeMuestraFase4.resumenDePokemonKadabra,
        ),
        altura = 900,
    )

    @Test
    fun confirmarConError() = capturar(
        "confirmar_con_error",
        BancoDepositoUiState(
            cargando = false,
            etapa = EtapaDeDeposito.CONFIRMAR,
            juegoElegido = DatosDeMuestra.juegoRojoFuego,
            pokemonElegido = DatosDeMuestraFase4.resumenDePokemonKadabra,
            error = "Cierra \"Pokemon Edicion Rojo Fuego.srm\" en la consola (sal del juego en RetroArch) e inténtalo de nuevo.",
        ),
        altura = 1_000,
    )

    @Test
    fun exito() = capturar("exito", BancoDepositoUiState(cargando = false, etapa = EtapaDeDeposito.EXITO), altura = 500)
}
