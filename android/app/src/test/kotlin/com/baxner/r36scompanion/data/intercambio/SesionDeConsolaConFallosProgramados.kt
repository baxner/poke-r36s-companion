package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.consola.SesionDeConsola

/**
 * Envuelve una [SesionDeConsola] de verdad (en las pruebas, [com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa])
 * para hacer fallar `escribirSeguro` en una [ruta] concreta en su enésimo intento (1-based), sin
 * tocar los demás. Hace falta porque [com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa.falloEnEscribirSeguro]
 * falla SIEMPRE que está puesto, sin importar la ruta ni el intento — no basta para simular "la
 * primera escritura de A funciona, la segunda (al restaurarla) falla", que es justo el caso de
 * `QuedoAMedias` en `EjecutorDeIntercambioConEscritorTest`.
 */
class SesionDeConsolaConFallosProgramados(private val delegada: SesionDeConsola) : SesionDeConsola by delegada {
    private val intentosPorRuta = mutableMapOf<String, Int>()

    /** `ruta -> (intento 1-based -> fallo)`. Un intento sin entrada no falla. */
    var fallos: Map<String, Map<Int, () -> Throwable>> = emptyMap()

    override suspend fun escribirSeguro(ruta: String, bytes: ByteArray) {
        val intento = (intentosPorRuta[ruta] ?: 0) + 1
        intentosPorRuta[ruta] = intento
        fallos[ruta]?.get(intento)?.let { throw it() }
        delegada.escribirSeguro(ruta, bytes)
    }
}
