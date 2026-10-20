package com.baxner.r36scompanion.ui.banco

import com.baxner.r36scompanion.data.banco.EstadoEnBanco

/*
 * Textos del banco (ver el encargo, punto 3). Puros: se prueban sin Android.
 */

const val TextoConfirmarDeposito =
    "Este Pokémon saldrá de tu partida en la consola y quedará en el banco del teléfono. Cierra el juego en la " +
        "consola antes de continuar: si está abierto, RetroArch pisaría el cambio al guardar."

const val TextoConfirmarRetiro =
    "Este Pokémon saldrá del banco del teléfono y quedará en tu partida en la consola. Cierra el juego en la " +
        "consola antes de continuar."

const val TextoSinConsolaParaMover =
    "Conecta la consola para depositar o retirar: hace falta escribir la partida ahí."

/**
 * Qué pasó al conciliar un pendiente (ver [BancoViewModel.conciliar]): se decide comparando el
 * estado de antes de llamar a `Banco.conciliar` con si el Pokémon [siguePresente] en el banco
 * después (confirmar un depósito o cancelar un retiro lo dejan `EN_BANCO`; cancelar un depósito o
 * confirmar un retiro lo sacan del banco del todo).
 */
fun explicarConciliacion(estadoPrevio: EstadoEnBanco, siguePresente: Boolean): String = when (estadoPrevio) {
    EstadoEnBanco.PENDIENTE_DE_DEPOSITO ->
        if (siguePresente) "El depósito se confirmó: ya quedó en el banco." else "El depósito no llegó a pasar: sigue en tu partida, no en el banco."
    EstadoEnBanco.PENDIENTE_DE_RETIRO ->
        if (siguePresente) "El retiro no llegó a pasar: sigue en el banco." else "El retiro se confirmó: ya quedó en tu partida, no en el banco."
    EstadoEnBanco.EN_BANCO -> "No había nada pendiente que conciliar."
}
