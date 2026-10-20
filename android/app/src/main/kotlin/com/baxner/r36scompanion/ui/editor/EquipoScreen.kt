package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio

/** La pestaña Equipo del editor (ver el encargo de esta fase, punto 5): carga el equipo de la sesión y se vuelve a pedir cuando algo cambió en otra pantalla. */
@Composable
fun EquipoTab(editorViewModel: EditorViewModel, textos: Textos, onAbrirPokemon: (Casilla) -> Unit, modifier: Modifier = Modifier) {
    val estado by editorViewModel.estado.collectAsStateWithLifecycle()
    val hayCambios by editorViewModel.hayCambios.collectAsStateWithLifecycle()
    val legalidad by editorViewModel.legalidad.collectAsStateWithLifecycle()
    val sesion = (estado as? EditorViewModel.Estado.Listo)?.sesion

    var equipo by remember { mutableStateOf<List<ResumenDePokemon>?>(null) }
    LaunchedEffect(sesion, hayCambios) { equipo = sesion?.equipo() }

    EquipoTabContent(equipo = equipo, legalidad = legalidad, textos = textos, onAbrirPokemon = onAbrirPokemon, modifier = modifier)
}

/** `equipo == null` mientras carga (no se pinta nada: la carga es de milisegundos). */
@Composable
fun EquipoTabContent(
    equipo: List<ResumenDePokemon>?,
    legalidad: Map<Casilla, Legalidad>?,
    textos: Textos,
    onAbrirPokemon: (Casilla) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (equipo != null && equipo.isEmpty()) {
        EstadoVacio("Sin Pokémon en el equipo", modifier = modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(equipo.orEmpty(), key = { "${it.casilla.origen}-${it.casilla.posicion}-${it.casilla.caja}" }) { pokemon ->
            FilaDePokemonEditable(
                pokemon = pokemon,
                textos = textos,
                legalidad = legalidadDe(legalidad, pokemon.casilla),
                onClick = { onAbrirPokemon(pokemon.casilla) },
            )
        }
    }
}
