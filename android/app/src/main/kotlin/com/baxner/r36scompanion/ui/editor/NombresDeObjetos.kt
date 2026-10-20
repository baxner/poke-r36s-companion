package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.textos.Textos

/*
 * Pequeñas envolturas sobre `Textos` para los campos del editor que usan el 0 como "nada" (un
 * objeto equipado, un movimiento vacío) o que necesitan un texto aunque la tabla no conozca el id
 * (una especie o un objeto que el motor dio pero no está en la tabla en español: mejor mostrar el
 * número que una pantalla en blanco). Puras: ver el encargo de esta fase, punto 5.
 */

fun nombreDeEspecieEditable(id: Int?, textos: Textos): String = id?.let { textos.especie(it) } ?: "?"

fun nombreDeObjetoEquipable(id: Int, textos: Textos): String = if (id == 0) "Ninguno" else textos.objetoGen3(id) ?: "Objeto #$id"

fun nombreDeMovimientoEditable(id: Int, textos: Textos): String = if (id == 0) "(Ninguno)" else textos.movimiento(id) ?: "Movimiento #$id"

fun nombreDeNaturalezaEditable(id: Int, textos: Textos): String = textos.naturaleza(id) ?: "Naturaleza #$id"

fun nombreDePokeBall(id: Int, textos: Textos): String = textos.objetoGen3(id) ?: "Poké Ball #$id"

fun nombreDeObjetoDeMochila(id: Int, textos: Textos): String = textos.objetoGen3(id) ?: "Objeto #$id"
