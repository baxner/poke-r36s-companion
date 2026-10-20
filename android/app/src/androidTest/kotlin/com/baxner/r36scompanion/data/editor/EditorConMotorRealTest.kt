package com.baxner.r36scompanion.data.editor

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baxner.r36scompanion.data.gen3.PartidaGen3
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.MotorWebView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El editor con el motor PKHeX de verdad (emulador o teléfono), sobre la muestra local del Rojo
 * Fuego del usuario (`androidTest/assets/muestras/rojo-fuego.srm`, no versionada: sin ella se salta).
 */
@RunWith(AndroidJUnit4::class)
class EditorConMotorRealTest {
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val editor = EditorDePartidasConMotor(MotorPartidas(MotorWebView.obtener(instrumentacion.targetContext)))

    private fun muestra(): ByteArray {
        val bytes = runCatching { instrumentacion.context.assets.open("muestras/rojo-fuego.srm").use { it.readBytes() } }.getOrNull()
        assumeTrue("sin muestra local", bytes != null)
        return bytes!!
    }

    @Test
    fun leeEquipoCajasEntrenadorMochilaYMedallas() = runBlocking {
        val sesion = editor.abrir(muestra(), "rojo-fuego.srm")
        val equipo = sesion.equipo()
        assertEquals(listOf(64, 148, 34, 6, 67, 117), equipo.map { it.especie }) // Kadabra … Seadra
        val cajas = sesion.cajas()
        assertEquals(14, cajas.size)
        assertEquals(77, cajas.sumOf { it.pokemon.size })
        assertEquals("Rojo", sesion.entrenador().nombre)
        assertEquals(listOf(true, true, true, true, true, false, false, false), sesion.medallas().map { it.ganada })
        val pociones = sesion.mochila().flatMap { it.objetos }.filter { it.id == 13 }
        assertTrue("la Poción (13) está en el PC", pociones.isNotEmpty())
        val kadabra = sesion.detalle(equipo[0].casilla)
        assertEquals(53, kadabra.nivel)
        assertEquals(200, kadabra.objeto) // Restos
        assertEquals(true, kadabra.legalidad?.valida)
        assertFalse(sesion.hayCambios)
    }

    @Test
    fun probarNoCambiaYAplicarSiYQuedaLegal() = runBlocking {
        val original = muestra()
        val sesion = editor.abrir(original, "rojo-fuego.srm")
        val kadabra = sesion.equipo()[0].casilla

        val prueba = sesion.probar(kadabra, CambioDePokemon(nivel = 54, mote = "PRUEBA"))
        assertEquals(54, prueba.nivel)
        assertFalse("probar no cambia la partida", sesion.hayCambios)
        assertArrayEquals(original, sesion.bytesActuales)

        val aplicado = sesion.aplicar(kadabra, CambioDePokemon(nivel = 54, mote = "PRUEBA"))
        assertEquals("PRUEBA", aplicado.mote)
        assertTrue(sesion.hayCambios)
        assertEquals(true, aplicado.legalidad?.valida)

        val revision = sesion.revisarAntesDeGuardar()
        Log.i("EditorConMotorRealTest", "revisión: $revision")
        assertTrue(revision.abre)
        assertTrue("ningún Pokémon ilegal: ${revision.pokemonIlegales}", revision.pokemonIlegales.isEmpty())

        val cambiados = original.indices.count { original[it] != sesion.bytesActuales[it] }
        Log.i("EditorConMotorRealTest", "bytes cambiados: $cambiados")
        assertTrue("cambian pocos bytes (fueron 22 en Node): $cambiados", cambiados in 1..64)
        assertNotNull("sigue siendo una partida de Gen 3 válida", PartidaGen3.leer(sesion.bytesActuales))

        sesion.deshacerTodo()
        assertArrayEquals(original, sesion.bytesActuales)
    }

    @Test
    fun dineroYMedallas() = runBlocking {
        val sesion = editor.abrir(muestra(), "rojo-fuego.srm")
        sesion.cambiarDinero(1234)
        assertEquals(1234L, sesion.entrenador().dinero)
        sesion.cambiarMedallas(List(8) { true })
        assertTrue(sesion.medallas().all { it.ganada })
        assertTrue(sesion.revisarAntesDeGuardar().abre)
    }
}
