package com.baxner.r36scompanion.data.respaldos

/** [Reloj] fijo para las pruebas: no corre solo, hay que avanzarlo a mano con [avanzar]. */
class RelojFalso(private var ahora: Long = 1_700_000_000_000L) : Reloj {
    override fun ahoraMs(): Long = ahora

    fun avanzar(ms: Long) {
        ahora += ms
    }
}
