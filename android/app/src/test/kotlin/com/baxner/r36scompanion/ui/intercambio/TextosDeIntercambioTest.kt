package com.baxner.r36scompanion.ui.intercambio

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.intercambio.Direccion
import com.baxner.r36scompanion.data.intercambio.EvolucionesPorIntercambio
import com.baxner.r36scompanion.data.intercambio.LlegadaPrevista
import com.baxner.r36scompanion.data.intercambio.Rechazo
import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class TextosDeIntercambioTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private val kadabra = Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0)

    private fun resumen(casilla: Casilla, especie: Int) =
        ResumenDePokemon(casilla = casilla, especie = especie, nivel = 30, mote = "", esShiny = false, esHuevo = false, editable = true)

    @Test
    fun `Direccion aTexto arma la flecha con los nombres de los juegos`() {
        assertEquals("Rojo Fuego → Esmeralda", Direccion.DE_A_HACIA_B.aTexto("Rojo Fuego", "Esmeralda"))
        assertEquals("Esmeralda → Rojo Fuego", Direccion.DE_B_HACIA_A.aTexto("Rojo Fuego", "Esmeralda"))
    }

    @Test
    fun `textoDeEspecie sin evolucion es solo el nombre con el que llega`() {
        val llegada = LlegadaPrevista(
            direccion = Direccion.DE_A_HACIA_B,
            sale = resumen(kadabra, 1), // Bulbasaur, solo para que no sea null
            llega = resumen(kadabra, 1),
            evolucion = null,
            cambios = emptyList(),
            cambiosEnPartidas = emptyList(),
            legalidad = Legalidad(valida = true, mensajes = emptyList()),
        )

        assertEquals("Bulbasaur", llegada.textoDeEspecie(textos))
    }

    @Test
    fun `textoDeEspecie con evolucion muestra Kadabra flecha Alakazam`() {
        val llegada = LlegadaPrevista(
            direccion = Direccion.DE_A_HACIA_B,
            sale = resumen(kadabra, 64),
            llega = resumen(kadabra, 65),
            evolucion = EvolucionesPorIntercambio.Evolucion(desde = 64, hacia = 65, objetoNecesario = null),
            cambios = emptyList(),
            cambiosEnPartidas = emptyList(),
            legalidad = Legalidad(valida = true, mensajes = emptyList()),
        )

        assertEquals("Kadabra → Alakazam (evoluciona)", llegada.textoDeEspecie(textos))
    }

    @Test
    fun `textoDeLegalidad distingue legal de ilegal`() {
        val base = LlegadaPrevista(
            direccion = Direccion.DE_A_HACIA_B,
            sale = resumen(kadabra, 64),
            llega = resumen(kadabra, 65),
            evolucion = null,
            cambios = emptyList(),
            cambiosEnPartidas = emptyList(),
            legalidad = Legalidad(valida = true, mensajes = emptyList()),
        )

        assertEquals("Legal en el juego de destino", base.textoDeLegalidad())
        assertEquals("El juego lo marca como ilegal ahí", base.copy(legalidad = Legalidad(valida = false, mensajes = listOf("x"))).textoDeLegalidad())
    }

    @Test
    fun `textoDeRechazo junta la casilla con el motivo`() {
        val rechazo = Rechazo(direccion = Direccion.DE_A_HACIA_B, casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 2, caja = 0), motivo = "no tiene la Pokédex Nacional")

        assertEquals("Caja 1, casilla 3: no tiene la Pokédex Nacional", rechazo.textoDeRechazo())
    }
}
