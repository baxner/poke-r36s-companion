package com.baxner.r36scompanion.screenshot

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.jugar.ConfirmarDescartarIntercambioContent
import com.baxner.r36scompanion.ui.jugar.ConfirmarTerminarIntercambioContent
import com.baxner.r36scompanion.ui.jugar.EtapaDeIntercambioReal
import com.baxner.r36scompanion.ui.jugar.IntercambioRealScreenContent
import com.baxner.r36scompanion.ui.jugar.IntercambioRealUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión visual del intercambio real (ver el encargo de la fase 5, puntos 4 y 9): las
 * instrucciones del Club de Cable, jugando en vertical y en horizontal (las dos superficies
 * salen negras en Robolectric, está bien), la revisión de cambios, y los dos diálogos de
 * confirmación. Regenerar con `./gradlew :app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class IntercambioRealScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun capturar(nombre: String, uiState: IntercambioRealUiState, altura: Dp = 900.dp) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura) {
                IntercambioRealScreenContent(
                    uiState = uiState,
                    onEmpezarAJugar = {},
                    onReintentar = {},
                    onControlar = {},
                    onTeclas = { _, _ -> },
                    onTenerSuperficie = { _: Int, _: Surface?, _: Int -> },
                    onCancelarTerminar = {},
                    onConfirmarTerminar = {},
                    onEscribir = {},
                    onPedirDescartar = {},
                    onCancelarDescartar = {},
                    onConfirmarDescartar = {},
                    onDescartarError = {},
                    onTerminar = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/intercambio_real_$nombre.png")
    }

    @Test
    @Config(qualifiers = "+h1300dp")
    fun instruccionesDelClubDeCable() = capturar(
        "instrucciones",
        IntercambioRealUiState(
            etapa = EtapaDeIntercambioReal.INSTRUCCIONES,
            nombreA = "Pokemon Edicion Rojo Fuego",
            nombreB = "Pokemon Edicion Esmeralda",
            necesitaPokedexNacional = true,
        ),
        altura = 1_300.dp,
    )

    @Test
    @Config(qualifiers = "+h2200dp")
    fun jugandoVertical() = capturar(
        "jugando_vertical",
        IntercambioRealUiState(
            etapa = EtapaDeIntercambioReal.JUGANDO,
            nombreA = "Rojo Fuego",
            nombreB = "Esmeralda",
            jugadorControlado = 0,
            vecesGuardadaA = 1,
            vecesGuardadaB = 0,
        ),
        altura = 2_200.dp,
    )

    @Test
    @Config(qualifiers = "+h900dp-land")
    fun jugandoHorizontal() = capturar(
        "jugando_horizontal",
        IntercambioRealUiState(
            etapa = EtapaDeIntercambioReal.JUGANDO,
            nombreA = "Rojo Fuego",
            nombreB = "Esmeralda",
            jugadorControlado = 1,
            vecesGuardadaA = 1,
            vecesGuardadaB = 2,
        ),
        altura = 900.dp,
    )

    @Test
    @Config(qualifiers = "+h1300dp")
    fun revisionDeCambios() = capturar(
        "revision",
        IntercambioRealUiState(
            etapa = EtapaDeIntercambioReal.REVISION,
            nombreA = "Pokemon Edicion Rojo Fuego",
            nombreB = "Pokemon Edicion Esmeralda",
            frasesDeCambiosA = listOf("Salió Kadabra del equipo"),
            frasesDeCambiosB = listOf("Entró Kadabra al equipo", "Kadabra evolucionó a Alakazam"),
        ),
        altura = 1_300.dp,
    )

    @Test
    @Config(qualifiers = "+h1300dp")
    fun revisionConErrorAlEscribir() = capturar(
        "revision_con_error",
        IntercambioRealUiState(
            etapa = EtapaDeIntercambioReal.REVISION,
            nombreA = "Pokemon Edicion Rojo Fuego",
            nombreB = "Pokemon Edicion Esmeralda",
            frasesDeCambiosA = listOf("Salió Kadabra del equipo"),
            frasesDeCambiosB = listOf("Entró Kadabra al equipo"),
            mensajeDeError = "No hay conexión con la consola: el intercambio queda pendiente. Conéctala y vuelve a intentar.",
        ),
        altura = 1_300.dp,
    )

    @Test
    fun escrito() = capturar("escrito", IntercambioRealUiState(etapa = EtapaDeIntercambioReal.ESCRITO), altura = 500.dp)

    @Test
    fun descartado() = capturar("descartado", IntercambioRealUiState(etapa = EtapaDeIntercambioReal.DESCARTADO), altura = 500.dp)

    // Los `AlertDialog` no asientan en Robolectric: se capturan los contenidos aparte (ver AGENTS.md).
    @Test
    fun dialogoConfirmarTerminar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 480.dp) {
                ComposeSurface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarTerminarIntercambioContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_confirmar_terminar_intercambio.png")
    }

    @Test
    fun dialogoConfirmarDescartar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 480.dp) {
                ComposeSurface(modifier = Modifier.padding(16.dp)) {
                    ConfirmarDescartarIntercambioContent(onConfirmar = {}, onCancelar = {})
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/dialogo_confirmar_descartar_intercambio.png")
    }
}
