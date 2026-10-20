package com.baxner.r36scompanion.ui.editor

/**
 * Nombres en español de las medallas de Kanto (Rojo Fuego/Verde Hoja) y Hoenn
 * (Rubí/Zafiro/Esmeralda), por el nombre en inglés que da el motor (ver el encargo de esta fase,
 * punto 5: [com.baxner.r36scompanion.data.editor.Medalla.nombreEnIngles]). Las dos listas no
 * comparten ningún nombre, así que un solo mapa alcanza sin saber de antemano en qué juego está
 * el usuario. Si el motor diera un nombre que no está aquí (otra generación, un hack), se muestra tal
 * cual: mejor eso que una pantalla en blanco.
 */
private val NombresDeMedallas: Map<String, String> = mapOf(
    // Kanto: Rojo Fuego / Verde Hoja.
    "Boulder" to "Roca",
    "Cascade" to "Cascada",
    "Thunder" to "Trueno",
    "Rainbow" to "Arcoíris",
    "Soul" to "Alma",
    "Marsh" to "Pantano",
    "Volcano" to "Volcán",
    "Earth" to "Tierra",
    // Hoenn: Rubí / Zafiro / Esmeralda.
    "Stone" to "Piedra",
    "Knuckle" to "Puño",
    "Dynamo" to "Dinamo",
    "Heat" to "Calor",
    "Balance" to "Equilibrio",
    "Feather" to "Pluma",
    "Mind" to "Mente",
    "Rain" to "Lluvia",
)

fun nombreDeMedalla(nombreEnIngles: String): String = NombresDeMedallas[nombreEnIngles] ?: nombreEnIngles
