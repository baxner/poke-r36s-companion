package com.baxner.r36scompanion.ui.compartido

import com.baxner.r36scompanion.data.intercambio.ErrorDeIntercambio
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura

/**
 * Explica en palabras simples lo que falló al escribir una partida en la consola (depositar,
 * retirar, restaurar). Pura: la usan Banco e Intercambio, y se prueba sin Android.
 */
fun mensajeDeErrorDeEscritura(e: Throwable): String = when (e) {
    is ErrorDeEscritura.JuegoAbierto -> "Cierra \"${e.rom}\" en la consola (sal del juego en RetroArch) e inténtalo de nuevo."
    is ErrorDeEscritura.Conflicto -> "La partida de la consola cambió desde que se leyó (¿jugaste mientras tanto?). Vuelve a intentarlo."
    else -> e.message ?: "no se pudo escribir en la consola: se perdió la conexión"
}

/** Explica los dos errores del intercambio rápido (ver el KDoc de `ErrorDeIntercambio` en `Intercambio.kt`). */
fun mensajeDeErrorDeIntercambio(e: ErrorDeIntercambio): String = when (e) {
    is ErrorDeIntercambio.SeDeshizo ->
        "No se pudo escribir la segunda partida, así que se deshizo todo: la primera partida se regresó a como " +
            "estaba. No se perdió ni se duplicó ningún Pokémon."
    is ErrorDeIntercambio.QuedoAMedias ->
        "El intercambio quedó a medias y la primera partida no se pudo regresar sola. Para no perder ni duplicar " +
            "nada, entra al historial de ese juego y restaura a mano la versión de antes del intercambio antes de " +
            "volver a intentarlo."
}
