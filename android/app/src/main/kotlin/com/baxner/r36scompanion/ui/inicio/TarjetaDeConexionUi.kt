package com.baxner.r36scompanion.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion

/**
 * La tarjeta de conexión de Inicio: traduce [estado] a [infoDeTarjeta] y pinta su botón según
 * [AccionDeTarjeta]. [onVerDiagnostico] se ofrece siempre que no está conectada (ver el encargo,
 * punto 3): un atajo a Diagnóstico para cuando algo no cuadra.
 */
@Composable
fun TarjetaDeConexion(
    estado: EstadoDeConexion,
    onReintentar: () -> Unit,
    onActivarAnclaje: () -> Unit,
    onPedirConfirmarOlvidar: () -> Unit,
    onIrAAjustes: () -> Unit,
    onVerDiagnostico: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = infoDeTarjeta(estado)
    TarjetaDeSeccion(modifier = modifier) {
        FilaDeNivel(nivel = info.nivel, titulo = info.titulo, subtitulo = info.cuerpo)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (info.accion) {
                AccionDeTarjeta.ACTIVAR_ANCLAJE -> BotonConIcono("Activar anclaje de USB", Icons.Default.WifiTethering, onActivarAnclaje)
                AccionDeTarjeta.REINTENTAR -> BotonConIcono("Reintentar", Icons.Default.Refresh, onReintentar)
                AccionDeTarjeta.CONFIRMAR_HUELLA -> BotonConIcono("Sí es mi consola, la reinstalé", Icons.Default.Refresh, onPedirConfirmarOlvidar)
                AccionDeTarjeta.IR_A_AJUSTES -> TextButton(onClick = onIrAAjustes) { Text("Ir a Ajustes") }
                AccionDeTarjeta.NINGUNA -> Unit
            }
            if (estado !is EstadoDeConexion.Conectada) {
                TextButton(onClick = onVerDiagnostico) { Text("Ver diagnóstico") }
            }
        }
    }
}

@Composable
private fun BotonConIcono(texto: String, icono: ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(texto, modifier = Modifier.padding(start = 8.dp))
    }
}

/** El contenido del diálogo "¿Sí es tu consola?" (ver AGENTS.md: un AlertDialog no asienta en Robolectric, así que esto se captura aparte). */
@Composable
fun ConfirmarOlvidarConsolaContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("¿Es tu consola?", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "La llave que contestó no es la que tenías guardada. Esto pasa si reinstalaste el sistema de tu R36S. " +
                "Si no hiciste eso, puede ser que el cable esté conectado a otro equipo: cancela y revisa antes de seguir.",
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancelar) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Cancelar", modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = onConfirmar) { Text("Sí, es mi consola") }
        }
    }
}
