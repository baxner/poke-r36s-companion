package com.baxner.r36scompanion.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.juego.nombreDeJuego
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.Insignia
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.sprites.IconoDeEquipo
import com.baxner.r36scompanion.ui.texto.formatoDeFechaYHora
import com.baxner.r36scompanion.ui.texto.formatoDeHorasYMinutos
import com.baxner.r36scompanion.ui.theme.CompanionColors

/** [ganadas] de [total] medallas, como [total] puntos (el primero en Esmeralda/Rubí/Zafiro/Rojo Fuego/Verde Hoja es 8). */
@Composable
fun FilaDeMedallas(ganadas: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { indice ->
            PuntoDeMedalla(ganada = indice < ganadas)
        }
    }
}

@Composable
private fun PuntoDeMedalla(ganada: Boolean) {
    val modificador = Modifier.size(9.dp).background(if (ganada) CompanionColors.MedallaGanada else CompanionColors.MedallaFaltante, CircleShape)
    androidx.compose.foundation.layout.Box(
        modifier = if (ganada) modificador else modificador.border(1.dp, CompanionColors.MedallaFaltanteBorde, CircleShape),
    )
}

/** Un juego de Pokémon en la lista de Inicio: entrenador, tiempo, medallas, Pokédex, equipo y avisos (ver el encargo, punto 3). */
@Composable
fun FilaDeJuegoPokemon(juego: JuegoRespaldado, textos: Textos, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val resumen = juego.resumen
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = nombreDeJuego(juego, textos),
                style = MaterialTheme.typography.titleMedium,
                color = CompanionColors.TextPrimary,
            )
            if (resumen != null) {
                val tiempo = if (resumen.horas != null && resumen.minutos != null) formatoDeHorasYMinutos(resumen.horas, resumen.minutos) else null
                Text(
                    text = listOfNotNull("Entrenador ${resumen.entrenador}", tiempo).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CompanionColors.TextSecondary,
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilaDeMedallas(ganadas = resumen.medallas ?: 0, total = resumen.medallasTotales ?: 8)
                    resumen.atrapados?.let { Text("$it atrapados", style = MaterialTheme.typography.labelMedium, color = CompanionColors.TextSecondary) }
                }
                if (resumen.equipo.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        resumen.equipo.forEach { pokemon ->
                            IconoDeEquipo(especieId = pokemon.especieId, shiny = pokemon.shiny, tamano = 28.dp)
                        }
                    }
                }
            }
            when (avisoDe(juego)) {
                AvisoDeJuego.PARTIDA_VACIA -> FilaDeNivel(NivelVisual.AVISO, TextoPartidaVacia)
                AvisoDeJuego.SOLO_RESPALDO -> Insignia(TextoSoloRespaldo, color = CompanionColors.Warning.copy(alpha = 0.2f), colorDeTexto = CompanionColors.Warning)
                AvisoDeJuego.NINGUNO -> Unit
            }
            Text(textoDeUltimoRespaldo(juego.ultimoRespaldo), style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary)
        }
    }
}

/** Un juego que no es de Pokémon, compacto: solo nombre y último respaldo (ver el encargo, punto 3). */
@Composable
fun FilaDeJuegoOtro(juego: JuegoRespaldado, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CompanionColors.SurfaceHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(juego.nombre, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
            Text(textoDeUltimoRespaldo(juego.ultimoRespaldo), style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary)
        }
    }
}

private fun textoDeUltimoRespaldo(ultimoRespaldo: Long?): String =
    ultimoRespaldo?.let { "Último respaldo: ${formatoDeFechaYHora(it)}" } ?: "Sin respaldar todavía"
