package com.baxner.r36scompanion.data.gen3

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PartidaGen3Test {

    @Test
    fun unaPartidaVaciaODeOtroJuegoNoEsGen3() {
        assertNull(PartidaGen3.leer(ByteArray(0x20000) { 0xFF.toByte() }))
        assertNull(PartidaGen3.leer(ByteArray(0x20000)))
        assertNull(PartidaGen3.leer(ByteArray(8192)))
    }

    @Test
    fun usaLaRanuraMasRecienteYLeeSuPokedex() {
        val bytes = PartidaSintetica(contadores = 7L to 8L, rotacion = 3)
            .conPokedex(ranura = 1, atrapados = setOf(1, 4, 7, 151, 386), vistos = setOf(1, 4, 7, 25, 151, 386))
            .conPokedex(ranura = 0, atrapados = setOf(1), vistos = setOf(1))
            .bytes()
        val partida = PartidaGen3.leer(bytes)!!
        assertEquals(1, partida.ranura)
        assertEquals(8L, partida.contador)
        assertEquals(setOf(1, 4, 7, 151, 386), partida.pokedex().atrapados)
        assertEquals(setOf(1, 4, 7, 25, 151, 386), partida.pokedex().vistos)
    }

    @Test
    fun unaSumaDeComprobacionMalaInvalidaEsaRanuraPeroNoLaOtra() {
        val sintetica = PartidaSintetica(contadores = 7L to 8L, rotacion = 0)
        val bytes = sintetica.bytes()
        bytes[0xE000 + 5 * 0x1000 + 10] = 0x55 // un dato de la ranura 1 sin recalcular su suma
        val partida = PartidaGen3.leer(bytes)!!
        assertEquals(0, partida.ranura)
    }

    @Test
    fun vaciarUnaCasillaSoloCambiaEsaCasillaYLasSumas() {
        // Caja 1, casilla 19: sus 80 bytes caen entre la sección 5 y la 6 del almacén.
        val sintetica = PartidaSintetica(contadores = 3L to 2L, rotacion = 5).conCasilla(ranura = 0, caja = 1, casilla = 19, relleno = 0x5A)
        val original = sintetica.bytes()
        val partida = PartidaGen3.leer(original)!!
        assertFalse(partida.casillaVacia(1, 19))

        val vaciada = partida.conCasillaVacia(1, 19)
        val despues = PartidaGen3.leer(vaciada)
        assertNotNull("la partida tiene que seguir siendo válida", despues)
        assertTrue(despues!!.casillaVacia(1, 19))
        assertEquals(partida.pokedex(), despues.pokedex())

        val cambiados = original.indices.filter { original[it] != vaciada[it] }
        val pies = cambiados.filter { it % 0x1000 in 0xFF6..0xFF7 }
        assertEquals("solo los 80 bytes de la casilla", 80, cambiados.size - pies.size)
        assertTrue("como mucho las sumas de 2 secciones", pies.size in 1..4)
        assertTrue("la otra ranura no se toca", cambiados.all { it < 0xE000 })
    }

    @Test
    fun conservaLosBytesDelRelojDeMgbaAlFinal() {
        val reloj = ByteArray(16) { (it + 1).toByte() }
        val bytes = PartidaSintetica(contadores = 1L to 0L, rotacion = 0).conCasilla(0, 0, 0, 0x11).bytes() + reloj
        val vaciada = PartidaGen3.leer(bytes)!!.conCasillaVacia(0, 0)
        assertEquals(bytes.size, vaciada.size)
        assertArrayEquals(reloj, vaciada.copyOfRange(0x20000, vaciada.size))
    }

    @Test
    fun laPartidaRealDeRojoFuegoCoincideConElMotor() {
        val bytes = javaClass.getResourceAsStream("/muestras/rojo-fuego.srm")?.use { it.readBytes() }
        assumeTrue("sin muestra local (muestras/ no se versiona)", bytes != null)
        val partida = PartidaGen3.leer(bytes!!)!!
        assertEquals(52L, partida.contador)
        // Lo mismo que dijo el motor PKHeX de esta partida: 78 atrapados, 111 vistos.
        assertEquals(78, partida.pokedex().atrapados.size)
        assertEquals(111, partida.pokedex().vistos.size)
        assertTrue(partida.pokedex().vistos.containsAll(partida.pokedex().atrapados))
    }
}

/** Una partida de Gen 3 armada a mano: secciones con datos en cero, pies correctos y sumas recalculadas. */
private class PartidaSintetica(private val contadores: Pair<Long, Long>, private val rotacion: Int) {
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
