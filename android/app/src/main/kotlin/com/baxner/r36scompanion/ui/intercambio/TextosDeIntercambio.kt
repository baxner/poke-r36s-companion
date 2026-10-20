package com.baxner.r36scompanion.ui.intercambio

import com.baxner.r36scompanion.data.intercambio.Direccion
import com.baxner.r36scompanion.data.intercambio.LlegadaPrevista
import com.baxner.r36scompanion.data.intercambio.Rechazo
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.pokedex.aTexto

/*
 * Textos de la vista previa del intercambio rápido (ver el encargo, punto 4: "quién llega a dónde,
 * con qué especie, si evoluciona —Kadabra → Alakazam— y su legalidad; los rechazos con su
 * motivo"). Puros: se prueban sin Android.
 */

/** "A → B" o "B → A", con los nombres de los juegos (no sus ids). */
fun Direccion.aTexto(nombreA: String, nombreB: String): String = when (this) {
    Direccion.DE_A_HACIA_B -> "$nombreA → $nombreB"
    Direccion.DE_B_HACIA_A -> "$nombreB → $nombreA"
}

/** "Kadabra → Alakazam (evoluciona)" si evoluciona, si no solo el nombre con el que llega. */
fun LlegadaPrevista.textoDeEspecie(textos: Textos): String {
    val llegaNombre = llega.especie?.let(textos::especie) ?: "?"
    if (evolucion == null) return llegaNombre
    val saleNombre = sale.especie?.let(textos::especie) ?: "?"
    return "$saleNombre → $llegaNombre (evoluciona)"
}

/** "Legal en el juego de destino" o "El juego lo marca como ilegal ahí". */
fun LlegadaPrevista.textoDeLegalidad(): String = if (legalidad.valida) "Legal en el juego de destino" else "El juego lo marca como ilegal ahí"

/** "Caja 1, casilla 1: <motivo>" (ver el encargo, punto 4: "los rechazos con su motivo"). */
fun Rechazo.textoDeRechazo(): String = "${casilla.aTexto().replaceFirstChar(Char::titlecase)}: $motivo"
