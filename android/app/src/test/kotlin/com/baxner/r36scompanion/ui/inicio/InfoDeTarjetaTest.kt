package com.baxner.r36scompanion.ui.inicio

import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tarjeta de conexión de Inicio, por cada [EstadoDeConexion] (ver el encargo, punto 3). */
class InfoDeTarjetaTest {
    private val consola = Consola(host = "10.101.33.156", puerto = 22, huella = "SHA256:abc", algoritmoDeLlave = "ssh-ed25519", nombreDeEquipo = "rg351mp")

    @Test
    fun sinCableEsInformativoSinAccion() {
        val info = infoDeTarjeta(EstadoDeConexion.SinCable)
        assertEquals(NivelVisual.INFO, info.nivel)
        assertEquals(AccionDeTarjeta.NINGUNA, info.accion)
        assertTrue(info.cuerpo.contains("OTG"))
    }

    @Test
    fun sinAnclajeOfreceActivarlo() {
        val info = infoDeTarjeta(EstadoDeConexion.SinAnclaje)
        assertEquals(NivelVisual.AVISO, info.nivel)
        assertEquals(AccionDeTarjeta.ACTIVAR_ANCLAJE, info.accion)
    }

    @Test
    fun buscandoEsSoloInformativo() {
        assertEquals(NivelVisual.INFO, infoDeTarjeta(EstadoDeConexion.Buscando).nivel)
    }

    @Test
    fun noEncontradaExplicaRemoteServicesYOfreceReintentar() {
        val info = infoDeTarjeta(EstadoDeConexion.NoEncontrada)
        assertEquals(NivelVisual.AVISO, info.nivel)
        assertEquals(AccionDeTarjeta.REINTENTAR, info.accion)
        assertTrue(info.cuerpo.contains("Enable Remote Services"))
    }

    @Test
    fun conectadaNuevaAvisaQueLaRecordara() {
        val info = infoDeTarjeta(EstadoDeConexion.Conectada(consola, esNueva = true))
        assertEquals(NivelVisual.OK, info.nivel)
        assertEquals("rg351mp", info.titulo)
        assertTrue(info.cuerpo.contains("nueva"))
    }

    @Test
    fun conectadaConocidaNoRepiteQueEsNueva() {
        val info = infoDeTarjeta(EstadoDeConexion.Conectada(consola, esNueva = false))
        assertEquals(NivelVisual.OK, info.nivel)
        assertTrue(!info.cuerpo.contains("nueva"))
    }

    @Test
    fun huellaDistintaEsErrorConConfirmacion() {
        val info = infoDeTarjeta(EstadoDeConexion.HuellaDistinta(esperada = "SHA256:vieja", recibida = "SHA256:otra"))
        assertEquals(NivelVisual.ERROR, info.nivel)
        assertEquals(AccionDeTarjeta.CONFIRMAR_HUELLA, info.accion)
        assertTrue(info.titulo.contains("no es la tuya"))
    }

    @Test
    fun credencialesIncorrectasLlevaAAjustes() {
        val info = infoDeTarjeta(EstadoDeConexion.CredencialesIncorrectas)
        assertEquals(NivelVisual.ERROR, info.nivel)
        assertEquals(AccionDeTarjeta.IR_A_AJUSTES, info.accion)
    }

    @Test
    fun falloMuestraElMensajeYOfreceReintentar() {
        val info = infoDeTarjeta(EstadoDeConexion.Fallo("no se pudo buscar la consola"))
        assertEquals(NivelVisual.ERROR, info.nivel)
        assertEquals(AccionDeTarjeta.REINTENTAR, info.accion)
        assertEquals("no se pudo buscar la consola", info.cuerpo)
    }
}
