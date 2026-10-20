package com.baxner.r36scompanion.ui.pokedex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.pokedex.EntradaDePokedex
import com.baxner.r36scompanion.data.pokedex.ResumenDePokedex
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.sprites.IconoDePokedex
import com.baxner.r36scompanion.ui.theme.CompanionColors

@Composable
fun PokedexScreen(onAbrirEspecie: (Int) -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: PokedexViewModel = viewModel { PokedexViewModel(modulos) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PokedexScreenContent(
        uiState = uiState,
        textos = modulos.textos,
        onElegirFiltro = viewModel::elegirFiltro,
        onAbrirEspecie = onAbrirEspecie,
        modifier = modifier,
    )
}

@Composable
fun PokedexScreenContent(
    uiState: PokedexUiState,
    textos: Textos,
    onElegirFiltro: (FiltroDePokedex) -> Unit,
    onAbrirEspecie: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            uiState.resumen?.let { TarjetaDeResumenDePokedex(it) }
            FilaDeFiltros(filtroElegido = uiState.filtro, onElegirFiltro = onElegirFiltro)
        }

        when {
            uiState.cargando -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null -> EstadoVacio(
                titulo = "No se pudo cargar la Pokédex",
                subtitulo = uiState.error,
            )
            uiState.entradasFiltradas.isEmpty() -> EstadoVacio(
                titulo = "Nada con este filtro",
                subtitulo = "Prueba con otro de arriba.",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.entradasFiltradas, key = { it.especie }) { entrada ->
                    CeldaDeEspecie(entrada = entrada, textos = textos, onClick = { onAbrirEspecie(entrada.especie) })
                }
            }
        }
    }
}

@Composable
private fun TarjetaDeResumenDePokedex(resumen: ResumenDePokedex) {
    TarjetaDeSeccion(titulo = "Pokédex Nacional") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DatoDeResumen("${resumen.registradas}", "registradas")
            DatoDeResumen("${resumen.tenidas}", "las tienes")
            DatoDeResumen("${resumen.vistas}", "vistas")
            DatoDeResumen("${resumen.total}", "en total")
        }
    }
}

@Composable
private fun DatoDeResumen(numero: String, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(numero, style = MaterialTheme.typography.headlineSmall, color = CompanionColors.TextPrimary)
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilaDeFiltros(filtroElegido: FiltroDePokedex, onElegirFiltro: (FiltroDePokedex) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FiltroDePokedex.entries.forEach { filtro ->
            FilterChip(
                selected = filtro == filtroElegido,
                onClick = { onElegirFiltro(filtro) },
                label = { Text(filtro.etiqueta) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CompanionColors.Primary,
                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
        }
    }
}

@Composable
private fun CeldaDeEspecie(entrada: EntradaDePokedex, textos: Textos, onClick: () -> Unit) {
    val nombre = textos.especie(entrada.especie) ?: "#${entrada.especie}"
    Column(
        modifier = Modifier.fillMaxWidth().padding(2.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconoDePokedex(especieId = entrada.especie, laTiene = entrada.laTiene, contentDescription = nombre)
        Text(
            "#${entrada.especie.toString().padStart(3, '0')}",
            style = MaterialTheme.typography.labelSmall,
            color = CompanionColors.TextSecondary,
        )
        Text(
            nombre,
            style = MaterialTheme.typography.labelSmall,
            color = if (entrada.laTiene) CompanionColors.TextPrimary else CompanionColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
