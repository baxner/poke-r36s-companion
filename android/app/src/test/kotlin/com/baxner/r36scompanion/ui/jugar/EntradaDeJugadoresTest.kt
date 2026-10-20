package com.baxner.r36scompanion.ui.jugar

import android.view.KeyEvent
import com.baxner.r36scompanion.emulador.TeclaGba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeclasDeCrucetaTest {
    private val lado = 100f
    private val centro = 50f

    @Test
    fun centroEsZonaMuerta() {
        assertEquals(emptySet<TeclaGba>(), teclasDeCruceta(centro, centro, lado))
        assertEquals(emptySet<TeclaGba>(), teclasDeCruceta(centro + 2f, centro - 2f, lado))
    }

    @Test
    fun arribaPuro() {
        assertEquals(setOf(TeclaGba.ARRIBA), teclasDeCruceta(centro, 5f, lado))
    }

    @Test
    fun abajoPuro() {
        assertEquals(setOf(TeclaGba.ABAJO), teclasDeCruceta(centro, 95f, lado))
    }

    @Test
    fun izquierdaPura() {
        assertEquals(setOf(TeclaGba.IZQUIERDA), teclasDeCruceta(5f, centro, lado))
    }

    @Test
    fun derechaPura() {
        assertEquals(setOf(TeclaGba.DERECHA), teclasDeCruceta(95f, centro, lado))
    }

    @Test
    fun diagonalArribaDerecha() {
        assertEquals(setOf(TeclaGba.ARRIBA, TeclaGba.DERECHA), teclasDeCruceta(90f, 10f, lado))
    }

    @Test
    fun diagonalAbajoIzquierda() {
        assertEquals(setOf(TeclaGba.ABAJO, TeclaGba.IZQUIERDA), teclasDeCruceta(10f, 90f, lado))
    }

    @Test
    fun ladoNoPositivoDaVacio() {
        assertEquals(emptySet<TeclaGba>(), teclasDeCruceta(10f, 10f, 0f))
        assertEquals(emptySet<TeclaGba>(), teclasDeCruceta(10f, 10f, -5f))
    }
}

class TeclaDelMandoTest {
    @Test
    fun flechasYDpad() {
        assertEquals(TeclaGba.ARRIBA, teclaDelMando(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(TeclaGba.ABAJO, teclaDelMando(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(TeclaGba.IZQUIERDA, teclaDelMando(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TeclaGba.DERECHA, teclaDelMando(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun botonesDeMando() {
        assertEquals(TeclaGba.A, teclaDelMando(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(TeclaGba.A, teclaDelMando(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(TeclaGba.B, teclaDelMando(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(TeclaGba.B, teclaDelMando(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(TeclaGba.L, teclaDelMando(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(TeclaGba.R, teclaDelMando(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(TeclaGba.START, teclaDelMando(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(TeclaGba.SELECT, teclaDelMando(KeyEvent.KEYCODE_BUTTON_SELECT))
    }

    @Test
    fun teclado() {
        assertEquals(TeclaGba.B, teclaDelMando(KeyEvent.KEYCODE_Z))
        assertEquals(TeclaGba.A, teclaDelMando(KeyEvent.KEYCODE_X))
        assertEquals(TeclaGba.L, teclaDelMando(KeyEvent.KEYCODE_A))
        assertEquals(TeclaGba.R, teclaDelMando(KeyEvent.KEYCODE_S))
        assertEquals(TeclaGba.START, teclaDelMando(KeyEvent.KEYCODE_ENTER))
        assertEquals(TeclaGba.SELECT, teclaDelMando(KeyEvent.KEYCODE_DEL))
        assertEquals(TeclaGba.ARRIBA, teclaDelMando(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun noReconocida() {
        assertEquals(null, teclaDelMando(KeyEvent.KEYCODE_VOLUME_UP))
    }
}

class DireccionesDelEjeTest {
    @Test
    fun zonaMuertaDelStick() {
        assertEquals(emptySet<TeclaGba>(), direccionesDelEje(0.4f, -0.4f, zonaMuerta = 0.5f))
    }

    @Test
    fun stickFueraDeZonaMuerta() {
        assertEquals(setOf(TeclaGba.DERECHA), direccionesDelEje(0.9f, 0f, zonaMuerta = 0.5f))
        assertEquals(setOf(TeclaGba.IZQUIERDA), direccionesDelEje(-0.9f, 0f, zonaMuerta = 0.5f))
        assertEquals(setOf(TeclaGba.ARRIBA), direccionesDelEje(0f, -0.9f, zonaMuerta = 0.5f))
        assertEquals(setOf(TeclaGba.ABAJO), direccionesDelEje(0f, 0.9f, zonaMuerta = 0.5f))
    }

    @Test
    fun diagonalDelStick() {
        assertEquals(setOf(TeclaGba.ARRIBA, TeclaGba.DERECHA), direccionesDelEje(0.9f, -0.9f, zonaMuerta = 0.5f))
    }

    @Test
    fun crucetaSinZonaMuerta() {
        assertEquals(setOf(TeclaGba.DERECHA), direccionesDelEje(1f, 0f, zonaMuerta = 0f))
        assertEquals(emptySet<TeclaGba>(), direccionesDelEje(0f, 0f, zonaMuerta = 0f))
    }
}

class EntradaDeJugadoresTest {
    @Test
    fun laTactilSoloVaAlJugadorControlado() {
        val entrada = EntradaDeJugadores(numeroDeJugadores = 2)
        entrada.controlarConTactil(0)
        entrada.tactil(TeclaGba.A.mascara)
        assertEquals(TeclaGba.A.mascara, entrada.mascara(0))
        assertEquals(0, entrada.mascara(1))

        entrada.controlarConTactil(1)
        assertEquals(0, entrada.mascara(0))
        assertEquals(TeclaGba.A.mascara, entrada.mascara(1))
    }

    @Test
    fun conUnSoloJugadorTodosLosMandosVanAEl() {
        val entrada = EntradaDeJugadores(numeroDeJugadores = 1)
        entrada.botonDeMando(deviceId = 5, tecla = TeclaGba.A, presionada = true)
        entrada.botonDeMando(deviceId = 9, tecla = TeclaGba.B, presionada = true)
        assertEquals(TeclaGba.A.mascara or TeclaGba.B.mascara, entrada.mascara(0))
    }

    @Test
    fun conDosJugadoresElPrimerMandoQueAprietaAlgoEsElJugador1() {
        val entrada = EntradaDeJugadores(numeroDeJugadores = 2)
        // El mando 20 aprieta primero: jugador 0.
        entrada.botonDeMando(deviceId = 20, tecla = TeclaGba.START, presionada = true)
        // El mando 30 aprieta después: jugador 1.
        entrada.botonDeMando(deviceId = 30, tecla = TeclaGba.A, presionada = true)

        assertEquals(TeclaGba.START.mascara, entrada.mascara(0))
        assertEquals(TeclaGba.A.mascara, entrada.mascara(1))
    }

    @Test
    fun soltarUnBotonLoQuitaDeLaMascara() {
        val entrada = EntradaDeJugadores(numeroDeJugadores = 1)
        entrada.botonDeMando(deviceId = 1, tecla = TeclaGba.A, presionada = true)
        entrada.botonDeMando(deviceId = 1, tecla = TeclaGba.B, presionada = true)
        assertEquals(TeclaGba.A.mascara or TeclaGba.B.mascara, entrada.mascara(0))

        entrada.botonDeMando(deviceId = 1, tecla = TeclaGba.A, presionada = false)
        assertEquals(TeclaGba.B.mascara, entrada.mascara(0))
    }

    @Test
    fun ejeYCrucetaDelMismoDispositivoSeCombinanConBotones() {
        val entrada = EntradaDeJugadores(numeroDeJugadores = 1)
        entrada.botonDeMando(deviceId = 1, tecla = TeclaGba.A, presionada = true)
        entrada.ejeDeMando(deviceId = 1, direcciones = setOf(TeclaGba.DERECHA))
        entrada.crucetaDeMando(deviceId = 1, direcciones = setOf(TeclaGba.ARRIBA))

        val esperado = TeclaGba.A.mascara or TeclaGba.DERECHA.mascara or TeclaGba.ARRIBA.mascara
        assertEquals(esperado, entrada.mascara(0))
    }

    @Test
    fun unTercerMandoConDosJugadoresQuedaConElSegundo() {
        val entrada = EntradaDeJugadores(numeroDeJugadores = 2)
        entrada.botonDeMando(deviceId = 1, tecla = TeclaGba.A, presionada = true)
        entrada.botonDeMando(deviceId = 2, tecla = TeclaGba.B, presionada = true)
        entrada.botonDeMando(deviceId = 3, tecla = TeclaGba.START, presionada = true)

        assertEquals(TeclaGba.A.mascara, entrada.mascara(0))
        assertTrue((entrada.mascara(1) and TeclaGba.B.mascara) != 0)
        assertTrue((entrada.mascara(1) and TeclaGba.START.mascara) != 0)
    }
}
