package com.baxner.r36scompanion.ui.juego

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import com.baxner.r36scompanion.ui.componentes.Insignia
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.texto.formatoDeFechaYHora
import com.baxner.r36scompanion.ui.theme.CompanionColors

/** Una versión del historial de la partida: fecha, origen, qué cambió y, si está conectada, Restaurar (ver el encargo, punto 4). */
@Composable
fun FilaDeHistorialDePartida(item: ItemDeHistorial, esLaMasVieja: Boolean, puedeRestaurar: Boolean, onRestaurar: () -> Unit, modifier: Modifier = Modifier) {
    TarjetaDeSeccion(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatoDeFechaYHora(item.version.respaldadaEn), style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
            Insignia(item.etiquetaDeOrigen)
        }
        when {
            item.cambios.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item.cambios.forEach { cambio -> Text("• $cambio", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary) }
            }
            esLaMasVieja -> Text("Primera versión respaldada", style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
            else -> Text("Sin cambios respecto a la anterior", style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
        }
        if (puedeRestaurar) {
            Button(onClick = onRestaurar) { Text("Restaurar esta versión en la consola") }
        }
    }
}

/** Una versión de un estado (`.state`/`.state.auto`): aparte, sin "qué cambió" ni Restaurar (ver el encargo, punto 4). */
@Composable
fun FilaDeEstado(version: VersionRespaldada, modifier: Modifier = Modifier) {
    TarjetaDeSeccion(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatoDeFechaYHora(version.respaldadaEn), style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
            Insignia(etiquetaDeTipoDeEstado(version.tipo))
        }
    }
}

/** El contenido del diálogo "Restaurar esta versión" (aparte del `AlertDialog`: ver AGENTS.md). */
@Composable
fun ConfirmarRestaurarContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Restaurar esta versión en la consola", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Antes de escribir, se respalda lo que haya ahora mismo en la consola. Tienes que haber cerrado el juego en RetroArch: " +
                "si está abierto, no se puede escribir. Se aparta (no se borra) el estado automático de este juego, para que " +
                "RetroArch no vuelva a cargar lo de antes la próxima vez que lo abras.",
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
            Button(onClick = onConfirmar) { Text("Restaurar") }
        }
    }
}
