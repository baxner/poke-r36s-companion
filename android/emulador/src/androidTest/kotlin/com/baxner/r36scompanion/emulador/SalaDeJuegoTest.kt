package com.baxner.r36scompanion.emulador

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** El emulador de verdad (mGBA en el emulador de Android o en el teléfono) con [RomDePrueba]. */
@RunWith(AndroidJUnit4::class)
class SalaDeJuegoTest {

    private lateinit var rom: File
    private val abiertas = mutableListOf<SalaDeJuego>()

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        rom = File(contexto.cacheDir, "rom-de-prueba.gba").apply { writeBytes(RomDePrueba.bytes()) }
    }

    @After
    fun limpiar() {
        abiertas.forEach { runCatching { it.close() } }
        rom.delete()
    }

    private fun abrir(vararg partidas: ByteArray, conCable: Boolean = partidas.size == 2): SalaDeJuego =
        SalaDeJuego.abrir(partidas.map { SalaDeJuego.Juego(rom, it) }, conCable).also { abiertas += it }

    private fun esperar(segundos: Long, condicion: () -> Boolean) {
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos)
        while (!condicion()) {
            check(System.nanoTime() < limite) { "no pasó en $segundos s" }
            Thread.sleep(20)
        }
    }

    @Test
    fun unJuegoCorreYEntregaLoQueGuardo() {
        val sala = abrir(ByteArray(0))
        assertNull("sin guardar todavía", sala.partidaGuardada(0))
        sala.arrancar()
        esperar(10) { sala.vecesQueGuardo(0) > 0 }
        val partida = sala.partidaGuardada(0)!!
        // SRAM de 32 KB (la detecta mGBA al primer acceso), en blanco salvo la marca.
        assertEquals(32 * 1024, partida.size)
        assertEquals(RomDePrueba.MARCA_DE_INICIO.toByte(), partida[RomDePrueba.BYTE_DE_INICIO])
        assertEquals(0xFF.toByte(), partida[0])
        assertTrue("corre: ${sala.cuadros(0)} cuadros", sala.cuadros(0) > 10)
        val finales = sala.terminar()
        assertArrayEquals(partida, finales.single())
    }

    @Test
    fun conservaElTamanoYElRestoDeLaPartidaDeLaConsola() {
        // Como un .srm de la R36S: 128 KB, aunque el juego use menos.
        val original = ByteArray(128 * 1024) { 0xFF.toByte() }.apply {
            this[size - 1] = 0x5A
            this[40_000] = 0x33
        }
        val sala = abrir(original)
        sala.arrancar()
        esperar(10) { sala.vecesQueGuardo(0) > 0 }
        val partida = sala.terminar().single()!!
        assertEquals(original.size, partida.size)
        assertEquals(0x5A.toByte(), partida.last())
        assertEquals(0x33.toByte(), partida[40_000])
        assertEquals(RomDePrueba.MARCA_DE_INICIO.toByte(), partida[RomDePrueba.BYTE_DE_INICIO])
    }

    @Test
    fun pausaYReanuda() {
        val sala = abrir(ByteArray(0))
        sala.arrancar()
        esperar(10) { sala.cuadros(0) > 30 }
        sala.pausar()
        val enPausa = sala.cuadros(0)
        Thread.sleep(300)
        assertTrue("no avanza en pausa", sala.cuadros(0) - enPausa <= 1)
        sala.reanudar()
        esperar(5) { sala.cuadros(0) > enPausa + 30 }
    }

    @Test
    fun vaAVelocidadReal() {
        val sala = abrir(ByteArray(0))
        sala.arrancar()
        esperar(10) { sala.cuadros(0) > 10 }
        val inicio = sala.cuadros(0)
        Thread.sleep(2000)
        val porSegundo = (sala.cuadros(0) - inicio) / 2.0
        // 60 cuadros por segundo, con o sin sonido (sin sonido marca el ritmo el reloj).
        assertTrue("$porSegundo cuadros por segundo (con sonido: ${sala.conSonido})", porSegundo in 50.0..70.0)
    }

    @Test
    fun dibujaLaPantallaSinSuavizar() {
        val hilo = HandlerThread("imagenes").apply { start() }
        val lector = ImageReader.newInstance(240 * 2, 160 * 2, PixelFormat.RGBX_8888, 2)
        val pixel = AtomicReference<IntArray>()
        val llego = CountDownLatch(1)
        lector.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { imagen ->
                val plano = imagen.planes[0]
                val bufer = plano.buffer
                val en = 100 * plano.rowStride + 200 * plano.pixelStride
                pixel.set(IntArray(4) { bufer.get(en + it).toInt() and 0xFF })
                llego.countDown()
            }
        }, Handler(hilo.looper))
        try {
            val sala = abrir(ByteArray(0))
            sala.pantalla(0, lector.surface, 2)
            sala.arrancar()
            // Unos cuadros para que la ROM ya haya pintado.
            esperar(10) { sala.cuadros(0) > 20 }
            assertTrue("llegó una imagen", llego.await(5, TimeUnit.SECONDS))
            esperar(5) { pixel.get()[0] == 0xFF }
            val (r, g, b) = pixel.get()
            assertEquals("rojo", 0xFF, r)
            assertEquals("verde", 0, g)
            assertEquals("azul", 0, b)
            sala.pantalla(0, null)
        } finally {
            lector.close()
            hilo.quitSafely()
        }
    }

    @Test
    fun dosJuegosSeHablanPorElCable() {
        val sala = abrir(ByteArray(0), ByteArray(0))
        sala.arrancar()
        // Cada uno guarda lo que recibió por el cable: 0x1111 del jugador 1 y 0x2222 del 2.
        val esperado = byteArrayOf(0x11, 0x11, 0x22, 0x22)
        try {
            esperar(20) {
                (0..1).all { j -> sala.partidaGuardada(j)?.copyOfRange(0, 4)?.contentEquals(esperado) == true }
            }
        } catch (e: IllegalStateException) {
            val estado = (0..1).joinToString { j ->
                "jugador ${j + 1}: ${sala.cuadros(j)} cuadros, guardó ${sala.vecesQueGuardo(j)} veces, " +
                    sala.partidaGuardada(j)?.copyOfRange(0, 9)?.joinToString(" ") { "%02x".format(it) }
            }
            throw AssertionError("el cable no pasó los datos: $estado | ${sala.diagnostico()}", e)
        }
        assertTrue(sala.cuadros(0) > 10 && sala.cuadros(1) > 10)
        val finales = sala.terminar()
        assertEquals(2, finales.size)
        finales.forEach { assertNotNull(it); assertArrayEquals(esperado, it!!.copyOfRange(0, 4)) }
    }

    @Test
    fun sinCableNoSeHablan() {
        // Los mismos dos juegos, sin cable: se quedan esperando a la otra consola.
        val sala = abrir(ByteArray(0), ByteArray(0), conCable = false)
        sala.arrancar()
        esperar(10) { sala.vecesQueGuardo(0) > 0 && sala.vecesQueGuardo(1) > 0 }
        Thread.sleep(1000)
        for (j in 0..1) {
            assertEquals(0xFF.toByte(), sala.partidaGuardada(j)!![0])
        }
    }

    @Test
    fun cerrarSinArrancarNoTruena() {
        val sala = abrir(ByteArray(0), ByteArray(0))
        assertEquals(listOf<ByteArray?>(null, null), sala.terminar())
    }

    @Test
    fun rechazaLoQueNoEsUnaRom() {
        val basura = File(rom.parentFile, "no-es-rom.gba").apply { writeBytes(ByteArray(4096) { 7 }) }
        try {
            val error = runCatching { SalaDeJuego.abrir(listOf(SalaDeJuego.Juego(basura, ByteArray(0)))) }.exceptionOrNull()
            assertTrue("$error", error is SalaDeJuego.ErrorAlAbrir)
        } finally {
            basura.delete()
        }
    }
}
