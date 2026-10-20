package com.baxner.r36scompanion.ui.banco

import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CajasDelBancoTest {
    private fun pokemon(id: Long, caja: Int, posicion: Int, estado: EstadoEnBanco = EstadoEnBanco.EN_BANCO) = PokemonEnBanco(
        id = id,
        bytes = ByteArray(0),
        especie = 21,
        nivel = 3,
        mote = "",
        esShiny = false,
        esHuevo = false,
        entrenadorOriginal = "ROJO",
        idDeEntrenador = 12345,
        versionDeOrigen = 4,
        legalidad = null,
        estado = estado,
        juegoDeOrigen = 1,
        casillaDeOrigen = null,
        depositadoEn = 0,
        caja = caja,
        posicion = posicion,
    )

    @Test
    fun `casillasDeLaCaja da 30 posiciones, con el Pokemon en la suya y el resto vacias`() {
        val spearow = pokemon(id = 1, caja = 0, posicion = 5)

        val casillas = casillasDeLaCaja(listOf(spearow), caja = 0)

        assertEquals(30, casillas.size)
        assertEquals(spearow, casillas[5])
        assertNull(casillas[0])
        assertNull(casillas[29])
    }

    @Test
    fun `casillasDeLaCaja ignora los pendientes - se muestran aparte`() {
        val pendiente = pokemon(id = 1, caja = 0, posicion = 0, estado = EstadoEnBanco.PENDIENTE_DE_RETIRO)

        val casillas = casillasDeLaCaja(listOf(pendiente), caja = 0)

        assertNull("un pendiente no ocupa casilla visible en la cuadricula", casillas[0])
    }

    @Test
    fun `casillasDeLaCaja solo mira la caja pedida`() {
        val enOtraCaja = pokemon(id = 1, caja = 1, posicion = 0)

        val casillas = casillasDeLaCaja(listOf(enOtraCaja), caja = 0)

        assertNull(casillas[0])
    }

    @Test
    fun `cajasConContenido siempre incluye al menos la caja 0`() {
        assertEquals(listOf(0), cajasConContenido(emptyList()))
    }

    @Test
    fun `cajasConContenido llega hasta la caja mas alta usada`() {
        val pokemonEnCajas = listOf(pokemon(1, caja = 0, posicion = 0), pokemon(2, caja = 3, posicion = 0))

        assertEquals(listOf(0, 1, 2, 3), cajasConContenido(pokemonEnCajas))
    }

    @Test
    fun `cajasConContenido ignora la caja de un pendiente para decidir el maximo`() {
        // Un pendiente no debería "inflar" la lista de cajas si no hay nada EN_BANCO ahí todavía.
        val soloPendienteEnCajaAlta = listOf(pokemon(1, caja = 5, posicion = 0, estado = EstadoEnBanco.PENDIENTE_DE_RETIRO))

        assertEquals(listOf(0), cajasConContenido(soloPendienteEnCajaAlta))
    }
}
