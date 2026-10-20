package com.baxner.r36scompanion.ui.editor

/**
 * Nombres en español de los bolsillos de la mochila, por el nombre que da el motor
 * ([com.baxner.r36scompanion.data.editor.Bolsillo.nombre]: `Items`, `KeyItems`, `Balls`, `TMHMs`,
 * `Berries`, `PCItems`; ver el encargo de esta fase, punto 5 y el KDoc de `Editor.kt`).
 */
private val NombresDeBolsillos: Map<String, String> = mapOf(
    "Items" to "Objetos",
    "KeyItems" to "Objetos clave",
    "Balls" to "Poké Balls",
    "TMHMs" to "MT y MO",
    "Berries" to "Bayas",
    "PCItems" to "PC",
)

fun nombreDeBolsillo(nombre: String): String = NombresDeBolsillos[nombre] ?: nombre
