package com.baxner.r36scompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tema provisional (oscuro, con el rojo de una Poké Ball de acento). El diseño de verdad llega
 * con las pantallas de la fase 2; esto solo fija los colores para que todo lo demás los use.
 */
object CompanionColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF171B22)
    val SurfaceHigh = Color(0xFF20262F)
    val Primary = Color(0xFFE5484D)
    val Secondary = Color(0xFF3E9BFF)
    val Ok = Color(0xFF3DD68C)
    val Warning = Color(0xFFF5B74F)
    val TextPrimary = Color(0xFFEDEFF3)
    val TextSecondary = Color(0xFFA3ABB8)

    /** Marca dorada de los Pokémon shiny (el ícono de equipo en Inicio: ver `ui/sprites/`). */
    val Shiny = Color(0xFFFFD54F)

    /**
     * Medallas ganadas/faltantes (lista de Inicio y cabecera del detalle del juego, esta última
     * sobre la tarjeta de color del juego: rojo, verde, azul…). Dorado sobre cualquier fondo se ve
     * bien; antes era del mismo rojo que la tarjeta de Rojo Fuego/Rubí y casi no se distinguía (lo
     * que notó el usuario: ver el encargo de esta fase, punto 1). Las faltantes llevan un borde además
     * del relleno oscuro, para que no se confundan con el fondo cuando éste también es oscuro.
     */
    val MedallaGanada = Shiny
    val MedallaFaltante = Color(0xFF14171C)
    val MedallaFaltanteBorde = Color(0xFF545D6E)
}

private val colors = darkColorScheme(
    primary = CompanionColors.Primary,
    onPrimary = Color.White,
    secondary = CompanionColors.Secondary,
    onSecondary = Color.White,
    background = CompanionColors.Background,
    surface = CompanionColors.Surface,
    surfaceVariant = CompanionColors.SurfaceHigh,
    onBackground = CompanionColors.TextPrimary,
    onSurface = CompanionColors.TextPrimary,
    onSurfaceVariant = CompanionColors.TextSecondary,
    error = CompanionColors.Primary,
    onError = Color.White,
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
