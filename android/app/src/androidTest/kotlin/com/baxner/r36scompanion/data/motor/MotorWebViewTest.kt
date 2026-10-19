package com.baxner.r36scompanion.data.motor

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El motor PKHeX dentro de un WebView de verdad (emulador o teléfono). La parte con una partida
 * real usa `src/androidTest/assets/muestras/rojo-fuego.srm`, que no se versiona (son partidas
 * del usuario): sin ese archivo esa prueba se salta.
 */
@RunWith(AndroidJUnit4::class)
class MotorWebViewTest {
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val motor = MotorWebView.obtener(instrumentacion.targetContext)
    private val partidas = MotorPartidas(motor)

    @Test
    fun arrancaYCreaUnaPartidaEnBlancoDeRojoFuego() = runBlocking {
        val ms = motor.arrancar()
        Log.i(Etiqueta, "motor listo en $ms ms")
        val rojoFuego = partidas.versionesEnBlanco().first { it.name.contains("FireRed", ignoreCase = true) }
        val resumen = partidas.conPartidaEnBlanco(rojoFuego.id) { resumen() }
        assertEquals(3, resumen.partida.generation)
        assertEquals("FireRed", resumen.partida.version)
        assertTrue(resumen.equipo.isEmpty())
    }

    @Test
    fun unaPartidaRealSaleIdenticaAlExportarla() = runBlocking {
        val muestra = runCatching { instrumentacion.context.assets.open("muestras/rojo-fuego.srm").use { it.readBytes() } }.getOrNull()
        assumeTrue("sin muestra local (muestras/ no se versiona)", muestra != null)
        val (resumen, exportada) = partidas.conPartida(muestra!!, "rojo-fuego.srm") { resumen() to exportar() }
        Log.i(Etiqueta, "${resumen.partida.version}: ${resumen.equipo.joinToString { "${it.species} Nv${it.level}" }}")
        assertTrue(resumen.equipo.isNotEmpty())
        assertArrayEquals("exportar sin cambios tiene que dar el mismo archivo", muestra, exportada)
    }

    @Test
    fun unArchivoQueNoEsPartidaDaInvalidSave() = runBlocking {
        val basura = ByteArray(131_072) { 0xFF.toByte() }
        val error = runCatching { partidas.conPartida(basura, "vacia.srm") { resumen() } }.exceptionOrNull()
        assertTrue("esperaba MotorException, llegó $error", error is MotorException)
        assertEquals("invalid-save", (error as MotorException).codigo)
    }

    private companion object {
        const val Etiqueta = "MotorWebViewTest"
    }
}
