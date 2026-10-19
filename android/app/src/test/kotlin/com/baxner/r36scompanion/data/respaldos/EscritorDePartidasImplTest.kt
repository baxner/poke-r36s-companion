package com.baxner.r36scompanion.data.respaldos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baxner.r36scompanion.data.consola.JuegoAbierto
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [EscritorDePartidasImpl] con Room en memoria (Robolectric) y una [SesionDeConsolaFalsa]: ver el
 * KDoc de [EscritorDePartidas] en Respaldos.kt y docs/plan.md, "Reglas de seguridad para no
 * perder una partida".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EscritorDePartidasImplTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var db: RespaldosDatabase
    private lateinit var almacen: AlmacenDeBlobs
    private lateinit var lector: LectorDeResumenFalso
    private lateinit var reloj: RelojFalso
    private lateinit var repositorio: RepositorioDeRespaldosImpl
    private lateinit var escritor: EscritorDePartidasImpl
    private lateinit var sesion: SesionDeConsolaFalsa

    private val rutaPartida = "/roms/gba/Pokemon Edicion Rojo Fuego.srm"
    private val rutaEstadoAutomatico = "/roms/gba/Pokemon Edicion Rojo Fuego.state.auto"
    private val rutaEstadoManual = "/roms/gba/Pokemon Edicion Rojo Fuego.state1"
    private val bytesOriginales = "original".toByteArray()
    private val bytesEstadoAutomatico = "estado-auto".toByteArray()
    private val bytesEstadoManual = "estado-manual".toByteArray()

    @Before
    fun preparar() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RespaldosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        almacen = AlmacenDeBlobs(carpetaTemporal.newFolder("blobs"))
        lector = LectorDeResumenFalso()
        reloj = RelojFalso()
        repositorio = RepositorioDeRespaldosImpl(db, almacen, lector, reloj)
        escritor = EscritorDePartidasImpl(db, almacen, lector, reloj)
        sesion = SesionDeConsolaFalsa()
    }

    @After
    fun cerrar() {
        db.close()
    }

    /** Dos la partida, su estado automático y un estado manual ya respaldados (un `respaldarTodo` previo), como si la app llevara un tiempo corriendo. */
    private suspend fun sembrarJuego(): Long {
        sesion.ponerArchivo(rutaPartida, bytesOriginales, modificado = 1000)
        sesion.ponerArchivo(rutaEstadoAutomatico, bytesEstadoAutomatico, modificado = 1000)
        sesion.ponerArchivo(rutaEstadoManual, bytesEstadoManual, modificado = 1000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/0"))
        repositorio.respaldarTodo(sesion)
        return repositorio.juegos().first().single().id
    }

    private suspend fun intentarEscribir(juegoId: Long, bytes: ByteArray, base: String?): Exception? = try {
        escritor.escribir(sesion, juegoId, bytes, base)
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `no escribe si el juego esta abierto en la consola`() = runTest {
        val juegoId = sembrarJuego()
        sesion.juegosAbiertosSimulados = listOf(JuegoAbierto(emulador = "retroarch", nucleo = "mgba_rumble", rom = "/roms/gba/Pokemon Edicion Rojo Fuego.gba"))

        val excepcion = intentarEscribir(juegoId, "editado".toByteArray(), base = null)

        assertTrue(excepcion is ErrorDeEscritura.JuegoAbierto)
        assertArrayEquals(bytesOriginales, sesion.bytesDe(rutaPartida))
        assertTrue(sesion.existeArchivo(rutaEstadoAutomatico))
        assertEquals(1, repositorio.versiones(juegoId).first().count { it.rutaEnConsola == rutaPartida })
    }

    @Test
    fun `tambien compara por nombre base de la rom, sin importar la extension`() = runTest {
        val juegoId = sembrarJuego()
        // "ps" podría reportar otra extensión o ninguna: igual tiene que reconocerse como el mismo juego.
        sesion.juegosAbiertosSimulados = listOf(JuegoAbierto("retroarch", "mgba_rumble", rom = "Pokemon Edicion Rojo Fuego"))

        val excepcion = intentarEscribir(juegoId, "editado".toByteArray(), base = null)

        assertTrue(excepcion is ErrorDeEscritura.JuegoAbierto)
    }

    @Test
    fun `no escribe si la partida de la consola ya no es la que se esperaba`() = runTest {
        val juegoId = sembrarJuego()

        val excepcion = intentarEscribir(juegoId, "editado".toByteArray(), base = "otro-hash-que-no-es")

        assertTrue(excepcion is ErrorDeEscritura.Conflicto)
        excepcion as ErrorDeEscritura.Conflicto
        assertEquals("otro-hash-que-no-es", excepcion.esperado)
        assertEquals(AlmacenDeBlobs.sha256Hex(bytesOriginales), excepcion.encontrado)
        assertArrayEquals(bytesOriginales, sesion.bytesDe(rutaPartida))
        assertTrue(sesion.existeArchivo(rutaEstadoAutomatico)) // tampoco se apartó: no se tocó nada
        assertEquals(1, repositorio.versiones(juegoId).first().count { it.rutaEnConsola == rutaPartida })
    }

    @Test
    fun `escritura feliz aparta el estado automatico y reporta los estados manuales`() = runTest {
        val juegoId = sembrarJuego()
        reloj.avanzar(5_000)
        val bytesEditados = "editado".toByteArray()

        val resultado = escritor.escribir(sesion, juegoId, bytesEditados, base = AlmacenDeBlobs.sha256Hex(bytesOriginales))

        // La partida en la consola quedó con lo nuevo.
        assertArrayEquals(bytesEditados, sesion.bytesDe(rutaPartida))

        // El estado automático se apartó (no se borró): ya no está en su lugar, pero sigue existiendo aparte.
        assertFalse(sesion.existeArchivo(rutaEstadoAutomatico))
        val rutaApartada = resultado.estadosApartados.single()
        assertTrue(rutaApartada.startsWith("/roms/gba/.pr36c/apartados/"))
        assertTrue(rutaApartada.endsWith("/Pokemon Edicion Rojo Fuego.state.auto"))
        assertArrayEquals(bytesEstadoAutomatico, sesion.bytesDe(rutaApartada))

        // El estado manual sigue en su lugar: la app solo avisa que cargarlo perdería la edición.
        assertEquals(listOf(rutaEstadoManual), resultado.estadosManuales)
        assertTrue(sesion.existeArchivo(rutaEstadoManual))

        // Se respaldó lo que había antes de escribir, y se registró lo escrito.
        assertNotNull(resultado.copiaPrevia)
        assertEquals(OrigenDeVersion.ANTES_DE_ESCRIBIR, resultado.copiaPrevia!!.origen)
        assertEquals(AlmacenDeBlobs.sha256Hex(bytesOriginales), resultado.copiaPrevia!!.sha256)
        assertEquals(OrigenDeVersion.ESCRITA_POR_LA_APP, resultado.versionEscrita.origen)
        assertEquals(AlmacenDeBlobs.sha256Hex(bytesEditados), resultado.versionEscrita.sha256)
    }

    @Test
    fun `si falla escribirSeguro el estado automatico apartado se devuelve a su lugar`() = runTest {
        val juegoId = sembrarJuego()
        sesion.falloEnEscribirSeguro = { IOException("se cayó la red a medio subir") }

        val excepcion = intentarEscribir(juegoId, "editado".toByteArray(), base = null)

        assertNotNull(excepcion)
        assertTrue(excepcion!!.message!!.contains("devolvió"))
        assertTrue(sesion.existeArchivo(rutaEstadoAutomatico))
        assertArrayEquals(bytesEstadoAutomatico, sesion.bytesDe(rutaEstadoAutomatico))
        assertArrayEquals(bytesOriginales, sesion.bytesDe(rutaPartida)) // la escritura nunca llegó a aplicarse

        val versionesDeLaPartida = repositorio.versiones(juegoId).first().filter { it.rutaEnConsola == rutaPartida }
        assertEquals(2, versionesDeLaPartida.size) // la del seed (CONSOLA) + la de ANTES_DE_ESCRIBIR del intento fallido
        assertTrue(versionesDeLaPartida.none { it.origen == OrigenDeVersion.ESCRITA_POR_LA_APP })
    }

    @Test
    fun `si tambien falla devolver el estado automatico, el error lo dice`() = runTest {
        val juegoId = sembrarJuego()
        sesion.falloEnEscribirSeguro = { IOException("se cayó la red a medio subir") }
        sesion.falloEnMover = { IOException("tampoco hay red para devolverlo") }

        val excepcion = intentarEscribir(juegoId, "editado".toByteArray(), base = null)

        assertNotNull(excepcion)
        assertFalse(sesion.existeArchivo(rutaEstadoAutomatico)) // no se pudo devolver: sigue apartado
        assertTrue(excepcion!!.message!!.contains("tampoco se pudo devolver"))
    }

    @Test
    fun `restaurar no exige una base concreta pero respalda lo que habia`() = runTest {
        val juegoId = sembrarJuego()
        val versionOriginalId = repositorio.versiones(juegoId).first().single { it.origen == OrigenDeVersion.CONSOLA }.id

        reloj.avanzar(1_000)
        escritor.escribir(sesion, juegoId, "editado".toByteArray(), base = AlmacenDeBlobs.sha256Hex(bytesOriginales))
        reloj.avanzar(1_000)

        val resultado = escritor.restaurar(sesion, versionOriginalId)

        assertArrayEquals(bytesOriginales, sesion.bytesDe(rutaPartida))
        assertEquals(OrigenDeVersion.ESCRITA_POR_LA_APP, resultado.versionEscrita.origen)
        assertEquals(AlmacenDeBlobs.sha256Hex(bytesOriginales), resultado.versionEscrita.sha256)
        assertNotNull(resultado.copiaPrevia)
        assertEquals(AlmacenDeBlobs.sha256Hex("editado".toByteArray()), resultado.copiaPrevia!!.sha256)
    }
}
