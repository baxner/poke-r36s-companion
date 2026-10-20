package com.baxner.r36scompanion.ui.editor

/**
 * Las 12 Poké Balls de la 3.ª generación, con la misma numeración de objetos que da el motor
 * (`Textos.objetoGen3`: ver el encargo de esta fase, punto 5 y `TextosTest.objetosConLaNumeracionDeLasPartidasDeGba`).
 * El contrato del editor (`opciones(...)`) no trae una lista de Poké Balls — a diferencia de
 * especies, objetos o movimientos, que sí cambian de generación en generación, el conjunto de
 * Poké Balls de la 3.ª generación es fijo y conocido, igual que las medallas y los bolsillos de la
 * mochila (ver el KDoc de `Editor.kt`: "con tablas propias para medallas y bolsillos").
 */
val IdsDePokeBallsGen3: List<Int> = (1..12).toList()
