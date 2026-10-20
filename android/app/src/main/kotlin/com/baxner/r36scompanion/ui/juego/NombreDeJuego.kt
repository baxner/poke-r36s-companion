package com.baxner.r36scompanion.ui.juego

import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.juegoPorNombreDeArchivo
import com.baxner.r36scompanion.data.textos.Textos

/**
 * El nombre a mostrar de un juego: el que da el motor por su resumen si lo tiene; si no (partida
 * vacía o un hack que el motor no reconoce), el que se deduce de su nombre de archivo
 * ([juegoPorNombreDeArchivo]); si tampoco, el nombre del archivo tal cual (ver el encargo de esta
 * fase, punto 1). Pura: junta [Textos.juego] (con resumen) y [juegoPorNombreDeArchivo] (sin
 * resumen) en un solo lugar para no repetir el orden en cada pantalla que lista o abre un juego.
 */
fun nombreDeJuego(juego: JuegoRespaldado, textos: Textos): String =
    juego.resumen?.let { textos.juego(it.version) } ?: juegoPorNombreDeArchivo(juego.nombre) ?: juego.nombre
