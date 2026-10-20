package com.baxner.r36scompanion.ui.juego

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.respaldos.PokemonCorto
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.inicio.FilaDeMedallas
import com.baxner.r36scompanion.ui.sprites.SpriteGrande
import com.baxner.r36scompanion.ui.texto.esMoteDeEspecie
import com.baxner.r36scompanion.ui.theme.CompanionColors

/** El color de cabecera de verdad (ver [ColorDeJuego]): aquí porque solo este archivo necesita Compose para la conversión. */
fun ColorDeJuego.aColorCompose(): Color = when (this) {
    ColorDeJuego.ROJO_FUEGO -> Color(0xFFD8432A)
    ColorDeJuego.VERDE_HOJA -> Color(0xFF3F8F3A)
    ColorDeJuego.ESMERALDA -> Color(0xFF0F9B6C)
    ColorDeJuego.RUBI -> Color(0xFFA3123A)
    ColorDeJuego.ZAFIRO -> Color(0xFF2056B0)
    ColorDeJuego.NEUTRO -> CompanionColors.SurfaceHigh
}

/** Entrenador, ID, dinero, tiempo, medallas y Pokédex (ver el encargo, punto 4). */
@Composable
fun CabeceraDeJuego(resumen: ResumenGuardado, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(resumen.entrenador, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text("ID ${resumen.idDeEntrenador}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        resumen.dinero?.let { Text("$$it", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f)) }
        if (resumen.horas != null && resumen.minutos != null) {
            Text(
                com.baxner.r36scompanion.ui.texto.formatoDeHorasYMinutos(resumen.horas, resumen.minutos) + " de juego",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilaDeMedallas(ganadas = resumen.medallas ?: 0, total = resumen.medallasTotales ?: 8)
            resumen.atrapados?.let { atrapados ->
                Text(
                    "$atrapados/${resumen.vistos ?: atrapados} Pokédex",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** El equipo con sprites grandes, en filas de 3 (ver el encargo, punto 4). */
@Composable
fun EquipoDeJuego(equipo: List<PokemonCorto>, textos: Textos, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        equipo.chunked(3).forEach { fila ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                fila.forEach { pokemon -> TarjetaDePokemonDelEquipo(pokemon, textos) }
            }
        }
    }
}

@Composable
private fun TarjetaDePokemonDelEquipo(pokemon: PokemonCorto, textos: Textos) {
    val nombreDeEspecie = pokemon.especieId?.let(textos::especie) ?: pokemon.especie
    Column(modifier = Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SpriteGrande(especieId = pokemon.especieId, shiny = pokemon.shiny, tamano = 72.dp, contentDescription = nombreDeEspecie)
        Text(
            pokemon.mote ?: nombreDeEspecie,
            style = MaterialTheme.typography.labelLarge,
            color = CompanionColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        // Si el mote es el nombre por defecto (la especie en mayúsculas, como "KADABRA"), no hay
        // nada que repetir debajo (ver el encargo de esta fase, punto 1).
        if (pokemon.mote != null && !esMoteDeEspecie(pokemon.mote, nombreDeEspecie)) {
            Text(nombreDeEspecie, style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary, textAlign = TextAlign.Center, maxLines = 1)
        }
        Text("Nv ${pokemon.nivel}", style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary)
    }
}
