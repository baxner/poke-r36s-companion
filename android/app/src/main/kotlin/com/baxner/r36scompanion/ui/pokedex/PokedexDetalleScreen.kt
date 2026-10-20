package com.baxner.r36scompanion.ui.pokedex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.Insignia
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.sprites.SpriteGrande
import com.baxner.r36scompanion.ui.theme.CompanionColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexDetalleScreen(especie: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: PokedexDetalleViewModel = viewModel(key = "pokedex-$especie") { PokedexDetalleViewModel(modulos, especie) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nombre = modulos.textos.especie(especie) ?: "#$especie"

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(nombre) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
            )
        },
    ) { padding ->
        PokedexDetalleScreenContent(uiState = uiState, nombre = nombre, textos = modulos.textos, modifier = Modifier.padding(padding))
    }
}

@Composable
fun PokedexDetalleScreenContent(uiState: PokedexDetalleUiState, nombre: String, textos: Textos, modifier: Modifier = Modifier) {
    if (uiState.cargando) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (uiState.error != null) {
        EstadoVacio(titulo = "No se pudo cargar", subtitulo = uiState.error, modifier = modifier.fillMaxSize())
        return
    }

    val entrada = uiState.entrada
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpriteGrande(especieId = uiState.especie, shiny = false, tamano = 96.dp, contentDescription = nombre)
                Text("#${uiState.especie.toString().padStart(3, '0')} $nombre", style = MaterialTheme.typography.titleLarge, color = CompanionColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entrada?.registrada == true) Insignia("Registrada", color = CompanionColors.Ok.copy(alpha = 0.2f), colorDeTexto = CompanionColors.Ok)
                    if (entrada?.laTiene == true) Insignia("La tienes", color = CompanionColors.Secondary.copy(alpha = 0.2f), colorDeTexto = CompanionColors.Secondary)
                }
            }
        }

        item { TituloDeSeccionDetalle("Dónde está") }
        if (entrada == null || entrada.ubicaciones.isEmpty()) {
            item {
                Text(
                    "No la tienes ahora mismo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.TextSecondary,
                )
            }
        } else {
            items(entrada.ubicaciones) { ubicacion ->
                val texto = ubicacion.aTexto(uiState.juegos, textos)
                TarjetaDeSeccion {
                    Text(texto.lugar, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                    Text(texto.detalle, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                }
            }
        }

        item { TituloDeSeccionDetalle("Registrada en la Pokédex de") }
        if (entrada == null || entrada.atrapadaEn.isEmpty() && entrada.vistaEn.isEmpty()) {
            item { Text("En ninguna todavía.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary) }
        } else {
            items(entrada.vistaEn.toList() + entrada.atrapadaEn.filter { it !in entrada.vistaEn }) { juegoId ->
                val nombreJuego = uiState.juegos[juegoId]?.nombreParaMostrar(textos) ?: "un juego"
                val comoAtrapada = juegoId in entrada.atrapadaEn
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nombreJuego, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
                    Insignia(if (comoAtrapada) "Atrapada" else "Solo vista")
                }
            }
        }

        item { TituloDeSeccionDetalle("Dónde atraparla") }
        if (uiState.dondeAtrapar.isEmpty()) {
            item {
                Text(
                    "Solo por intercambio o evento: no sale por encuentro normal en tus juegos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.TextSecondary,
                )
            }
        } else {
            items(uiState.dondeAtrapar) { lugar ->
                Text(lugar.aTexto(uiState.juegos, textos), style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun TituloDeSeccionDetalle(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary)
}
