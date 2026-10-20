package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.theme.CompanionColors

private val PestanasDelEditor = listOf("Equipo", "Cajas", "Mochila", "Entrenador")

/**
 * El editor de una partida (ver el encargo de esta fase, punto 5): pestañas Equipo / Cajas /
 * Mochila / Entrenador sobre [editorViewModel] (la misma instancia que usan
 * [PokemonEditorScreen] y [GuardarEnConsolaScreen]: ver cómo la comparte
 * `CompanionNavHost.kt` entre las tres rutas de `editor/{juegoId}...`), con "Deshacer todo" y
 * "Guardar en la consola" (solo si hay cambios) en la barra superior.
 */
@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    textos: Textos,
    onBack: () -> Unit,
    onAbrirPokemon: (Casilla) -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by editorViewModel.estado.collectAsStateWithLifecycle()
    val hayCambios by editorViewModel.hayCambios.collectAsStateWithLifecycle()

    EditorScreenScaffold(
        estado = estado,
        hayCambios = hayCambios,
        onBack = onBack,
        onDeshacerTodo = editorViewModel::deshacerTodo,
        onGuardar = onGuardar,
        modifier = modifier,
    ) { pagina ->
        when (pagina) {
            0 -> EquipoTab(editorViewModel, textos, onAbrirPokemon, modifier = Modifier.fillMaxSize())
            1 -> CajasTab(editorViewModel, onAbrirPokemon, modifier = Modifier.fillMaxSize())
            2 -> MochilaTab(editorViewModel, textos, modifier = Modifier.fillMaxSize())
            else -> EntrenadorTab(editorViewModel, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreenScaffold(
    estado: EditorViewModel.Estado,
    hayCambios: Boolean,
    onBack: () -> Unit,
    onDeshacerTodo: () -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier,
    contenidoDePestana: @Composable (Int) -> Unit,
) {
    var pagina by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Editar partida") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
                    actions = {
                        if (estado is EditorViewModel.Estado.Listo) {
                            TextButton(onClick = onDeshacerTodo, enabled = hayCambios) { Text("Deshacer todo") }
                            if (hayCambios) {
                                Button(onClick = onGuardar) { Text("Guardar en la consola") }
                            }
                        }
                    },
                )
                if (estado is EditorViewModel.Estado.Listo) {
                    TabRow(selectedTabIndex = pagina) {
                        PestanasDelEditor.forEachIndexed { indice, titulo ->
                            Tab(selected = pagina == indice, onClick = { pagina = indice }, text = { Text(titulo) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (estado) {
                EditorViewModel.Estado.Cargando -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Abriendo la partida para editar…", color = CompanionColors.TextSecondary)
                }
                is EditorViewModel.Estado.Error -> Box(modifier = Modifier.padding(16.dp)) {
                    FilaDeNivel(NivelVisual.ERROR, "No se pudo abrir el editor", subtitulo = estado.mensaje)
                }
                is EditorViewModel.Estado.Listo -> contenidoDePestana(pagina)
            }
        }
    }
}
