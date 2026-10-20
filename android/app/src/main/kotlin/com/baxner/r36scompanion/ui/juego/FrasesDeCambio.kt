package com.baxner.r36scompanion.ui.juego

import com.baxner.r36scompanion.data.respaldos.Cambio
import com.baxner.r36scompanion.data.respaldos.PokemonCorto
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.respaldos.diferencias
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.texto.formatoDeDuracionEnMinutos

/**
 * "Qué cambió" entre dos versiones de una partida, en frases (ver el encargo, punto 4: "+1
 * medalla", "Kadabra subió al Nv 54", "Entró Alakazam al equipo", "+2 h 10 min de juego", "+3
 * atrapados"). Pura (aparte de [Textos], que tampoco depende de Android: ver su propio KDoc).
 *
 * [Cambio.SubioDeNivel] y [Cambio.Evoluciono] solo traen el nombre de la especie en inglés (lo que
 * dio el motor: ver [com.baxner.r36scompanion.data.respaldos.diferencias]), sin su
 * `especieId`. Para traducirlos de todos modos, [nombreDeEspecie] busca ese nombre en el equipo
 * de [antes]/[despues] (el que sí tiene el resumen completo) y usa su id; si de plano no aparece
 * ahí (no debería pasar: el nombre viene de ese mismo resumen), se muestra el nombre en inglés
 * antes que nada en blanco.
 */
fun frasesDeCambios(antes: ResumenGuardado?, despues: ResumenGuardado?, textos: Textos): List<String> =
    diferencias(antes, despues).map { fraseDe(it, antes, despues, textos) }

private fun fraseDe(cambio: Cambio, antes: ResumenGuardado?, despues: ResumenGuardado?, textos: Textos): String = when (cambio) {
    is Cambio.Medallas -> fraseDeDiferencia(cambio.despues - cambio.antes, "medalla", "medallas")
    is Cambio.TiempoDeJuego -> "+${formatoDeDuracionEnMinutos(cambio.minutosMas)} de juego"
    is Cambio.Atrapados -> fraseDeDiferencia(cambio.despues - cambio.antes, "atrapado", "atrapados")
    is Cambio.EntroAlEquipo -> "Entró ${nombreDePokemonCorto(cambio.pokemon, textos)} al equipo"
    is Cambio.SalioDelEquipo -> "Salió ${nombreDePokemonCorto(cambio.pokemon, textos)} del equipo"
    is Cambio.SubioDeNivel -> "${nombreDeEspecie(despues, cambio.especie, textos)} subió al Nv ${cambio.despues}"
    is Cambio.Evoluciono -> "${nombreDeEspecie(antes, cambio.antes, textos)} evolucionó a ${nombreDeEspecie(despues, cambio.despues, textos)}"
    is Cambio.Dinero -> fraseDeDinero(cambio.despues - cambio.antes)
}

private fun fraseDeDiferencia(diferencia: Int, singular: String, plural: String): String {
    val palabra = if (diferencia == 1 || diferencia == -1) singular else plural
    return if (diferencia >= 0) "+$diferencia $palabra" else "$diferencia $palabra"
}

private fun fraseDeDinero(diferencia: Long): String = if (diferencia >= 0) "+$${diferencia}" else "-$${-diferencia}"

private fun nombreDePokemonCorto(pokemon: PokemonCorto, textos: Textos): String =
    pokemon.especieId?.let(textos::especie) ?: pokemon.especie

/** Busca [especieEnIngles] en el equipo de [resumen] para traducir su nombre por id (ver el KDoc del archivo). */
private fun nombreDeEspecie(resumen: ResumenGuardado?, especieEnIngles: String, textos: Textos): String {
    val id = resumen?.equipo?.firstOrNull { it.especie == especieEnIngles }?.especieId
    return id?.let(textos::especie) ?: especieEnIngles
}
