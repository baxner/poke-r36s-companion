package com.baxner.r36scompanion.emulador

import java.io.ByteArrayOutputStream

/**
 * Una ROM de GBA mínima, armada aquí mismo (sin nada de Nintendo), para probar el emulador:
 *
 * 1. pinta la pantalla de rojo puro (color de fondo 0x001F, sin capas);
 * 2. escribe 0x49 ('I') en el byte 8 de la SRAM, para probar que el juego "guarda";
 * 3. se pone en modo multijugador del cable link y manda 0x1111 si es el jugador 1 o 0x2222 si
 *    es el 2 (lo revisa en cada vuelta); el jugador 1 lanza transferencias hasta recibir las dos;
 * 4. cuando ve 0x1111 y 0x2222 en SIOMULTI0 y SIOMULTI1, los guarda en los bytes 0 a 3 de la SRAM.
 *
 * Sola (sin cable) se queda en el paso 3 esperando, que es justo lo que hace un juego de verdad.
 */
object RomDePrueba {
    const val BYTE_DE_INICIO = 8
    const val MARCA_DE_INICIO = 0x49

    fun bytes(): ByteArray {
        val a = Ensamblador()
        // Pantalla roja.
        a.mov(0, 0x04000000)
        a.mov(1, 0)
        a.strh(1, 0, 0) // DISPCNT = 0
        a.mov(2, 0x05000000)
        a.mov(1, 0x1F)
        a.strh(1, 2, 0) // paleta[0] = rojo
        // La marca de inicio en la SRAM.
        a.mov(11, 0x0E000000)
        a.mov(1, MARCA_DE_INICIO)
        a.strb(1, 11, BYTE_DE_INICIO)
        // Cable en modo multijugador.
        a.add(2, 0, 0x134)
        a.mov(1, 0)
        a.strh(1, 2, 0) // RCNT = 0
        a.add(2, 0, 0x128) // r2 = SIOCNT
        a.mov(1, 0x2000)
        a.orr(1, 1, 3)
        a.strh(1, 2, 0) // SIOCNT = multijugador, 115200 baudios
        a.mov(4, 0x1100)
        a.orr(4, 4, 0x11) // r4 = 0x1111
        a.mov(5, 0x2200)
        a.orr(5, 5, 0x22) // r5 = 0x2222
        a.add(7, 0, 0x120) // r7 = SIOMULTI0
        // El papel (jugador 1 o 2) se revisa en cada vuelta: con una sola consola conectada, mGBA
        // dice "listos" y "jugador 2" hasta que llega la otra (como el hardware).
        val vuelta = a.aqui()
        a.ldrh(3, 2, 0)
        a.tst(3, 4) // ¿es el jugador 2?
        a.movReg(6, 4, Ensamblador.EQ)
        a.movReg(6, 5, Ensamblador.NE)
        a.strh(6, 2, 2) // SIOMLT_SEND
        a.ldrh(8, 7, 0)
        a.ldrh(9, 7, 2)
        a.cmpReg(8, 4)
        val noTodavia1 = a.saltoPendiente(Ensamblador.NE)
        a.cmpReg(9, 5)
        val listo = a.saltoPendiente(Ensamblador.EQ)
        a.resolver(noTodavia1)
        a.tst(3, 4)
        a.b(vuelta, Ensamblador.NE) // el jugador 2 solo espera
        a.tst(3, 8)
        a.b(vuelta, Ensamblador.EQ) // ¿todos listos?
        a.tst(3, 0x80)
        a.b(vuelta, Ensamblador.NE) // ¿ocupado?
        a.mov(10, 0x4000)
        val pausa = a.aqui()
        a.subs(10, 10, 1)
        a.b(pausa, Ensamblador.NE)
        a.orr(3, 3, 0x80)
        a.strh(3, 2, 0) // empieza la transferencia
        a.b(vuelta)
        a.resolver(listo)
        a.strb(8, 11, 0)
        a.movLsr(1, 8, 8)
        a.strb(1, 11, 1)
        a.strb(9, 11, 2)
        a.movLsr(1, 9, 8)
        a.strb(1, 11, 3)
        val fin = a.aqui()
        a.b(fin)

        val rom = ByteArray(0x1000)
        escribirPalabra(rom, 0, 0xEA00002E.toInt()) // b 0xC0
        "PR36SPRUEBA ".toByteArray().copyInto(rom, 0xA0)
        "PR36".toByteArray().copyInto(rom, 0xAC)
        "01".toByteArray().copyInto(rom, 0xB0)
        rom[0xB2] = 0x96.toByte()
        var suma = 0
        for (i in 0xA0..0xBC) suma -= rom[i]
        rom[0xBD] = (suma - 0x19).toByte()
        a.codigo().copyInto(rom, 0xC0)
        return rom
    }

    private fun escribirPalabra(destino: ByteArray, en: Int, palabra: Int) {
        for (i in 0..3) destino[en + i] = (palabra ushr (8 * i)).toByte()
    }
}

/** Lo justo de ARM (modo ARM, little endian) para [RomDePrueba]. Las direcciones son relativas a 0xC0. */
private class Ensamblador {
    private val palabras = mutableListOf<Int>()

    fun aqui(): Int = palabras.size

    fun codigo(): ByteArray {
        val salida = ByteArrayOutputStream()
        for (p in palabras) for (i in 0..3) salida.write(p ushr (8 * i) and 0xFF)
        return salida.toByteArray()
    }

    private fun poner(palabra: Int) {
        palabras += palabra
    }

    /** imm8 y rotación de un inmediato de ARM. */
    private fun inmediato(valor: Int): Int {
        for (rot in 0..15) {
            val girado = Integer.rotateLeft(valor, 2 * rot)
            if (girado and 0xFF.inv() == 0) return (rot shl 8) or girado
        }
        error("el inmediato 0x${valor.toString(16)} no cabe en una instrucción")
    }

    private fun procesar(cond: Int, opcode: Int, s: Int, rn: Int, rd: Int, operando: Int, inmediato: Boolean) =
        poner((cond shl 28) or ((if (inmediato) 1 else 0) shl 25) or (opcode shl 21) or (s shl 20) or (rn shl 16) or (rd shl 12) or operando)

    fun mov(rd: Int, valor: Int) = procesar(AL, 13, 0, 0, rd, inmediato(valor), true)
    fun movReg(rd: Int, rm: Int, cond: Int = AL) = procesar(cond, 13, 0, 0, rd, rm, false)
    fun movLsr(rd: Int, rm: Int, desplazamiento: Int) = procesar(AL, 13, 0, 0, rd, (desplazamiento shl 7) or (1 shl 5) or rm, false)
    fun add(rd: Int, rn: Int, valor: Int) = procesar(AL, 4, 0, rn, rd, inmediato(valor), true)
    fun orr(rd: Int, rn: Int, valor: Int) = procesar(AL, 12, 0, rn, rd, inmediato(valor), true)
    fun subs(rd: Int, rn: Int, valor: Int) = procesar(AL, 2, 1, rn, rd, inmediato(valor), true)
    fun tst(rn: Int, valor: Int) = procesar(AL, 8, 1, rn, 0, inmediato(valor), true)
    fun cmpReg(rn: Int, rm: Int) = procesar(AL, 10, 1, rn, 0, rm, false)

    private fun mediaPalabra(carga: Boolean, rd: Int, rn: Int, desplazamiento: Int) {
        require(desplazamiento in 0..255)
        poner(
            (AL shl 28) or 0x01C000B0 or ((if (carga) 1 else 0) shl 20) or (rn shl 16) or (rd shl 12) or
                ((desplazamiento shr 4) shl 8) or (desplazamiento and 0xF),
        )
    }

    fun ldrh(rd: Int, rn: Int, desplazamiento: Int) = mediaPalabra(true, rd, rn, desplazamiento)
    fun strh(rd: Int, rn: Int, desplazamiento: Int) = mediaPalabra(false, rd, rn, desplazamiento)
    fun strb(rd: Int, rn: Int, desplazamiento: Int) = poner((AL shl 28) or 0x05C00000 or (rn shl 16) or (rd shl 12) or desplazamiento)

    fun b(destino: Int, cond: Int = AL) {
        val desde = palabras.size
        poner(salto(cond, desde, destino))
    }

    fun saltoPendiente(cond: Int): Pair<Int, Int> {
        palabras += 0
        return (palabras.size - 1) to cond
    }

    fun resolver(pendiente: Pair<Int, Int>) {
        palabras[pendiente.first] = salto(pendiente.second, pendiente.first, palabras.size)
    }

    private fun salto(cond: Int, desde: Int, destino: Int) = (cond shl 28) or (0b101 shl 25) or ((destino - desde - 2) and 0xFFFFFF)

    companion object {
        const val EQ = 0x0
        const val NE = 0x1
        const val AL = 0xE
    }
}
