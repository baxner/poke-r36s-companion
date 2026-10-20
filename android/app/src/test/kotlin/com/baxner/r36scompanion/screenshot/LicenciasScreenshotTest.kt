package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.ajustes.LicenciasScreenContent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de Licencias (ver el encargo, punto 7): PKHeX, las tablas de nombres y los sprites. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "+h1300dp")
class LicenciasScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun avisosFijos() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = 1_300.dp) {
                LicenciasScreenContent(avisosDelMotor = null)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/licencias.png")
    }
}
