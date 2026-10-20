package com.baxner.r36scompanion.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.theme.CompanionColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Aviso(val titulo: String, val cuerpo: String)

private val AvisosFijos = listOf(
    Aviso(
        "Motor PKHeX (@pkhex-everywhere/engine)",
        "El motor que lee, edita y exporta tus partidas es PKHeX.Core, del proyecto PKHeX.Everywhere, licenciado GPL-3.0-or-later. " +
            "Corre dentro de la app, sin red, en un WebView invisible.",
    ),
    Aviso(
        "Tablas de nombres en español",
        "Los nombres de especies, movimientos, objetos, habilidades, naturalezas y juegos vienen de las tablas de texto del propio " +
            "PKHeX (también GPL-3.0-or-later). Ver `assets/textos/es/ORIGEN.md` en el código de la app.",
    ),
    Aviso(
        "Sprites",
        "Los sprites de los Pokémon son de Nintendo/Game Freak, incluidos solo para uso personal, no comercial.",
    ),
    Aviso(
        "mGBA",
        "El emulador de Game Boy Advance que corre \"seguir jugando en el teléfono\" y el intercambio real (fase 5) es mGBA " +
            "(https://mgba.io), licenciado Mozilla Public License 2.0.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenciasScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var avisosDelMotor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        avisosDelMotor = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("motor/notices.json").bufferedReader().use { it.readText() } }.getOrNull()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Licencias") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
            )
        },
    ) { padding ->
        LicenciasScreenContent(avisosDelMotor = avisosDelMotor, modifier = Modifier.padding(padding))
    }
}

@Composable
fun LicenciasScreenContent(avisosDelMotor: String?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(AvisosFijos) { aviso ->
            TarjetaDeSeccion(titulo = aviso.titulo) {
                Text(aviso.cuerpo, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
            }
        }
        if (avisosDelMotor != null) {
            item {
                TarjetaDeSeccion(titulo = "Avisos adicionales del motor") {
                    Text(avisosDelMotor, style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
