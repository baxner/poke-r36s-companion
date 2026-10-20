package com.baxner.r36scompanion.ui.diagnostico

import com.baxner.r36scompanion.data.consola.AjustesRetroArch
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasosDeDiagnosticoTest {
    private val consola = Consola("10.101.33.156", 22, "SHA256:abc", "ssh-ed25519", "rg351mp")

    @Test
    fun sinCableTodoLoDemasQuedaPendiente() {
        val pasos = pasosDeDiagnostico(HechosDeDiagnostico(EstadoDeConexion.SinCable))
        assertEquals(NivelVisual.ERROR, pasos[0].nivel) // cable
        assertEquals(NivelVisual.INFO, pasos[1].nivel) // anclaje: depende del cable
    }

    @Test
    fun conCableSinAnclajeElAnclajeEsError() {
        val pasos = pasosDeDiagnostico(HechosDeDiagnostico(EstadoDeConexion.SinAnclaje))
        assertEquals(NivelVisual.OK, pasos[0].nivel) // cable
        assertEquals(NivelVisual.ERROR, pasos[1].nivel) // anclaje
    }

    @Test
    fun anclajeActivoPeroNadieContesta() {
        val pasos = pasosDeDiagnostico(HechosDeDiagnostico(EstadoDeConexion.NoEncontrada))
        val consolaEncontrada = pasos.first { it.titulo.contains("contesta") }
        assertEquals(NivelVisual.ERROR, consolaEncontrada.nivel)
        assertTrue(consolaEncontrada.detalle!!.contains("Enable Remote Services"))
    }

    @Test
    fun huellaDistintaSeMarcaComoErrorEnSsh() {
        val pasos = pasosDeDiagnostico(
            HechosDeDiagnostico(EstadoDeConexion.HuellaDistinta(esperada = "SHA256:a", recibida = "SHA256:b")),
        )
        val ssh = pasos.first { it.titulo == "Conexión SSH" }
        assertEquals(NivelVisual.ERROR, ssh.nivel)
    }

    @Test
    fun todoConectadoYSano() {
        val hechos = HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.Conectada(consola, esNueva = false),
            carpetaGbaExiste = true,
            partidasEncontradas = 11,
            ajustesRetroArch = listOf(ajuste(cargaAutomatica = false)),
            partidasVaciasConEstadoAutomatico = emptyList(),
        )
        val pasos = pasosDeDiagnostico(hechos)
        assertTrue("ningún paso debería quedar en error", pasos.none { it.nivel == NivelVisual.ERROR })
        assertEquals(NivelVisual.OK, pasos.first { it.titulo == "Conexión SSH" }.nivel)
        assertEquals(NivelVisual.OK, pasos.first { it.titulo.contains("Partidas encontradas") }.nivel)
    }

    @Test
    fun avisaSiRetroArchRetomaDelEstadoAutomatico() {
        val hechos = HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.Conectada(consola, esNueva = false),
            carpetaGbaExiste = true,
            partidasEncontradas = 3,
            ajustesRetroArch = listOf(ajuste(cargaAutomatica = true)),
        )
        val paso = pasosDeDiagnostico(hechos).first { it.titulo.contains("retoma") }
        assertEquals(NivelVisual.AVISO, paso.nivel)
    }

    @Test
    fun avisaDePartidasVaciasConEstadoAutomatico() {
        val hechos = HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.Conectada(consola, esNueva = false),
            carpetaGbaExiste = true,
            partidasEncontradas = 3,
            partidasVaciasConEstadoAutomatico = listOf("Pokemon Edicion Verde Hoja"),
        )
        val paso = pasosDeDiagnostico(hechos).first { it.titulo.contains("avance solo en un estado") }
        assertEquals(NivelVisual.AVISO, paso.nivel)
        assertTrue(paso.detalle!!.contains("Verde Hoja"))
    }

    @Test
    fun sinCarpetaGbaEsError() {
        val hechos = HechosDeDiagnostico(
            estadoDeConexion = EstadoDeConexion.Conectada(consola, esNueva = false),
            carpetaGbaExiste = false,
        )
        val paso = pasosDeDiagnostico(hechos).first { it.titulo.contains("/roms/gba") }
        assertEquals(NivelVisual.ERROR, paso.nivel)
    }

    private fun ajuste(cargaAutomatica: Boolean?) = AjustesRetroArch(
        archivo = "/home/ark/.config/retroarch/retroarch.cfg",
        cargaEstadoAutomatico = cargaAutomatica,
        guardaEstadoAutomatico = true,
        partidasJuntoALaRom = true,
        estadosJuntoALaRom = true,
        carpetaDePartidas = null,
        carpetaDeEstados = null,
        intervaloDeAutoguardado = 0,
    )
}
