package com.baxner.r36scompanion.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.theme.CompanionTheme

/**
 * Tamaño de teléfono para las capturas de revisión visual (como en Vigía, `ScreenshotHarness.kt`):
 * un `Box` de tamaño fijo, no solo los qualifiers de Robolectric, para que `onRoot()` mida un
 * viewport fijo y no todo el contenido real (p. ej. un `LazyColumn` sin recortar).
 */
private val PhoneWidth = 412.dp
private val PhoneHeight = 915.dp

@Composable
fun CompanionScreenshotFrame(height: Dp = PhoneHeight, width: Dp = PhoneWidth, content: @Composable () -> Unit) {
    CompanionTheme {
        Box(modifier = Modifier.size(width, height)) {
            Surface(modifier = Modifier.size(width, height)) {
                content()
            }
        }
    }
}
