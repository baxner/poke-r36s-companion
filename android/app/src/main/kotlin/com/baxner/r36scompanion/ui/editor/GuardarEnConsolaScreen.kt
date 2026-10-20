package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.theme.CompanionColors

/**
 * Guardar en la consola (ver el encargo de esta fase, punto 5): pide la revisión apenas se abre,
 * explica en palabras simples qué va a pasar y, al confirmar, escribe con
 * [com.baxner.r36scompanion.data.respaldos.EscritorDePartidas.escribir] (con todas sus reglas de
 * seguridad: ver docs/plan.md).
 */
@Composable
fun GuardarEnConsolaScreen(editorViewModel: EditorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val estadoDeGuardado by editorViewModel.estadoDeGuardado.collectAsStateWithLifecycle()
    val conectada by editorViewModel.conectada.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { editorViewModel.pedirRevisionParaGuardar() }

    GuardarEnConsolaScreenContent(
        estadoDeGuardado = estadoDeGuardado,
        conectada = conectada,
        onConfirmar = editorViewModel::confirmarGuardado,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardarEnConsolaScreenContent(
    estadoDeGuardado: EditorViewModel.EstadoDeGuardado,
    conectada: Boolean,
    onConfirmar: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Guardar en la consola") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (estadoDeGuardado) {
                EditorViewModel.EstadoDeGuardado.Inactivo, EditorViewModel.EstadoDeGuardado.Revisando ->
                    FilaDeNivel(NivelVisual.INFO, "Revisando la partida…")

                is EditorViewModel.EstadoDeGuardado.ListoParaConfirmar -> ContenidoDeRevision(estadoDeGuardado, conectada, onConfirmar)

                EditorViewModel.EstadoDeGuardado.Guardando -> FilaDeNivel(NivelVisual.INFO, "Guardando en la consola…")

                is EditorViewModel.EstadoDeGuardado.Exito -> ContenidoDeExito(estadoDeGuardado, onBack)

                is EditorViewModel.EstadoDeGuardado.Fallo -> FilaDeNivel(NivelVisual.ERROR, "No se pudo guardar", subtitulo = estadoDeGuardado.mensaje)
            }
        }
    }
}

@Composable
private fun ContenidoDeRevision(estado: EditorViewModel.EstadoDeGuardado.ListoParaConfirmar, conectada: Boolean, onConfirmar: () -> Unit) {
    val revision = estado.revision
    if (!revision.abre) {
        FilaDeNivel(
            NivelVisual.ERROR,
            "No se puede guardar",
            subtitulo = "La partida editada ya no abre" + (revision.problemas.firstOrNull()?.let { ": $it" } ?: "."),
        )
        return
    }

    TarjetaDeSeccion(titulo = "Qué va a pasar") {
        Text("• Se respalda lo que haya ahora mismo en la consola, antes de escribir nada.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
        Text(
            "• Tienes que haber cerrado el juego en RetroArch: si está abierto, no se puede escribir.",
            style = MaterialTheme.typography.bodyMedium,
            color = CompanionColors.TextPrimary,
        )
        Text(
            "• Se aparta (no se borra) el estado automático de este juego, para que RetroArch no retome desde ahí y pierdas la edición.",
            style = MaterialTheme.typography.bodyMedium,
            color = CompanionColors.TextPrimary,
        )
    }

    if (revision.pokemonIlegales.isNotEmpty()) {
        FilaDeNivel(
            NivelVisual.AVISO,
            "${revision.pokemonIlegales.size} Pokémon no serían legales en el juego real",
            subtitulo = "Puedes guardar de todos modos si quieres.",
        )
    }

    if (!conectada) {
        FilaDeNivel(NivelVisual.AVISO, "No hay consola conectada", subtitulo = "Conéctate para poder guardar.")
    }

    Button(onClick = onConfirmar, enabled = conectada, modifier = Modifier.fillMaxWidth()) { Text("Guardar en la consola") }
}

@Composable
private fun ContenidoDeExito(exito: EditorViewModel.EstadoDeGuardado.Exito, onBack: () -> Unit) {
    val resultado = exito.resultado
    FilaDeNivel(NivelVisual.OK, "Listo: se guardó en la consola")
    if (resultado.estadosApartados.isNotEmpty()) {
        Text(
            "Se apartó el estado automático: ${resultado.estadosApartados.joinToString { it.substringAfterLast('/') }}",
            style = MaterialTheme.typography.bodySmall,
            color = CompanionColors.TextSecondary,
        )
    }
    if (resultado.estadosManuales.isNotEmpty()) {
        FilaDeNivel(
            NivelVisual.AVISO,
            "Todavía hay estados guardados a mano",
            subtitulo = "Si cargas uno de estos perderás la edición: " + resultado.estadosManuales.joinToString { it.substringAfterLast('/') },
        )
    }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
}
