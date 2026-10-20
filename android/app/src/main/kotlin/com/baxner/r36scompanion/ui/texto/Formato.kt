package com.baxner.r36scompanion.ui.texto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "19 oct 2026, 14:30": la fecha de un respaldo, en la zona horaria del teléfono. Pura. */
fun formatoDeFechaYHora(epocaMs: Long): String = FormatoFecha.format(Instant.ofEpochMilli(epocaMs).atZone(ZoneId.systemDefault()))

private val FormatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "MX"))

/** "34 h 12 min" o "45 min" (sin segundos: para "tiempo de juego" alcanza con minutos). */
fun formatoDeHorasYMinutos(horas: Int, minutos: Int): String = if (horas > 0) "$horas h $minutos min" else "$minutos min"

/** "+2 h 10 min" a partir de un total de minutos (ver [com.baxner.r36scompanion.data.respaldos.Cambio.TiempoDeJuego]). */
fun formatoDeDuracionEnMinutos(totalDeMinutos: Int): String {
    val horas = totalDeMinutos / 60
    val minutos = totalDeMinutos % 60
    return formatoDeHorasYMinutos(horas, minutos)
}
