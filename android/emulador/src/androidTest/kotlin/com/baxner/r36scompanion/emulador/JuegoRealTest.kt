package com.baxner.r36scompanion.emulador

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los juegos de verdad del usuario en mGBA: solo corre si están en `src/androidTest/assets/muestras/`
 * (no se versionan; se copian de la consola, ver docs/plan.md). Deja capturas en
 * `getExternalFilesDir()/capturas/` para revisarlas a ojo (`adb pull`).
 */
@RunWith(AndroidJUnit4::class)
class JuegoRealTest {

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val contexto = instrumentacion.targetContext
    private lateinit var capturas: File
    private val abiertas = mutableListOf<Sala>()
    private lateinit var hilo: HandlerThread

    @Before
    fun preparar() {
        capturas = File(contexto.getExternalFilesDir(null), "capturas").apply { deleteRecursively(); mkdirs() }
        hilo = HandlerThread("capturas").apply { start() }
    }

    @After
    fun limpiar() {
        abiertas.forEach { runCatching { it.close() } }
        hilo.quitSafely()
    }

    private fun muestra(nombre: String): File? {
        val destino = File(contexto.cacheDir, nombre)
        return runCatching {
            instrumentacion.context.assets.open("muestras/$nombre").use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
            destino
        }.getOrNull()
    }

    /** Lo último que dibujó el juego [jugador], como imagen de 240 × 160. */
    private inner class Camara(sala: Sala, jugador: Int) : AutoCloseable {
        private val lector = ImageReader.newInstance(240, 160, PixelFormat.RGBX_8888, 3)
        private val ultima = AtomicReference<Bitmap?>()

        init {
            lector.setOnImageAvailableListener({ r ->
                r.acquireLatestImage()?.use { imagen ->
                    val plano = imagen.planes[0]
                    val mapa = Bitmap.createBitmap(plano.rowStride / plano.pixelStride, 160, Bitmap.Config.ARGB_8888)
                    mapa.copyPixelsFromBuffer(plano.buffer)
                    ultima.set(Bitmap.createBitmap(mapa, 0, 0, 240, 160))
                }
            }, Handler(hilo.looper))
            sala.pantalla(jugador, lector.surface, 1)
        }

        fun guardar(nombre: String): Bitmap? {
            Thread.sleep(150)
            val mapa = ultima.get() ?: return null
            File(capturas, "$nombre.png").outputStream().use { mapa.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return mapa
        }

        override fun close() = lector.close()
    }

    private fun pulsar(sala: Sala, jugador: Int, tecla: TeclaGba, ms: Long = 120, despues: Long = 350) {
        sala.teclas(jugador, tecla.mascara)
        Thread.sleep(ms)
        sala.teclas(jugador, 0)
        Thread.sleep(despues)
    }

    /** Colores distintos en la imagen: una pantalla negra o de un solo color tiene 1. */
    private fun colores(mapa: Bitmap?): Int {
        mapa ?: return 0
        val vistos = HashSet<Int>()
        for (y in 0 until 160 step 4) for (x in 0 until 240 step 4) vistos += mapa.getPixel(x, y)
        return vistos.size
    }

    @Test
    fun rojoFuegoArrancaContinuaYGuarda() {
        val rom = muestra("Pokemon Edicion Rojo Fuego.gba")
        val partida = muestra("Pokemon Edicion Rojo Fuego.srm")
        assumeTrue("sin los juegos del usuario en assets/muestras", rom != null && partida != null)
        val original = partida!!.readBytes()

        val sala = SalaDeJuego.abrir(listOf(JuegoParaSala(rom!!, original)), conCable = false).also { abiertas += it }
        Camara(sala, 0).use { camara ->
            sala.arrancar()
            Thread.sleep(3000)
            camara.guardar("01-arranque")
            // Intro y título: START hasta el menú de "Continuar".
            repeat(8) { pulsar(sala, 0, TeclaGba.START, despues = 900) }
            camara.guardar("02-menu-principal")
            pulsar(sala, 0, TeclaGba.A, despues = 1500) // Continuar
            camara.guardar("03-continuar")
            pulsar(sala, 0, TeclaGba.A, despues = 3000) // la partida
            // "Hagamos memoria…": el repaso de Rojo Fuego al continuar; B lo salta.
            repeat(6) { pulsar(sala, 0, TeclaGba.B, despues = 700) }
            Thread.sleep(2000)
            val enElMapa = camara.guardar("04-en-el-mapa")
            assertTrue("el mapa tiene dibujo (${colores(enElMapa)} colores)", colores(enElMapa) > 8)

            // Menú → GUARDAR (el quinto: Pokédex, Pokémon, Mochila, entrenador, Guardar).
            pulsar(sala, 0, TeclaGba.START, despues = 800)
            camara.guardar("05-menu")
            repeat(4) { pulsar(sala, 0, TeclaGba.ABAJO, despues = 250) }
            camara.guardar("06-en-guardar")
            pulsar(sala, 0, TeclaGba.A, despues = 1200)
            camara.guardar("07-quieres-guardar")
            pulsar(sala, 0, TeclaGba.A, despues = 1200)
            camara.guardar("08-sobrescribir")
            pulsar(sala, 0, TeclaGba.A, despues = 300)
            val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (sala.vecesQueGuardo(0) == 0 && System.nanoTime() < limite) Thread.sleep(100)
            Thread.sleep(1500)
            camara.guardar("09-guardado")
            assertTrue("el juego guardó (${sala.diagnostico()})", sala.vecesQueGuardo(0) > 0)
        }
        val guardada = sala.terminar().single()!!
        assertEquals(original.size, guardada.size)
        assertTrue("la partida cambió", !guardada.contentEquals(original))
        File(capturas, "rojo-fuego-guardada.srm").writeBytes(guardada)
    }

    @Test
    fun rojoFuegoYVerdeHojaConCable() {
        val romA = muestra("Pokemon Edicion Rojo Fuego.gba")
        val romB = muestra("Pokemon Edicion Verde Hoja.gba")
        val partidaA = muestra("Pokemon Edicion Rojo Fuego.srm")
        val partidaB = muestra("Pokemon Edicion Verde Hoja.srm")
        assumeTrue("sin los juegos del usuario en assets/muestras", listOf(romA, romB, partidaA, partidaB).all { it != null })

        val sala = SalaDeJuego.abrir(
            listOf(JuegoParaSala(romA!!, partidaA!!.readBytes()), JuegoParaSala(romB!!, partidaB!!.readBytes())),
            conCable = true,
        ).also { abiertas += it }
        Camara(sala, 0).use { camaraA ->
            Camara(sala, 1).use { camaraB ->
                sala.arrancar()
                Thread.sleep(2000)
                repeat(8) {
                    pulsar(sala, 0, TeclaGba.START, despues = 0)
                    pulsar(sala, 1, TeclaGba.START, despues = 900)
                }
                pulsar(sala, 0, TeclaGba.A, despues = 0)
                pulsar(sala, 1, TeclaGba.A, despues = 1500)
                pulsar(sala, 0, TeclaGba.A, despues = 0)
                pulsar(sala, 1, TeclaGba.A, despues = 3000)
                repeat(6) {
                    pulsar(sala, 0, TeclaGba.B, despues = 0)
                    pulsar(sala, 1, TeclaGba.B, despues = 700)
                }
                Thread.sleep(2000)
                val a = camaraA.guardar("cable-1-rojo-fuego")
                val b = camaraB.guardar("cable-2-verde-hoja")
                val inicio = sala.cuadros(0) to sala.cuadros(1)
                Thread.sleep(3000)
                val porSegundo = (sala.cuadros(0) - inicio.first) / 3.0 to (sala.cuadros(1) - inicio.second) / 3.0
                File(capturas, "cable.txt").writeText("cuadros por segundo: $porSegundo\n${sala.diagnostico()}\n")
                // La partida de Verde Hoja del usuario está vacía: arranca una nueva (el tutorial de
                // los botones, texto sobre azul). Basta con que los dos dibujen algo.
                assertTrue("los dos dibujan (${colores(a)}, ${colores(b)})", colores(a) > 8 && colores(b) > 1)
                assertTrue("los dos van a velocidad real: $porSegundo", porSegundo.first > 50 && porSegundo.second > 50)
            }
        }
    }
}
