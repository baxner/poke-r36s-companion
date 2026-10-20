package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.Insignia
import com.baxner.r36scompanion.ui.sprites.IconoDeEquipo
import com.baxner.r36scompanion.ui.sprites.SpriteGrande
import com.baxner.r36scompanion.ui.texto.esMoteDeEspecie
import com.baxner.r36scompanion.ui.theme.CompanionColors

/** ✅/⚠️ con el motivo resumido en español, o nada mientras no se ha podido revisar (ver el encargo de esta fase, punto 5). */
@Composable
fun InsigniaDeLegalidad(legalidad: Legalidad?, modifier: Modifier = Modifier) {
    when {
        legalidad == null -> Unit
        legalidad.valida -> Insignia("✅ Legal", modifier = modifier, color = CompanionColors.Ok.copy(alpha = 0.18f), colorDeTexto = CompanionColors.Ok)
        else -> Insignia(
            "⚠️ ${resumenDeLegalidad(legalidad)}",
            modifier = modifier,
            color = CompanionColors.Warning.copy(alpha = 0.18f),
            colorDeTexto = CompanionColors.Warning,
        )
    }
}

/**
 * Una fila del equipo en el editor: sprite, mote/especie, nivel e insignia de legalidad (ver el
 * encargo de esta fase, punto 5). La insignia va en su propia línea, dentro de la columna con
 * `weight`: un motivo largo ("el nivel no cuadra con su especie y origen") no cabe junto al mote
 * en una sola fila sin aplastarlo (se vio en la primera captura: el mote desaparecía).
 */
@Composable
fun FilaDePokemonEditable(pokemon: ResumenDePokemon, textos: Textos, legalidad: Legalidad?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val nombreDeEspecie = pokemon.especie?.let(textos::especie) ?: "?"
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpriteGrande(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 48.dp, contentDescription = nombreDeEspecie)
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pokemon.mote, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text("Nv ${pokemon.nivel}", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
            }
            if (!esMoteDeEspecie(pokemon.mote, nombreDeEspecie)) {
                Text(nombreDeEspecie, style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary, maxLines = 1)
            }
            InsigniaDeLegalidad(legalidad, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Una celda de la cuadrícula de una caja: sprite chico, o vacía si no hay Pokémon en esa posición (ver el encargo de esta fase, punto 5). */
@Composable
fun CeldaDePokemonEditable(pokemon: ResumenDePokemon?, legalidad: Legalidad?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(56.dp)
            .clickable(enabled = pokemon != null, onClick = onClick)
            .background(CompanionColors.Surface, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pokemon != null) {
            IconoDeEquipo(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 32.dp)
            Text("Nv ${pokemon.nivel}", style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary, maxLines = 1, textAlign = TextAlign.Center)
            if (legalidad?.valida == false) {
                Text("⚠️", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
