package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baxner.r36scompanion.data.editor.Caja
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.theme.CompanionColors

/** La pestaña Cajas del editor (ver el encargo de esta fase, punto 5): cuadrícula de 6 columnas, deslizando entre cajas. */
@Composable
fun CajasTab(editorViewModel: EditorViewModel, onAbrirPokemon: (Casilla) -> Unit, modifier: Modifier = Modifier) {
    val estado by editorViewModel.estado.collectAsStateWithLifecycle()
    val hayCambios by editorViewModel.hayCambios.collectAsStateWithLifecycle()
    val legalidad by editorViewModel.legalidad.collectAsStateWithLifecycle()
    val sesion = (estado as? EditorViewModel.Estado.Listo)?.sesion

    var cajas by remember { mutableStateOf<List<Caja>?>(null) }
    LaunchedEffect(sesion, hayCambios) { cajas = sesion?.cajas() }

    CajasTabContent(cajas = cajas, legalidad = legalidad, onAbrirPokemon = onAbrirPokemon, modifier = modifier)
}

private const val ColumnasDeCaja = 6

@Composable
fun CajasTabContent(cajas: List<Caja>?, legalidad: Map<Casilla, Legalidad>?, onAbrirPokemon: (Casilla) -> Unit, modifier: Modifier = Modifier) {
    if (cajas != null && cajas.isEmpty()) {
        EstadoVacio("Sin cajas en esta partida", modifier = modifier.fillMaxSize())
        return
    }
    if (cajas == null) return

    val pagerState = rememberPagerState(initialPage = 0) { cajas.size }
    Column(modifier = modifier.fillMaxSize()) {
        val cajaActual = cajas[pagerState.currentPage]
        Text(
            cajaActual.nombre.ifBlank { "CAJA ${cajaActual.numero + 1}" },
            style = MaterialTheme.typography.titleMedium,
            color = CompanionColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pagina ->
            CuadriculaDeCaja(caja = cajas[pagina], legalidad = legalidad, onAbrirPokemon = onAbrirPokemon)
        }
    }
}

@Composable
private fun CuadriculaDeCaja(caja: Caja, legalidad: Map<Casilla, Legalidad>?, onAbrirPokemon: (Casilla) -> Unit, modifier: Modifier = Modifier) {
    val porPosicion = remember(caja) { caja.pokemon.associateBy { it.casilla.posicion } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(ColumnasDeCaja),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(caja.casillas) { posicion ->
            val pokemon = porPosicion[posicion]
            CeldaDePokemonEditable(
                pokemon = pokemon,
                legalidad = pokemon?.let { legalidadDe(legalidad, it.casilla) },
                onClick = { pokemon?.let { onAbrirPokemon(it.casilla) } },
            )
        }
    }
}
