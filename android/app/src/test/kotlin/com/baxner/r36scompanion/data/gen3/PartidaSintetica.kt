package com.baxner.r36scompanion.data.gen3

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Una partida de Gen 3 armada a mano: secciones con datos en cero, pies correctos y sumas
 * recalculadas. La usan las pruebas de [PartidaGen3] y las de `data/banco/` (que necesitan
 * partidas reales de verdad, no solo partidas de mentiras del motor, porque `BancoConMotor` llama
 * directo a [PartidaGen3.leer] y [PartidaGen3.conCasillaVacia] sobre los bytes, sin pasar por el
 * motor para eso). Se extrajo de `PartidaGen3Test.kt` (era una clase privada de ese archivo, y
 * las pruebas de `data/banco/` están en otro paquete) sin cambiar su comportamiento.
 */
class PartidaSintetica(private val contadores: Pair<Long, Long>, private val rotacion: Int) {
    private val bytes = ByteArray(0x20000)
    private val longitudes = intArrayOf(0xF2C, 0xF80, 0xF80, 0xF80, 0xF08, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0x7D0)

    private fun inicio(ranura: Int, id: Int) = ranura * 0xE000 + ((id + rotacion) % 14) * 0x1000

    fun conPokedex(ranura: Int, atrapados: Set<Int>, vistos: Set<Int>) = apply {
        val s0 = inicio(ranura, 0)
        atrapados.forEach { e -> bytes[s0 + 0x28 + (e - 1) / 8] = (bytes[s0 + 0x28 + (e - 1) / 8].toInt() or (1 shl ((e - 1) % 8))).toByte() }
        vistos.forEach { e -> bytes[s0 + 0x5C + (e - 1) / 8] = (bytes[s0 + 0x5C + (e - 1) / 8].toInt() or (1 shl ((e - 1) % 8))).toByte() }
    }

    fun conCasilla(ranura: Int, caja: Int, casilla: Int, relleno: Int) = apply {
        val enAlmacen = 4 + (caja * 30 + casilla) * 80
        repeat(80) { i ->
            val p = enAlmacen + i
            bytes[inicio(ranura, 5 + p / 0xF80) + p % 0xF80] = relleno.toByte()
        }
    }

    /**
     * Como [conCasilla], pero con una especie y un nivel reconocibles en vez de un relleno
     * uniforme: los primeros 3 bytes de la casilla quedan en `especie` (16 bits, poco significativo
     * primero) y `nivel` (8 bits); el resto en cero. Solo lo entienden las pruebas de `data/banco/`
     * (sus motores falsos leen esos mismos bytes); el motor de verdad no lo reconocería como un
     * Pokémon real, pero a esas pruebas no les hace falta: solo les importa que
     * [PartidaGen3.conCasillaVacia] deje la casilla en cero y las sumas bien.
     */
    fun conPokemonDeCaja(ranura: Int, caja: Int, casilla: Int, especie: Int, nivel: Int) = apply {
        val contenido = ByteArray(80)
        contenido[0] = (especie and 0xFF).toByte()
        contenido[1] = ((especie shr 8) and 0xFF).toByte()
        contenido[2] = nivel.toByte()
        val enAlmacen = 4 + (caja * 30 + casilla) * 80
        contenido.forEachIndexed { i, valor ->
            val p = enAlmacen + i
            bytes[inicio(ranura, 5 + p / 0xF80) + p % 0xF80] = valor
        }
    }

    fun bytes(): ByteArray {
        for (ranura in 0..1) {
            val contador = if (ranura == 0) contadores.first else contadores.second
            for (id in 0 until 14) {
                val s = inicio(ranura, id)
                val pie = ByteBuffer.wrap(bytes, s + 0xFF4, 12).order(ByteOrder.LITTLE_ENDIAN)
                pie.putShort(id.toShort())
                pie.putShort(PartidaGen3.sumaDeComprobacion(bytes, s, longitudes[id]).toShort())
                pie.putInt(0x08012025)
                pie.putInt(contador.toInt())
            }
        }
        return bytes.copyOf()
    }
}

/** Especie (16 bits LE) y nivel (8 bits) escritos por [PartidaSintetica.conPokemonDeCaja]; 0 = casilla vacía. */
fun PartidaGen3.especieYNivelDeCaja(caja: Int, casilla: Int): Pair<Int, Int>? {
    val datos = casillaDeCaja(caja, casilla)
    val especie = (datos[0].toInt() and 0xFF) or ((datos[1].toInt() and 0xFF) shl 8)
    if (especie == 0) return null
    return especie to (datos[2].toInt() and 0xFF)
}
