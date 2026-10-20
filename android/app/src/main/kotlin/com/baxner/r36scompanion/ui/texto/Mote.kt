package com.baxner.r36scompanion.ui.texto

/**
 * El juego pone por defecto a un Pokémon sin apodo el nombre de su especie en mayúsculas (como
 * "KADABRA"). Si [mote] es ese nombre (comparado sin importar mayúsculas/minúsculas), no hay nada
 * que repetir debajo (ver el encargo de esta fase, punto 1: ni en el equipo del detalle del juego
 * ni en el equipo/cajas del editor). Pura: ni Compose ni el motor.
 */
fun esMoteDeEspecie(mote: String?, nombreDeEspecie: String?): Boolean =
    mote != null && nombreDeEspecie != null && mote.equals(nombreDeEspecie, ignoreCase = true)
