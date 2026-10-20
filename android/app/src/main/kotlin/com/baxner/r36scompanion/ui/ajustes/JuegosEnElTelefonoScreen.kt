package com.baxner.r36scompanion.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.texto.formatoDeTamanoEnMb
import com.baxner.r36scompanion.ui.theme.CompanionColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuegosEnElTelefonoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: JuegosEnElTelefonoViewModel = viewModel { JuegosEnElTelefonoViewModel(modulos.romsDelTelefono) }
    val roms by viewModel.romsGuardadas.collectAsStateWithLifecycle()
    val borrando by viewModel.borrando.collectAsStateWithLifecycle()
    var aConfirmar by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Juegos en el teléfono") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
            )
        },
    ) { padding ->
        JuegosEnElTelefonoScreenContent(
            roms = roms,
            borrando = borrando,
            aConfirmar = aConfirmar,
            onPedirBorrar = { aConfirmar = it },
            onConfirmarBorrar = {
                aConfirmar?.let(viewModel::borrar)
                aConfirmar = null
            },
            onCancelarBorrar = { aConfirmar = null },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun JuegosEnElTelefonoScreenContent(
    roms: List<RomEnElTelefono>,
    borrando: Long?,
    aConfirmar: Long?,
    onPedirBorrar: (Long) -> Unit,
    onConfirmarBorrar: () -> Unit,
    onCancelarBorrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (roms.isEmpty()) {
        EstadoVacio(
            titulo = "No hay juegos copiados al teléfono",
            subtitulo = "Se copian solos al seguir jugando en el teléfono o al empezar un intercambio real.",
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(roms, key = { it.juegoId }) { rom ->
                TarjetaDeSeccion {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rom.archivo.name, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                            Text(formatoDeTamanoEnMb(rom.tamano), style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
                        }
                        if (borrando == rom.juegoId) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        } else {
                            IconButton(onClick = { onPedirBorrar(rom.juegoId) }) { Icon(Icons.Default.Delete, contentDescription = "Borrar") }
                        }
                    }
                }
            }
        }
    }

    if (aConfirmar != null) {
        AlertDialog(
            onDismissRequest = onCancelarBorrar,
            confirmButton = {},
            text = { ConfirmarBorrarRomContent(onConfirmar = onConfirmarBorrar, onCancelar = onCancelarBorrar) },
        )
    }
}

/** Aparte del `AlertDialog` (ver AGENTS.md, Roborazzi no asienta diálogos). */
@Composable
fun ConfirmarBorrarRomContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("¿Borrar esta copia?", style = MaterialTheme.typography.titleMedium)
        Text(
            "El juego y la partida siguen en la consola: solo se borra la copia del teléfono.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
            Button(onClick = onConfirmar) { Text("Borrar") }
        }
    }
}
