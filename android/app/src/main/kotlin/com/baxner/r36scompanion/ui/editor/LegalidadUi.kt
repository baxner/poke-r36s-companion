package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad

/**
 * El resumen en español de una [Legalidad] para su insignia (ver el encargo de esta fase, punto
 * 5: "✅ legal / ⚠️ con el motivo resumido en español"). Los mensajes de PKHeX vienen en inglés
 * (ver el KDoc de `Legalidad` en `Editor.kt`) y son demasiados y demasiado técnicos para traducir
 * uno por uno; en vez de eso se busca una palabra clave conocida en el primer mensaje y se cambia
 * por una frase corta en español. Si no se reconoce ninguna, un resumen genérico ("no es posible
 * en el juego real") sigue siendo mejor que mostrar el inglés tal cual. Pura.
 */
fun resumenDeLegalidad(legalidad: Legalidad?): String = when {
    legalidad == null -> "Sin revisar"
    legalidad.valida -> "Legal"
    else -> legalidad.mensajes.firstOrNull()?.let(::resumirMensaje) ?: MensajeGenerico
}

private const val MensajeGenerico = "no sería posible en el juego real"

private val PatronesConocidos: List<Pair<Regex, String>> = listOf(
    Regex("level|nivel", RegexOption.IGNORE_CASE) to "el nivel no cuadra con su especie y origen",
    Regex("move|movimiento", RegexOption.IGNORE_CASE) to "tiene un movimiento que no podría aprender",
    Regex("abilit|habilidad", RegexOption.IGNORE_CASE) to "su habilidad no coincide con su especie",
    Regex("egg|huevo", RegexOption.IGNORE_CASE) to "no podría venir de un huevo así",
    Regex("ball|bola", RegexOption.IGNORE_CASE) to "no podría haberse atrapado en esa Poké Ball",
    Regex("hidden power|\\biv", RegexOption.IGNORE_CASE) to "sus IVs no cuadran con sus otros datos",
    Regex("trade|evolution|intercambio|evoluci", RegexOption.IGNORE_CASE) to "esa evolución necesita un intercambio",
    Regex("shiny", RegexOption.IGNORE_CASE) to "no podría ser shiny así",
    Regex("met|location|captura|lugar", RegexOption.IGNORE_CASE) to "el lugar o nivel de captura no cuadra",
    Regex("nickname|nombre|mote", RegexOption.IGNORE_CASE) to "el mote no es válido para este idioma",
)

private fun resumirMensaje(mensaje: String): String =
    PatronesConocidos.firstOrNull { (patron, _) -> patron.containsMatchIn(mensaje) }?.second ?: MensajeGenerico

/**
 * La legalidad de [casilla] a partir del mapa que arma [EditorViewModel] con
 * `revisarAntesDeGuardar()` (solo trae las ilegales: ver su KDoc en `Editor.kt`). `null` en
 * [mapaDeIlegales] significa que todavía no se pudo revisar (la partida editada no abre, o no se
 * ha pedido la revisión); una [casilla] que no está en el mapa, pero el mapa sí existe, es legal.
 */
fun legalidadDe(mapaDeIlegales: Map<Casilla, Legalidad>?, casilla: Casilla): Legalidad? =
    mapaDeIlegales?.let { it[casilla] ?: Legalidad(valida = true, mensajes = emptyList()) }
