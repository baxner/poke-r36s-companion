package com.baxner.r36scompanion.data.gen3

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lectura directa de una partida de la 3.ª generación (Rubí, Zafiro, Esmeralda, Rojo Fuego y
 * Verde Hoja), para lo poco que el motor PKHeX no expone: las banderas de la Pokédex por especie
 * (Pokédex global) y vaciar una casilla de caja (depositar en el banco). Todo lo demás pasa por
 * el motor.
 *
 * Formato (el mismo que usa PKHeX, `SAV3`): 128 KB con dos ranuras de 14 secciones de 4 KB. Cada
 * sección termina en un pie con su id (0-13), su suma de comprobación, la firma `0x08012025` y el
 * contador de guardados; el juego usa la ranura con el contador más alto. Las secciones de cada
 * ranura están rotadas: hay que buscarlas por id. Verificado el 2026-10-19 con el Rojo Fuego del
 * usuario: 14/14 sumas correctas y la Pokédex da los mismos 78 atrapados y 111 vistos que el motor.
 *
 * Inmutable: las funciones que cambian algo devuelven una copia nueva.
 */
class PartidaGen3 private constructor(private val datos: ByteArray, val ranura: Int, val contador: Long, private val inicioDeSeccion: IntArray) {

    /** Especies (número de la Pokédex Nacional, 1-386) atrapadas y vistas según la Pokédex del juego. */
    fun pokedex(): Pokedex {
        val bloque2 = inicioDeSeccion[0]
        return Pokedex(
            atrapados = banderas(bloque2 + OffsetAtrapados),
            vistos = banderas(bloque2 + OffsetVistos),
        )
    }

    /** Los 80 bytes (cifrados, tal cual los guarda el juego) de una casilla de caja. Todo ceros = vacía. */
    fun casillaDeCaja(caja: Int, casilla: Int): ByteArray {
        val offsets = offsetsDeCasilla(caja, casilla)
        return ByteArray(TamanoPokemonDeCaja) { datos[offsets[it]] }
    }

    fun casillaVacia(caja: Int, casilla: Int): Boolean = casillaDeCaja(caja, casilla).all { it == 0.toByte() }

    /**
     * Una copia de la partida con esa casilla de caja vacía (sus 80 bytes en cero, como la deja
     * el juego al soltar o mover un Pokémon) y las sumas de comprobación recalculadas. Solo
     * cambia esa casilla y las sumas de las secciones que la contienen; la otra ranura no se toca.
     */
    fun conCasillaVacia(caja: Int, casilla: Int): ByteArray {
        val copia = datos.copyOf()
        val offsets = offsetsDeCasilla(caja, casilla)
        offsets.forEach { copia[it] = 0 }
        offsets.map { (it - inicioDeRanura(ranura)) / TamanoSeccion }.distinct().forEach { numero ->
            val inicio = inicioDeRanura(ranura) + numero * TamanoSeccion
            val id = leerU16(copia, inicio + OffsetPie)
            escribirU16(copia, inicio + OffsetPie + 2, sumaDeComprobacion(copia, inicio, Longitudes[id]))
        }
        return copia
    }

    /** Las posiciones en el archivo de los 80 bytes de una casilla (puede caer entre dos secciones). */
    private fun offsetsDeCasilla(caja: Int, casilla: Int): IntArray {
        require(caja in 0 until Cajas) { "caja fuera de rango: $caja" }
        require(casilla in 0 until CasillasPorCaja) { "casilla fuera de rango: $casilla" }
        val inicioEnAlmacen = OffsetPrimeraCaja + (caja * CasillasPorCaja + casilla) * TamanoPokemonDeCaja
        return IntArray(TamanoPokemonDeCaja) { i ->
            val posicion = inicioEnAlmacen + i
            val seccion = PrimeraSeccionDeAlmacen + posicion / BytesUtilesDeAlmacen
            inicioDeSeccion[seccion] + posicion % BytesUtilesDeAlmacen
        }
    }

    private fun banderas(inicio: Int): Set<Int> = buildSet {
        for (especie in 1..EspeciesNacionales) {
            val bit = especie - 1
            if ((datos[inicio + bit / 8].toInt() shr (bit % 8)) and 1 == 1) add(especie)
        }
    }

    data class Pokedex(val atrapados: Set<Int>, val vistos: Set<Int>)

    companion object {
        const val TamanoPartida = 0x20000
        const val TamanoSeccion = 0x1000
        const val SeccionesPorRanura = 14
        const val Firma = 0x08012025L
        const val Cajas = 14
        const val CasillasPorCaja = 30
        const val TamanoPokemonDeCaja = 80
        const val EspeciesNacionales = 386

        private const val OffsetPie = 0xFF4
        private const val OffsetAtrapados = 0x28
        private const val OffsetVistos = 0x5C
        private const val PrimeraSeccionDeAlmacen = 5
        private const val BytesUtilesDeAlmacen = 0xF80
        private const val OffsetPrimeraCaja = 4

        /** Bytes de cada sección (por id) que entran en su suma de comprobación. */
        private val Longitudes = intArrayOf(0xF2C, 0xF80, 0xF80, 0xF80, 0xF08, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0xF80, 0x7D0)

        /**
         * La partida si es de la 3.ª generación y tiene al menos una ranura completa y sana (14
         * secciones con firma, ids 0-13 sin repetir y sumas correctas); null si no (vacía con
         * todo en `0xFF`, otro juego, dañada). Acepta archivos más grandes (mGBA agrega 16 bytes
         * con el reloj al final), que se conservan tal cual.
         */
        fun leer(bytes: ByteArray): PartidaGen3? {
            if (bytes.size < TamanoPartida) return null
            val ranuras = (0..1).mapNotNull { ranura -> leerRanura(bytes, ranura) }
            val activa = ranuras.maxByOrNull { it.second } ?: return null
            return PartidaGen3(bytes.copyOf(), activa.first, activa.second, activa.third)
        }

        private fun leerRanura(bytes: ByteArray, ranura: Int): Triple<Int, Long, IntArray>? {
            val inicios = IntArray(SeccionesPorRanura) { -1 }
            var contador = -1L
            for (numero in 0 until SeccionesPorRanura) {
                val inicio = inicioDeRanura(ranura) + numero * TamanoSeccion
                if (leerU32(bytes, inicio + OffsetPie + 4) != Firma) return null
                val id = leerU16(bytes, inicio + OffsetPie)
                if (id !in 0 until SeccionesPorRanura || inicios[id] != -1) return null
                if (sumaDeComprobacion(bytes, inicio, Longitudes[id]) != leerU16(bytes, inicio + OffsetPie + 2)) return null
                val suyo = leerU32(bytes, inicio + OffsetPie + 8)
                if (contador != -1L && suyo != contador) return null
                contador = suyo
                inicios[id] = inicio
            }
            return Triple(ranura, contador, inicios)
        }

        private fun inicioDeRanura(ranura: Int) = ranura * SeccionesPorRanura * TamanoSeccion

        /** Suma de 32 bits de las palabras de la sección, doblada a 16 bits (como el juego). */
        internal fun sumaDeComprobacion(bytes: ByteArray, inicio: Int, longitud: Int): Int {
            val palabras = ByteBuffer.wrap(bytes, inicio, longitud).order(ByteOrder.LITTLE_ENDIAN)
            var suma = 0L
            repeat(longitud / 4) { suma = (suma + (palabras.int.toLong() and 0xFFFFFFFFL)) and 0xFFFFFFFFL }
            return (((suma ushr 16) + (suma and 0xFFFF)) and 0xFFFF).toInt()
        }

        private fun leerU16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

        private fun leerU32(b: ByteArray, i: Int): Long =
            (leerU16(b, i).toLong()) or (leerU16(b, i + 2).toLong() shl 16)

        private fun escribirU16(b: ByteArray, i: Int, valor: Int) {
            b[i] = (valor and 0xFF).toByte()
            b[i + 1] = ((valor shr 8) and 0xFF).toByte()
        }
    }
}
