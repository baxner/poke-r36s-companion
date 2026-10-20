package com.baxner.r36scompanion.ui.editor

import com.baxner.r36scompanion.data.editor.Estadisticas

/** Los seis campos de [Estadisticas], para limitar IVs/EVs campo por campo sin repetir un `when` en cada pantalla que los edita. */
enum class CampoDeEstadistica { PS, ATAQUE, DEFENSA, ATAQUE_ESPECIAL, DEFENSA_ESPECIAL, VELOCIDAD }

fun Estadisticas.valor(campo: CampoDeEstadistica): Int = when (campo) {
    CampoDeEstadistica.PS -> ps
    CampoDeEstadistica.ATAQUE -> ataque
    CampoDeEstadistica.DEFENSA -> defensa
    CampoDeEstadistica.ATAQUE_ESPECIAL -> ataqueEspecial
    CampoDeEstadistica.DEFENSA_ESPECIAL -> defensaEspecial
    CampoDeEstadistica.VELOCIDAD -> velocidad
}

fun Estadisticas.con(campo: CampoDeEstadistica, valor: Int): Estadisticas = when (campo) {
    CampoDeEstadistica.PS -> copy(ps = valor)
    CampoDeEstadistica.ATAQUE -> copy(ataque = valor)
    CampoDeEstadistica.DEFENSA -> copy(defensa = valor)
    CampoDeEstadistica.ATAQUE_ESPECIAL -> copy(ataqueEspecial = valor)
    CampoDeEstadistica.DEFENSA_ESPECIAL -> copy(defensaEspecial = valor)
    CampoDeEstadistica.VELOCIDAD -> copy(velocidad = valor)
}

fun Estadisticas.total(): Int = ps + ataque + defensa + ataqueEspecial + defensaEspecial + velocidad

const val IvMinimo = 0
const val IvMaximo = 31
const val EvMinimoPorCampo = 0
const val EvMaximoPorCampo = 255
const val EvTotalMaximo = 510
const val DineroMinimo = 0L
const val DineroMaximo = 999_999L

/** Un IV siempre cabe en 0..31 (ver el encargo de esta fase, punto 5): no depende de los demás. */
fun limitarIv(valor: Int): Int = valor.coerceIn(IvMinimo, IvMaximo)

/**
 * Un EV en 0..255, pero además el total de los seis no puede pasar de 510 (ver el encargo de esta
 * fase, punto 5): si subir [campo] a [nuevoValor] se pasaría de 510, se limita a lo que quepa con
 * los demás EVs tal como están en [actuales] (sin tocarlos). Pura: la pantalla la llama con cada
 * dígito que el usuario escribe, antes de mandarlo a `probar`.
 */
fun limitarEv(actuales: Estadisticas, campo: CampoDeEstadistica, nuevoValor: Int): Int {
    val limitadoPorCampo = nuevoValor.coerceIn(EvMinimoPorCampo, EvMaximoPorCampo)
    val sumaDeLosDemas = actuales.total() - actuales.valor(campo)
    val maximoPorElTotal = (EvTotalMaximo - sumaDeLosDemas).coerceIn(EvMinimoPorCampo, EvMaximoPorCampo)
    return limitadoPorCampo.coerceAtMost(maximoPorElTotal)
}

fun limitarDinero(valor: Long): Long = valor.coerceIn(DineroMinimo, DineroMaximo)

/** El juego trunca el nombre a lo que quepa, no lo rechaza (ver el encargo de esta fase, punto 5: "con su máximo de letras"). */
fun limitarNombre(nombre: String, maximoDeLetras: Int): String = if (maximoDeLetras > 0) nombre.take(maximoDeLetras) else nombre
