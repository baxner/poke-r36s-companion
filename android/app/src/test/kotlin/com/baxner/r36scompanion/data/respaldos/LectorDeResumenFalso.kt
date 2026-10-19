package com.baxner.r36scompanion.data.respaldos

/**
 * [LectorDeResumen] falso: por nombre de archivo, para no tener que inventar bytes de partida
 * reales en las pruebas. Sin configurar para un nombre, se comporta como un hack que el motor no
 * reconoce (`null`), que es el caso que de verdad importa probar (ver docs/plan.md, "Lets Go
 * Eevee... no lo reconoce").
 */
class LectorDeResumenFalso : LectorDeResumen {
    private val resumenes = mutableMapOf<String, ResumenGuardado?>()
    var llamadas: Int = 0
        private set

    fun definir(nombreDeArchivo: String, resumen: ResumenGuardado?) {
        resumenes[nombreDeArchivo] = resumen
    }

    override suspend fun resumir(bytes: ByteArray, nombreDeArchivo: String): ResumenGuardado? {
        llamadas++
        return resumenes[nombreDeArchivo]
    }
}
