package com.baxner.r36scompanion.ui.sprites

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.ui.theme.CompanionColors

/**
 * El sprite grande de una especie (equipo en el detalle del juego): `normal/` o `shiny/` según
 * [shiny]. Sin [especieId] (huevo, o el motor no lo dio) o sin archivo, un círculo con el número o
 * con "?" (ver [SpriteOFallback]).
 */
@Composable
fun SpriteGrande(especieId: Int?, shiny: Boolean, modifier: Modifier = Modifier, tamano: Dp = 72.dp, contentDescription: String? = null) {
    SpriteOFallback(especieId, carpeta = if (shiny) "shiny" else "normal", tamano = tamano, modifier = modifier, contentDescription = contentDescription)
}

/**
 * El ícono chico de una especie (lista de juegos en Inicio): siempre de `icono/` (no hay una
 * variante shiny de los íconos). Con [shiny], una marca dorada en la esquina — no hay sprite
 * shiny de icono que cargar, solo un adorno encima del normal.
 */
@Composable
fun IconoDeEquipo(especieId: Int?, shiny: Boolean, modifier: Modifier = Modifier, tamano: Dp = 32.dp, contentDescription: String? = null) {
    Box(modifier = modifier) {
        SpriteOFallback(especieId, carpeta = "icono", tamano = tamano, contentDescription = contentDescription)
        if (shiny) {
            Box(
                modifier = Modifier
                    .size(tamano * 0.4f)
                    .align(Alignment.TopEnd)
                    .background(CompanionColors.Shiny, CircleShape),
            )
        }
    }
}

@Composable
private fun SpriteOFallback(especieId: Int?, carpeta: String, tamano: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val bitmap = remember(especieId, carpeta) { especieId?.let { CargadorDeSprites.obtener(context, carpeta, it) } }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier.size(tamano))
    } else {
        CirculoConNumero(especieId, tamano, modifier)
    }
}

/** Círculo gris con el número de la Pokédex (o "?" si ni eso se sabe): nunca truena por un sprite que falta. */
@Composable
private fun CirculoConNumero(especieId: Int?, tamano: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(tamano).background(CompanionColors.SurfaceHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = especieId?.let { "#$it" } ?: "?",
            style = MaterialTheme.typography.labelSmall,
            color = CompanionColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
