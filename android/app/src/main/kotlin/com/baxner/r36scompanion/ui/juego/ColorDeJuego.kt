package com.baxner.r36scompanion.ui.juego

/**
 * Un color por juego para la cabecera del detalle (ver el encargo, punto 4: "Rojo Fuego rojo,
 * Verde Hoja verde, Esmeralda verde esmeralda, Rubí rojo rubí, Zafiro azul"). Enum en vez de
 * [androidx.compose.ui.graphics.Color] directamente para poder probar [colorDeJuego] con JUnit
 * puro, sin Compose; la conversión a color de verdad vive en `JuegoScreen.kt` (sí importa Compose).
 */
enum class ColorDeJuego { ROJO_FUEGO, VERDE_HOJA, ESMERALDA, RUBI, ZAFIRO, NEUTRO }

/** [versionEnIngles] es el `ResumenGuardado.version` tal cual lo da el motor (`"FireRed"`…). */
fun colorDeJuego(versionEnIngles: String?): ColorDeJuego = when (versionEnIngles) {
    "FireRed" -> ColorDeJuego.ROJO_FUEGO
    "LeafGreen" -> ColorDeJuego.VERDE_HOJA
    "Emerald" -> ColorDeJuego.ESMERALDA
    "Ruby" -> ColorDeJuego.RUBI
    "Sapphire" -> ColorDeJuego.ZAFIRO
    else -> ColorDeJuego.NEUTRO
}
