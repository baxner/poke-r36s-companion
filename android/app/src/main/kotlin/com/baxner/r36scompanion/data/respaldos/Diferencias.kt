package com.baxner.r36scompanion.data.respaldos

/**
 * Qué cambió entre dos resúmenes de la misma partida (ver Respaldos.kt, [Cambio]). Pura: solo
 * compara los campos de [ResumenGuardado], sin tocar la consola ni el motor.
 *
 * Si falta alguno de los dos resúmenes (primera versión respaldada, o el motor dejó de reconocer
 * la partida) no hay con qué comparar: lista vacía. No se inventa una línea base en ceros porque
 * mostraría como "cambios" cosas como "5 medallas nuevas" o "10 Pokémon nuevos en el equipo" en
 * el primer respaldo, que no son un cambio real, solo el estado inicial.
 *
 * El equipo se compara por posición: [ResumenGuardado.equipo] es el orden real del equipo del
 * juego, y esa posición es lo único que relaciona una entrada de "antes" con una de "después". En
 * una misma posición, el mismo mote o un nivel que no bajó se toma como "sigue siendo el mismo
 * Pokémon" (subió de nivel, o evolucionó si además cambió de especie); si no, se toma como que
 * uno salió del equipo y otro distinto entró ahí.
 */
fun diferencias(antes: ResumenGuardado?, despues: ResumenGuardado?): List<Cambio> {
    if (antes == null || despues == null) return emptyList()

    val cambios = mutableListOf<Cambio>()

    if (antes.medallas != null && despues.medallas != null && antes.medallas != despues.medallas) {
        cambios += Cambio.Medallas(antes.medallas, despues.medallas)
    }

    minutosDeJuego(antes)?.let { minutosAntes ->
        minutosDeJuego(despues)?.let { minutosDespues ->
            val diferencia = minutosDespues - minutosAntes
            if (diferencia > 0) cambios += Cambio.TiempoDeJuego(diferencia)
        }
    }

    if (antes.atrapados != null && despues.atrapados != null && antes.atrapados != despues.atrapados) {
        cambios += Cambio.Atrapados(antes.atrapados, despues.atrapados)
    }

    cambios += diferenciasDeEquipo(antes.equipo, despues.equipo)

    if (antes.dinero != null && despues.dinero != null && antes.dinero != despues.dinero) {
        cambios += Cambio.Dinero(antes.dinero, despues.dinero)
    }

    return cambios
}

private fun diferenciasDeEquipo(antes: List<PokemonCorto>, despues: List<PokemonCorto>): List<Cambio> {
    val cambios = mutableListOf<Cambio>()
    val maximo = maxOf(antes.size, despues.size)
    for (posicion in 0 until maximo) {
        val previo = antes.getOrNull(posicion)
        val actual = despues.getOrNull(posicion)
        when {
            previo == null && actual != null -> cambios += Cambio.EntroAlEquipo(actual)
            previo != null && actual == null -> cambios += Cambio.SalioDelEquipo(previo)
            previo != null && actual != null -> cambios += diferenciaDePosicion(previo, actual)
        }
    }
    return cambios
}

/** Mismo mote, o el nivel no bajó: es el mismo Pokémon en esa posición (ver KDoc de [diferencias]). */
private fun diferenciaDePosicion(previo: PokemonCorto, actual: PokemonCorto): List<Cambio> {
    val siguenSiendoElMismo = previo.mote == actual.mote || actual.nivel >= previo.nivel
    if (!siguenSiendoElMismo) return listOf(Cambio.SalioDelEquipo(previo), Cambio.EntroAlEquipo(actual))

    return when {
        previo.especie != actual.especie -> listOf(Cambio.Evoluciono(previo.especie, actual.especie))
        actual.nivel > previo.nivel -> listOf(Cambio.SubioDeNivel(previo.especie, previo.nivel, actual.nivel))
        else -> emptyList()
    }
}

/** Minutos totales de juego, o null si el motor no dio horas y minutos (los segundos solos no bastan). */
private fun minutosDeJuego(resumen: ResumenGuardado): Int? {
    val horas = resumen.horas ?: return null
    val minutos = resumen.minutos ?: return null
    return horas * 60 + minutos + (resumen.segundos ?: 0) / 60
}
