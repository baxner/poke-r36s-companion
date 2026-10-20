package com.baxner.r36scompanion.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.theme.CompanionColors

/**
 * Qué tan grave es algo, para la tarjeta de conexión de Inicio y los pasos de Diagnóstico: mismo
 * significado de color/ícono en toda la app (ver [color] e [icono]).
 */
enum class NivelVisual { INFO, OK, AVISO, ERROR }

fun NivelVisual.color(): Color = when (this) {
    NivelVisual.INFO -> CompanionColors.Secondary
    NivelVisual.OK -> CompanionColors.Ok
    NivelVisual.AVISO -> CompanionColors.Warning
    NivelVisual.ERROR -> CompanionColors.Primary
}

fun NivelVisual.icono(): ImageVector = when (this) {
    NivelVisual.INFO -> Icons.Default.Info
    NivelVisual.OK -> Icons.Default.CheckCircle
    NivelVisual.AVISO -> Icons.Default.Warning
    NivelVisual.ERROR -> Icons.Default.Error
}

/** Tarjeta con un título chico arriba (opcional) y contenido libre abajo: la base de casi toda pantalla. */
@Composable
fun TarjetaDeSeccion(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    colorDeFondo: Color = CompanionColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorDeFondo),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (titulo != null) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary)
            }
            content()
        }
    }
}

/** Una fila con ícono de [nivel] a la izquierda y título/subtítulo a la derecha (pasos de Diagnóstico, avisos de un juego…). */
@Composable
fun FilaDeNivel(nivel: NivelVisual, titulo: String, modifier: Modifier = Modifier, subtitulo: String? = null) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(nivel.icono(), contentDescription = null, tint = nivel.color(), modifier = Modifier.padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
            }
        }
    }
}

/** Una etiqueta chica con fondo de color: "Hack: solo respaldo", el origen de una versión, etc. */
@Composable
fun Insignia(texto: String, modifier: Modifier = Modifier, color: Color = CompanionColors.SurfaceHigh, colorDeTexto: Color = CompanionColors.TextPrimary) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colorDeTexto,
        )
    }
}

/** Ícono grande + texto, centrado: listas vacías. */
@Composable
fun EstadoVacio(titulo: String, modifier: Modifier = Modifier, subtitulo: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextSecondary, textAlign = TextAlign.Center)
        if (subtitulo != null) {
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}
