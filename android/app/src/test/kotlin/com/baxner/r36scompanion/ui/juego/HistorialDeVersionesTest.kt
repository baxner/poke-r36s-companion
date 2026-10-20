package com.baxner.r36scompanion.ui.juego

import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.OrigenDeVersion
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorialDeVersionesTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun version(id: Long, origen: OrigenDeVersion, medallas: Int) = VersionRespaldada(
        id = id,
        juegoId = 1,
        tipo = TipoDeArchivo.PARTIDA,
        rutaEnConsola = "/roms/gba/Pokemon Edicion Rojo Fuego.srm",
        sha256 = "hash$id",
        tamano = 131_072,
        modificadoEnConsola = 1_700_000_000L,
        respaldadaEn = 1_700_000_000_000L + id,
        origen = origen,
        estadoDePartida = EstadoDePartida.CON_DATOS,
        resumen = ResumenGuardado(
            version = "FireRed",
            entrenador = "Rojo",
            idDeEntrenador = "12345/0",
            medallas = medallas,
            medallasTotales = 8,
        ),
    )

    @Test
    fun cadaVersionSeComparaConLaSiguienteMasVieja() {
        // De la más reciente a la más vieja, como las da el repositorio.
        val versiones = listOf(
            version(3, OrigenDeVersion.CONSOLA, medallas = 5),
            version(2, OrigenDeVersion.CONSOLA, medallas = 4),
            version(1, OrigenDeVersion.CONSOLA, medallas = 4),
        )
        val historial = historialDePartida(versiones, textos)
        assertEquals(listOf("+1 medalla"), historial[0].cambios)
        assertEquals(emptyList<String>(), historial[1].cambios)
        assertEquals(emptyList<String>(), historial[2].cambios) // la más vieja: no hay con qué comparar
    }

    @Test
    fun etiquetasDeOrigen() {
        assertEquals("Respaldo", etiquetaDeOrigen(OrigenDeVersion.CONSOLA))
        assertEquals("Antes de que la app escribiera", etiquetaDeOrigen(OrigenDeVersion.ANTES_DE_ESCRIBIR))
        assertEquals("Escrita por la app", etiquetaDeOrigen(OrigenDeVersion.ESCRITA_POR_LA_APP))
    }

    @Test
    fun unaSolaVersionNoTieneConQueComparar() {
        val historial = historialDePartida(listOf(version(1, OrigenDeVersion.CONSOLA, medallas = 5)), textos)
        assertEquals(1, historial.size)
        assertEquals(emptyList<String>(), historial[0].cambios)
    }

    @Test
    fun etiquetasDeTipoDeEstado() {
        assertEquals("Estado automático", etiquetaDeTipoDeEstado(TipoDeArchivo.ESTADO_AUTOMATICO))
        assertEquals("Estado", etiquetaDeTipoDeEstado(TipoDeArchivo.ESTADO))
        assertEquals("Partida", etiquetaDeTipoDeEstado(TipoDeArchivo.PARTIDA))
    }
}
