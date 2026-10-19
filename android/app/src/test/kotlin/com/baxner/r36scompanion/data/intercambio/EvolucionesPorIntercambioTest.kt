package com.baxner.r36scompanion.data.intercambio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvolucionesPorIntercambioTest {
    private val sinObjeto = 0
    private val restos = 200

    @Test
    fun losCuatroQueEvolucionanSoloPorIntercambio() {
        assertEquals(65, EvolucionesPorIntercambio.alRecibir(64, restos)!!.hacia) // Kadabra con Restos → Alakazam
        assertEquals(68, EvolucionesPorIntercambio.alRecibir(67, 182)!!.hacia) // Machoke con Repartir Exp → Machamp
        assertEquals(76, EvolucionesPorIntercambio.alRecibir(75, sinObjeto)!!.hacia)
        assertEquals(94, EvolucionesPorIntercambio.alRecibir(93, sinObjeto)!!.hacia)
        assertFalse("no gastan el objeto que llevan", EvolucionesPorIntercambio.alRecibir(64, restos)!!.gastaObjeto)
    }

    @Test
    fun losQueNecesitanUnObjetoEquipado() {
        assertNull("Seadra sin Escamadragón no evoluciona", EvolucionesPorIntercambio.alRecibir(117, sinObjeto))
        val kingdra = EvolucionesPorIntercambio.alRecibir(117, EvolucionesPorIntercambio.Escamadragon)!!
        assertEquals(230, kingdra.hacia)
        assertTrue(kingdra.gastaObjeto)
        assertEquals(listOf(EvolucionesPorIntercambio.Escamadragon), EvolucionesPorIntercambio.objetosQueLaHacenEvolucionar(117))
    }

    @Test
    fun clamperlDependeDelObjeto() {
        assertEquals(367, EvolucionesPorIntercambio.alRecibir(366, EvolucionesPorIntercambio.DienteMarino)!!.hacia)
        assertEquals(368, EvolucionesPorIntercambio.alRecibir(366, EvolucionesPorIntercambio.EscamaMarina)!!.hacia)
        assertNull(EvolucionesPorIntercambio.alRecibir(366, sinObjeto))
    }

    @Test
    fun laPiedraEternaYLosHuevosNoEvolucionan() {
        assertNull(EvolucionesPorIntercambio.alRecibir(64, EvolucionesPorIntercambio.PiedraEterna))
        assertNull(EvolucionesPorIntercambio.alRecibir(64, sinObjeto, esHuevo = true))
    }

    @Test
    fun losDemasNoEvolucionan() {
        assertNull(EvolucionesPorIntercambio.alRecibir(6, restos)) // Charizard
        assertNull(EvolucionesPorIntercambio.alRecibir(65, sinObjeto)) // Alakazam ya evolucionado
        assertTrue(EvolucionesPorIntercambio.objetosQueLaHacenEvolucionar(64).isEmpty())
    }
}
