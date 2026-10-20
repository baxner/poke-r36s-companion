package com.baxner.r36scompanion.ui.pokedex

import com.baxner.r36scompanion.data.pokedex.EntradaDePokedex
import com.baxner.r36scompanion.data.pokedex.ResumenDePokedex

/** Los cuatro filtros de la cuadrícula de la Pokédex global (ver el encargo, punto 2). */
enum class FiltroDePokedex(val etiqueta: String) {
    TODAS("Todas"),
    ME_FALTAN("Me faltan"),
    LAS_TENGO("Las tengo"),
    REGISTRADAS_PERO_YA_NO("Las tuviste"),
}

/** Pura: qué entradas se ven con cada filtro. Se prueba sin Android. */
fun FiltroDePokedex.aplicar(entradas: List<EntradaDePokedex>): List<EntradaDePokedex> = when (this) {
    FiltroDePokedex.TODAS -> entradas
    FiltroDePokedex.ME_FALTAN -> entradas.filter { !it.laTiene }
    FiltroDePokedex.LAS_TENGO -> entradas.filter { it.laTiene }
    FiltroDePokedex.REGISTRADAS_PERO_YA_NO -> entradas.filter { it.registrada && !it.laTiene }
}

/**
 * El resumen (registradas / las tienes / vistas, de 386) calculado directo de [entradas], sin
 * volver a pasar por [com.baxner.r36scompanion.data.pokedex.PokedexGlobal.resumen] (que repite
 * todo el trabajo de [com.baxner.r36scompanion.data.pokedex.PokedexGlobal.entradas] por su cuenta:
 * ver su KDoc). Misma fórmula; pura y se prueba sin Android.
 */
fun resumenDe(entradas: List<EntradaDePokedex>): ResumenDePokedex = ResumenDePokedex(
    total = entradas.size,
    registradas = entradas.count { it.registrada },
    tenidas = entradas.count { it.laTiene },
    vistas = entradas.count { it.vistaEn.isNotEmpty() },
)
