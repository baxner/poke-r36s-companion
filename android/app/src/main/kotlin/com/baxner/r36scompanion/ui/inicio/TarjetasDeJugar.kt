package com.baxner.r36scompanion.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.jugar.EstadoDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.IntercambioReal
import com.baxner.r36scompanion.data.jugar.PartidaPrestada
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.theme.CompanionColors

/*
 * Las tarjetas de la fase 5 en Inicio (ver su encargo, punto 5): una por cada partida prestada
 * ("seguir jugando en el teléfono") y una si hay un intercambio real pendiente.
 */

@Composable
fun TarjetaDePrestamo(prestada: PartidaPrestada, juego: JuegoRespaldado?, onSeguirJugando: (Long) -> Unit, onRegresar: (Long) -> Unit, modifier: Modifier = Modifier) {
    val nombre = juego?.nombre ?: "Un juego"
    TarjetaDeSeccion(modifier = modifier) {
        Text("$nombre está en el teléfono", style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSeguirJugando(prestada.juegoId) }) { Text("Seguir jugando") }
            OutlinedButton(onClick = { onRegresar(prestada.juegoId) }) { Text("Regresar a la consola") }
        }
    }
}

@Composable
fun TarjetaDeIntercambioRealPendiente(
    intercambio: IntercambioReal,
    juegoA: JuegoRespaldado?,
    juegoB: JuegoRespaldado?,
    onContinuar: (Long, Long) -> Unit,
    onEscribir: (Long) -> Unit,
    onDescartar: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nombreA = juegoA?.nombre ?: "un juego"
    val nombreB = juegoB?.nombre ?: "otro juego"
    TarjetaDeSeccion(modifier = modifier) {
        Text("Intercambio real pendiente: $nombreA y $nombreB", style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onContinuar(intercambio.juegoA, intercambio.juegoB) }) { Text("Continuar") }
            if (intercambio.estado == EstadoDeIntercambioReal.POR_ESCRIBIR) {
                OutlinedButton(onClick = { onEscribir(intercambio.id) }) { Text("Escribir") }
                OutlinedButton(onClick = { onDescartar(intercambio.id) }) { Text("Descartar") }
            }
        }
    }
}
