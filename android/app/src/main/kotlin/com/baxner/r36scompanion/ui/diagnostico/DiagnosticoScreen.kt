package com.baxner.r36scompanion.ui.diagnostico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales

/** Diagnóstico como ruta propia (Inicio y Ajustes la abren igual): el mismo contenido de [DiagnosticoScreenContent], con su barra y botón de regreso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: DiagnosticoViewModel = viewModel { DiagnosticoViewModel(modulos) }
    val pasos by viewModel.pasos.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") }
                },
                actions = {
                    IconButton(onClick = viewModel::actualizar) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                },
            )
        },
    ) { padding ->
        DiagnosticoScreenContent(pasos = pasos, modifier = Modifier.padding(padding))
    }
}

/**
 * El contenido puro de Diagnóstico: una lista con ✅/⚠️/❌ y qué hacer en cada una (ver el
 * encargo, punto 6). Sin barra ni botón de regreso para poder incrustarlo igual como último paso
 * de la Introducción (ver `ui/introduccion/IntroduccionScreen.kt`).
 */
@Composable
fun DiagnosticoScreenContent(pasos: List<PasoDeDiagnostico>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pasos) { paso ->
            TarjetaDeSeccion {
                FilaDeNivel(nivel = paso.nivel, titulo = paso.titulo, subtitulo = paso.detalle)
            }
        }
    }
}
