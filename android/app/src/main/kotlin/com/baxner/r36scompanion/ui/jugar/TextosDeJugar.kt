package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar

/*
 * Textos puros de "seguir jugando" y el intercambio real (ver el encargo, puntos 3 y 4): nada de
 * Android aquí, para poder probarlos con JUnit normal.
 */

/**
 * "Guardado en el teléfono hace 5 min" / "hace 2 h" / "hace 3 días", o
 * "Aún no guardas en el teléfono" si [ultimoGuardado] es null (ver el encargo, punto 3).
 */
fun textoDeUltimoGuardadoEnElTelefono(ahoraMs: Long, ultimoGuardado: Long?): String {
    if (ultimoGuardado == null) return "Aún no guardas en el teléfono"
    return "Guardado en el teléfono hace ${textoDeHaceTiempo(ahoraMs, ultimoGuardado)}"
}

/** "5 min", "2 h" o "3 días": lo de atrás de un "hace …". Nunca negativo (si el reloj se movió, "0 min"). */
fun textoDeHaceTiempo(ahoraMs: Long, entoncesMs: Long): String {
    val minutos = ((ahoraMs - entoncesMs) / 60_000L).coerceAtLeast(0L)
    return when {
        minutos < 1 -> "un momento"
        minutos < 60 -> "$minutos min"
        minutos < 60 * 24 -> "${minutos / 60} h"
        else -> {
            val dias = minutos / (60 * 24)
            if (dias == 1L) "1 día" else "$dias días"
        }
    }
}

private val JuegosConPokedexNacionalRequerida = setOf("FireRed", "LeafGreen")
private val JuegosDeHoenn = setOf("Ruby", "Sapphire", "Emerald")

/**
 * Si el intercambio real necesita la Pokédex Nacional en Rojo Fuego/Verde Hoja (ver el encargo,
 * punto 4): pasa cuando uno de los dos juegos es Rojo Fuego/Verde Hoja y el otro es de Hoenn
 * (Rubí/Zafiro/Esmeralda). [versionA]/[versionB] son como los da el motor (`ResumenGuardado.version`).
 */
fun necesitaPokedexNacional(versionA: String?, versionB: String?): Boolean {
    val unoEsFireRedOLeafGreen = versionA in JuegosConPokedexNacionalRequerida || versionB in JuegosConPokedexNacionalRequerida
    val otroEsDeHoenn = versionA in JuegosDeHoenn || versionB in JuegosDeHoenn
    return unoEsFireRedOLeafGreen && otroEsDeHoenn
}

/** El texto amable de cada aviso de [AvisoAntesDeJugar] (ver el encargo, punto 3). */
fun mensajeDeAvisoAntesDeJugar(aviso: AvisoAntesDeJugar): String = when (aviso) {
    is AvisoAntesDeJugar.AvanceSoloEnElEstado ->
        "Si jugaste después de guardar dentro del juego, ese avance no viene: en la consola RetroArch retoma " +
            "desde el estado automático, no desde la partida. Si quieres traerlo todo, guarda dentro del juego en " +
            "la consola y vuelve a conectar."
    AvisoAntesDeJugar.PartidaVacia ->
        "Esta partida está vacía: nunca se ha guardado dentro del juego. Vas a empezar de cero en el teléfono."
    AvisoAntesDeJugar.NoReconocida ->
        "Esta partida no la reconoce la app (parece un hack): se puede jugar, pero no se va a revisar ni editar."
}
