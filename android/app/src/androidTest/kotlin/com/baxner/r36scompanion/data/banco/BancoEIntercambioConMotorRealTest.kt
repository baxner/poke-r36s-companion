package com.baxner.r36scompanion.data.banco

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.EditorDePartidasConMotor
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.intercambio.IntercambiosConMotor
import com.baxner.r36scompanion.data.intercambio.OfertaDeIntercambio
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.MotorWebView
import com.baxner.r36scompanion.data.respaldos.Reloj
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Banco e intercambio rápido con el motor PKHeX de verdad, sobre la muestra local del Rojo Fuego del
 * usuario (`androidTest/assets/muestras/rojo-fuego.srm`, no versionada: sin ella se salta).
 */
@RunWith(AndroidJUnit4::class)
class BancoEIntercambioConMotorRealTest {
    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val motorPartidas = MotorPartidas(MotorWebView.obtener(instrumentacion.targetContext))
    private val db = Room.inMemoryDatabaseBuilder(instrumentacion.targetContext, BancoDatabase::class.java).build()
    private val banco = BancoConMotor(db, motorPartidas, Reloj { 1_760_000_000_000L })
    private val editor = EditorDePartidasConMotor(motorPartidas)

    @After
    fun cerrar() = db.close()

    private fun muestra(): ByteArray {
        val bytes = runCatching { instrumentacion.context.assets.open("muestras/rojo-fuego.srm").use { it.readBytes() } }.getOrNull()
        assumeTrue("sin muestra local", bytes != null)
        return bytes!!
    }

    @Test
    fun depositarYRetirarDevuelvenElMismoPokemon() = runBlocking {
        val original = muestra()
        val spearow = Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0)

        val deposito = banco.prepararDeposito(juegoId = 1, partida = original, nombreDeArchivo = "rojo-fuego.srm", casilla = spearow)
        val cambiados = original.indices.count { original[it] != deposito.partidaNueva[it] }
        Log.i(Etiqueta, "depósito: ${cambiados} bytes cambiados")
        val sinEl = editor.abrir(deposito.partidaNueva, "x.srm")
        assertEquals(76, sinEl.cajas().sumOf { it.pokemon.size })
        banco.confirmar(deposito)

        val enBanco = banco.pokemon().first().single()
        Log.i(Etiqueta, "en el banco: especie ${enBanco.especie} Nv${enBanco.nivel} '${enBanco.mote}', ${enBanco.bytes.size} bytes, legal ${enBanco.legalidad?.valida}")
        assertEquals(21, enBanco.especie) // Spearow
        assertEquals(EstadoEnBanco.EN_BANCO, enBanco.estado)

        val retiro = banco.prepararRetiro(enBanco.id, juegoId = 1, partida = deposito.partidaNueva, nombreDeArchivo = "x.srm")
        Log.i(Etiqueta, "retiro a ${retiro.casilla}, legal en destino ${retiro.legalidadEnDestino}, cambios ${retiro.cambios}")
        val conEl = editor.abrir(retiro.partidaNueva, "y.srm")
        assertEquals(77, conEl.cajas().sumOf { it.pokemon.size })
        val devuelto = conEl.detalle(retiro.casilla)
        assertEquals(21, devuelto.especie)
        assertEquals(true, devuelto.legalidad?.valida)
        banco.confirmar(retiro)
        assertTrue(banco.pokemon().first().isEmpty())
    }

    @Test
    fun intercambioConEvolucionKadabraLlegaComoAlakazam() = runBlocking {
        val a = muestra()
        val b = a.copyOf()
        val intercambios = IntercambiosConMotor(motorPartidas)
        val oferta = OfertaDeIntercambio(
            envia = listOf(Casilla(OrigenDeCasilla.EQUIPO, 0)), // Kadabra
            recibe = listOf(Casilla(OrigenDeCasilla.CAJA, posicion = 1, caja = 0)),
        )
        val vista = intercambios.vistaPrevia(a, "a.srm", b, "b.srm", oferta)
        vista.llegadas.forEach { Log.i(Etiqueta, "llegada ${it.direccion}: ${it.sale.especie} → ${it.llega.especie} legal ${it.legalidad.valida} evolución ${it.evolucion}") }
        assertTrue("rechazos: ${vista.rechazos}", vista.sePuede)
        assertEquals(65, vista.llegadas.single { it.sale.especie == 64 }.llega.especie)

        val partidas = intercambios.intercambiar(a, "a.srm", b, "b.srm", oferta)
        val enB = editor.abrir(partidas.partidaB, "b2.srm")
        val alakazam = enB.cajas().flatMap { it.pokemon }.firstOrNull { it.especie == 65 }
        assertNotNull("en B tiene que haber un Alakazam", alakazam)
        assertEquals(true, enB.detalle(alakazam!!.casilla).legalidad?.valida)
    }

    private companion object {
        const val Etiqueta = "BancoEIntercambioReal"
    }
}
