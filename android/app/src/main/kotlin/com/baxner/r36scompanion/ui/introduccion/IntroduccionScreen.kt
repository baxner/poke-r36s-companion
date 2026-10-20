package com.baxner.r36scompanion.ui.introduccion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.ui.diagnostico.DiagnosticoScreenContent
import com.baxner.r36scompanion.ui.diagnostico.DiagnosticoViewModel
import com.baxner.r36scompanion.ui.diagnostico.PasoDeDiagnostico
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.theme.CompanionColors
import kotlinx.coroutines.launch

private const val TotalDePaginas = 5

/**
 * La Introducción (ver el encargo, punto 5): se abre sola la primera vez y también desde Ajustes.
 * Termina en el Diagnóstico en vivo (reutiliza [DiagnosticoScreenContent]): así alguien que
 * recién llega ve de inmediato si sus pasos funcionaron.
 */
@Composable
fun IntroduccionScreen(onTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val diagnosticoViewModel: DiagnosticoViewModel = viewModel { DiagnosticoViewModel(modulos) }
    val pasos by diagnosticoViewModel.pasos.collectAsStateWithLifecycle()

    IntroduccionScreenContent(
        pasosDeDiagnostico = pasos,
        onAbrirAjustesDeAnclaje = { modulos.conexion.abrirAjustesDeAnclaje() },
        onTerminar = {
            modulos.preferenciasDeLaApp.introduccionVista = true
            onTerminar()
        },
        modifier = modifier,
    )
}

@Composable
fun IntroduccionScreenContent(
    pasosDeDiagnostico: List<PasoDeDiagnostico>,
    onAbrirAjustesDeAnclaje: () -> Unit,
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
    paginaInicial: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = paginaInicial) { TotalDePaginas }
    val alcance = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pagina ->
            when (pagina) {
                0 -> PaginaDeTexto(
                    icono = Icons.Default.Usb,
                    titulo = "Qué necesitas",
                    cuerpo = "Un cable USB-A a C, con un adaptador OTG del lado de la consola. " +
                        "Con un cable C a C el anclaje de USB suele salir en gris. " +
                        "Ten la consola cargada: en cuanto conectes el cable, es ella la que le da corriente a tu teléfono, no al revés.",
                )
                1 -> PaginaDeTexto(
                    icono = Icons.Default.SportsEsports,
                    titulo = "En la consola",
                    cuerpo = "Conecta el cable al puerto OTG de la R36S, no al DC. " +
                        "En el menú Options del carrusel, activa Enable Remote Services. " +
                        "Se apaga cada vez que reinicias la consola, así que hazlo de nuevo cada vez que la enciendas.",
                )
                2 -> PaginaDeTexto(
                    icono = Icons.Default.WifiTethering,
                    titulo = "En el teléfono",
                    cuerpo = "Activa el anclaje de USB. Si el botón sale en gris, revisa el cable del paso anterior, " +
                        "o desconéctalo y vuelve a conectarlo.",
                    accion = { Button(onClick = onAbrirAjustesDeAnclaje) { Text("Activar anclaje de USB") } },
                )
                3 -> PaginaDeTexto(
                    icono = Icons.Default.Save,
                    titulo = "Guarda dentro del juego",
                    cuerpo = "RetroArch siempre retoma el juego desde el último estado automático, no desde tu partida guardada. " +
                        "Cuando la app escribe una partida (al restaurar una versión, o al editar en una fase futura), aparta ese estado " +
                        "automático para que el cambio se note — pero igual conviene acostumbrarte a guardar dentro del juego de vez en " +
                        "cuando, desde su propio menú.",
                )
                else -> PaginaDeDiagnostico(pasosDeDiagnostico)
            }
        }

        IndicadorDePaginas(total = TotalDePaginas, actual = pagerState.currentPage)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { alcance.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Text("Atrás")
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (pagerState.currentPage < TotalDePaginas - 1) {
                Button(onClick = { alcance.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Text("Siguiente")
                }
            } else {
                Button(onClick = onTerminar) { Text("Terminar") }
            }
        }
    }
}

@Composable
private fun PaginaDeTexto(icono: ImageVector, titulo: String, cuerpo: String, accion: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icono, contentDescription = null, tint = CompanionColors.Primary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(titulo, style = MaterialTheme.typography.headlineSmall, color = CompanionColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(cuerpo, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextSecondary, textAlign = TextAlign.Center)
        if (accion != null) {
            Spacer(modifier = Modifier.height(24.dp))
            accion()
        }
    }
}

@Composable
private fun PaginaDeDiagnostico(pasos: List<PasoDeDiagnostico>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Esto es lo que veo ahorita",
            style = MaterialTheme.typography.titleLarge,
            color = CompanionColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        )
        DiagnosticoScreenContent(pasos = pasos, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IndicadorDePaginas(total: Int, actual: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(total) { indice ->
            val color = if (indice == actual) CompanionColors.Primary else CompanionColors.SurfaceHigh
            Box(modifier = Modifier.padding(4.dp).size(8.dp).background(color, CircleShape))
        }
    }
}
