package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.pokedex.FiltroDePokedex
import com.baxner.r36scompanion.ui.pokedex.PokedexScreenContent
import com.baxner.r36scompanion.ui.pokedex.PokedexUiState
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
 * Revisión visual de la pestaña Pokédex (ver el encargo, punto 2 y 9): la del usuario con 78
 * atrapadas y 111 vistas de 386, en gris las que le faltan.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class PokedexScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: PokedexUiState, altura: Int = 3_600) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura.dp) {
                PokedexScreenContent(uiState = uiState, textos = textos, onElegirFiltro = {}, onAbrirEspecie = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/pokedex_$nombre.png")
    }

    // Con el filtro TODAS solo las especies 1-74 salen a color (las 75-386 ya son gris: 75-78
    // están registradas pero ya no las tiene, y de 79 en adelante no las tiene en absoluto): con
    // 2200dp alcanza para ver el encabezado, los filtros y las dos zonas (a color y en gris) sin
    // gastar un mapa de bits gigante (un intento con las 386 de una sola vez quedó sin memoria al
    // correr junto con el resto de las pruebas del módulo).
    @Test
    @Config(qualifiers = "+h2200dp")
    fun todas() = capturar(
        "todas",
        PokedexUiState(cargando = false, resumen = DatosDeMuestraFase4.resumenPokedex, entradas = DatosDeMuestraFase4.entradasPokedex, filtro = FiltroDePokedex.TODAS),
        altura = 2_200,
    )

    @Test
    @Config(qualifiers = "+h1500dp")
    fun meFaltan() = capturar(
        "me_faltan",
        PokedexUiState(cargando = false, resumen = DatosDeMuestraFase4.resumenPokedex, entradas = DatosDeMuestraFase4.entradasPokedex, filtro = FiltroDePokedex.ME_FALTAN),
        altura = 1_500,
    )

    @Test
    fun cargando() = capturar("cargando", PokedexUiState(cargando = true), altura = 900)
}
