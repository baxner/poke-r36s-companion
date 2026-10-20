package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.editor.MochilaTabContent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de la pestaña Mochila del editor: bolsillos en español, cantidad editable y "Agregar objeto" (ver el encargo de esta fase, puntos 5 y 6). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class MochilaScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    @Test
    @Config(qualifiers = "+h1700dp")
    fun mochilaDeRojoFuego() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 1_700.dp) {
                MochilaTabContent(mochila = EditorDatosDeMuestra.mochila, textos = textos, onCambiarCantidad = { _, _, _ -> }, onAgregar = { _, _ -> })
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/editor_mochila.png")
    }
}
