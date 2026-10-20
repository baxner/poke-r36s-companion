package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.intercambio.Direccion
import com.baxner.r36scompanion.data.intercambio.EvolucionesPorIntercambio
import com.baxner.r36scompanion.data.intercambio.LlegadaPrevista
import com.baxner.r36scompanion.data.intercambio.Rechazo
import com.baxner.r36scompanion.data.intercambio.VistaPreviaDeIntercambio
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.intercambio.EtapaDeIntercambio
import com.baxner.r36scompanion.ui.intercambio.IntercambioScreenContent
import com.baxner.r36scompanion.ui.intercambio.IntercambioUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual del intercambio rápido (ver el encargo, punto 4 y 9): Kadabra → Alakazam entre Rojo Fuego y Esmeralda. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class IntercambioScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private val pokemonDeEsmeralda = ResumenDePokemon(
        casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0),
        especie = 151,
        nivel = 30,
        mote = "",
        esShiny = false,
        esHuevo = false,
        editable = true,
    )

    private fun capturar(nombre: String, uiState: IntercambioUiState, altura: Int = 1_000) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura.dp) {
                IntercambioScreenContent(
                    uiState = uiState,
                    textos = textos,
                    onElegirJuegoA = {},
                    onElegirJuegoB = {},
                    onAlternarSeleccionA = {},
                    onAlternarSeleccionB = {},
                    onAlternarEvolucionar = {},
                    onVerVistaPrevia = {},
                    onConfirmar = {},
                    onDescartarError = {},
                    onTerminar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/intercambio_$nombre.png")
    }

    @Test
    fun elegirJuegoA() = capturar("elegir_juego_a", IntercambioUiState(cargando = false, etapa = EtapaDeIntercambio.ELEGIR_JUEGO_A, juegosEditables = DatosDeMuestraFase4.juegosEditables))

    @Test
    fun elegirJuegoB() = capturar(
        "elegir_juego_b",
        IntercambioUiState(cargando = false, etapa = EtapaDeIntercambio.ELEGIR_JUEGO_B, juegosEditables = DatosDeMuestraFase4.juegosEditables, juegoA = DatosDeMuestra.juegoRojoFuego),
    )

    @Test
    @Config(qualifiers = "+h1300dp")
    fun elegirPokemon() = capturar(
        "elegir_pokemon",
        IntercambioUiState(
            cargando = false,
            etapa = EtapaDeIntercambio.ELEGIR_POKEMON,
            juegoA = DatosDeMuestra.juegoRojoFuego,
            juegoB = DatosDeMuestraFase4.juegoEsmeralda,
            pokemonDeA = listOf(DatosDeMuestraFase4.resumenDePokemonKadabra, DatosDeMuestraFase4.resumenDePokemonSeadra),
            pokemonDeB = listOf(pokemonDeEsmeralda),
            seleccionA = setOf(DatosDeMuestraFase4.resumenDePokemonKadabra.casilla),
            evolucionar = true,
        ),
        altura = 1_300,
    )

    @Test
    @Config(qualifiers = "+h1300dp")
    fun vistaPreviaKadabraAAlakazam() = capturar(
        "vista_previa",
        IntercambioUiState(
            cargando = false,
            etapa = EtapaDeIntercambio.VISTA_PREVIA,
            juegoA = DatosDeMuestra.juegoRojoFuego,
            juegoB = DatosDeMuestraFase4.juegoEsmeralda,
            vistaPrevia = VistaPreviaDeIntercambio(
                llegadas = listOf(
                    LlegadaPrevista(
                        direccion = Direccion.DE_A_HACIA_B,
                        sale = DatosDeMuestraFase4.resumenDePokemonKadabra,
                        llega = DatosDeMuestraFase4.resumenDePokemonKadabra.copy(especie = DatosDeMuestraFase4.Alakazam),
                        evolucion = EvolucionesPorIntercambio.Evolucion(desde = DatosDeMuestraFase4.Kadabra, hacia = DatosDeMuestraFase4.Alakazam, objetoNecesario = null),
                        cambios = listOf("species: Kadabra -> Alakazam (trade evolution)"),
                        cambiosEnPartidas = listOf("pokedexCaught: Emerald (Alakazam added)"),
                        legalidad = Legalidad(valida = true, mensajes = emptyList()),
                    ),
                ),
                rechazos = listOf(
                    // El motivo viene en inglés del motor (ver el KDoc de `Intercambio.kt`): no hay
                    // tabla de `Textos` para frases libres de legalidad, a diferencia de especies/objetos.
                    Rechazo(
                        direccion = Direccion.DE_B_HACIA_A,
                        casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 1, caja = 0),
                        motivo = "National Dex not unlocked in FireRed",
                    ),
                ),
            ),
        ),
        altura = 1_300,
    )

    @Test
    fun exito() = capturar("exito", IntercambioUiState(cargando = false, etapa = EtapaDeIntercambio.EXITO), altura = 500)
}
