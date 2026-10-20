package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [IntercambiosConMotor] con [MotorFalsoDeIntercambio] (ver su KDoc). */
class IntercambiosConMotorTest {
    private val casillaA = Casilla(origen = OrigenDeCasilla.CAJA, posicion = 0, caja = 0)

    private fun intercambios(motor: MotorFalsoDeIntercambio): IntercambiosConMotor = IntercambiosConMotor(MotorPartidas(motor))

    @Test
    fun `Kadabra sin objeto evoluciona a Alakazam al llegar`() = runTest {
        val motor = MotorFalsoDeIntercambio(mia = EstadoDeIntercambioDePrueba(listOf(PokemonDeIntercambioDePrueba(0, 0, especie = 64, heldItem = 0))))
        val companera = EstadoDeIntercambioDePrueba(emptyList())
        val oferta = OfertaDeIntercambio(envia = listOf(casillaA), recibe = emptyList())

        val vista = intercambios(motor).vistaPrevia(motor.mia.aBytes(), "a.sav", companera.aBytes(), "b.sav", oferta)

        val llegada = vista.llegadas.single()
        assertEquals(Direccion.DE_A_HACIA_B, llegada.direccion)
        assertEquals(65, llegada.llega.especie) // Alakazam
        assertEquals(65, llegada.evolucion!!.hacia)
        assertTrue(llegada.legalidad.valida)
        assertTrue(vista.sePuede)
    }

    @Test
    fun `Seadra con Piedra Eterna no evoluciona`() = runTest {
        val motor = MotorFalsoDeIntercambio(
            mia = EstadoDeIntercambioDePrueba(listOf(PokemonDeIntercambioDePrueba(0, 0, especie = 117, heldItem = EvolucionesPorIntercambio.PiedraEterna))),
        )
        val oferta = OfertaDeIntercambio(envia = listOf(casillaA), recibe = emptyList())

        val vista = intercambios(motor).vistaPrevia(motor.mia.aBytes(), "a.sav", EstadoDeIntercambioDePrueba().aBytes(), "b.sav", oferta)

        val llegada = vista.llegadas.single()
        assertNull("la Piedra Eterna impide la evolución por intercambio", llegada.evolucion)
        assertEquals(117, llegada.llega.especie) // sigue siendo Seadra
    }

    @Test
    fun `Seadra con Escamadragon evoluciona a Kingdra y gasta el objeto`() = runTest {
        val motor = MotorFalsoDeIntercambio(
            mia = EstadoDeIntercambioDePrueba(listOf(PokemonDeIntercambioDePrueba(0, 0, especie = 117, heldItem = EvolucionesPorIntercambio.Escamadragon))),
        )
        val oferta = OfertaDeIntercambio(envia = listOf(casillaA), recibe = emptyList())

        val intercambiados = intercambios(motor).intercambiar(motor.mia.aBytes(), "a.sav", EstadoDeIntercambioDePrueba().aBytes(), "b.sav", oferta)

        val partidaB = Puente.json.decodeFromString(EstadoDeIntercambioDePrueba.serializer(), intercambiados.partidaB.toString(Charsets.UTF_8))
        val kingdra = partidaB.pokemon.single()
        assertEquals(230, kingdra.especie) // Kingdra
        assertEquals(0, kingdra.heldItem) // la Escamadragón se gastó

        val partidaA = Puente.json.decodeFromString(EstadoDeIntercambioDePrueba.serializer(), intercambiados.partidaA.toString(Charsets.UTF_8))
        assertTrue("el Seadra salió de la partida de origen", partidaA.pokemon.isEmpty())
    }

    @Test
    fun `evolucionar en false no manda ninguna llegada con patch`() = runTest {
        val motor = MotorFalsoDeIntercambio(mia = EstadoDeIntercambioDePrueba(listOf(PokemonDeIntercambioDePrueba(0, 0, especie = 64, heldItem = 0))))
        val oferta = OfertaDeIntercambio(envia = listOf(casillaA), recibe = emptyList(), evolucionar = false)

        val vista = intercambios(motor).vistaPrevia(motor.mia.aBytes(), "a.sav", EstadoDeIntercambioDePrueba().aBytes(), "b.sav", oferta)

        assertNull(vista.llegadas.single().evolucion)
        assertEquals(64, vista.llegadas.single().llega.especie) // sigue Kadabra, no evolucionó
    }

    @Test
    fun `un rechazo deja sePuede en falso`() = runTest {
        val handle = PokemonHandleDto("box", 0, 0)
        val motor = MotorFalsoDeIntercambio(mia = EstadoDeIntercambioDePrueba(listOf(PokemonDeIntercambioDePrueba(0, 0, especie = 1)))).apply {
            rechazos = mapOf(handle to "speciesNotInGame")
        }
        val oferta = OfertaDeIntercambio(envia = listOf(casillaA), recibe = emptyList())

        val vista = intercambios(motor).vistaPrevia(motor.mia.aBytes(), "a.sav", EstadoDeIntercambioDePrueba().aBytes(), "b.sav", oferta)

        assertTrue(vista.llegadas.isEmpty())
        val rechazo = vista.rechazos.single()
        assertEquals(Direccion.DE_A_HACIA_B, rechazo.direccion)
        assertEquals("speciesNotInGame", rechazo.motivo)
        assertFalse(vista.sePuede)
    }
}
