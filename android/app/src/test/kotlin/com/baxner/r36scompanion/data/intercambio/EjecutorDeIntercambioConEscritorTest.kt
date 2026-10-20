package com.baxner.r36scompanion.data.intercambio

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.EscritorDePartidasImpl
import com.baxner.r36scompanion.data.respaldos.LectorDeResumenFalso
import com.baxner.r36scompanion.data.respaldos.RelojFalso
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldosImpl
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.respaldos.RespaldosDatabase
import com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [EjecutorDeIntercambioConEscritor] con [EscritorDePartidasImpl] de verdad (Room en memoria,
 * Robolectric) y [SesionDeConsolaFalsa] (reusada de `data/respaldos/`, ver AGENTS.md: nunca
 * contra la consola real), envuelta en [SesionDeConsolaConFallosProgramados] para los casos de
 * falla de la segunda escritura.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EjecutorDeIntercambioConEscritorTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var db: RespaldosDatabase
    private lateinit var repositorio: RepositorioDeRespaldosImpl
    private lateinit var escritor: EscritorDePartidasImpl
    private lateinit var ejecutor: EjecutorDeIntercambioConEscritor
    private lateinit var sesionBase: SesionDeConsolaFalsa

    private val rutaA = "/roms/gba/Pokemon Edicion Rojo Fuego.srm"
    private val rutaB = "/roms/gba/Pokemon Edicion Verde Hoja.srm"
    private val bytesOriginalesA = "original-a".toByteArray()
    private val bytesOriginalesB = "original-b".toByteArray()
    private val bytesNuevosA = "nuevo-a".toByteArray()
    private val bytesNuevosB = "nuevo-b".toByteArray()

    private var juegoAId = 0L
    private var juegoBId = 0L

    @Before
    fun preparar() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RespaldosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val almacen = AlmacenDeBlobs(carpetaTemporal.newFolder("blobs"))
        val lector = LectorDeResumenFalso()
        val reloj = RelojFalso()
        repositorio = RepositorioDeRespaldosImpl(db, almacen, lector, reloj)
        escritor = EscritorDePartidasImpl(db, almacen, lector, reloj)
        ejecutor = EjecutorDeIntercambioConEscritor(escritor)
        sesionBase = SesionDeConsolaFalsa()

        sesionBase.ponerArchivo(rutaA, bytesOriginalesA, modificado = 1000)
        sesionBase.ponerArchivo(rutaB, bytesOriginalesB, modificado = 1000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/0"))
        lector.definir("Pokemon Edicion Verde Hoja.srm", ResumenGuardado(version = "LeafGreen", entrenador = "Rojo", idDeEntrenador = "1/0"))
        repositorio.respaldarTodo(sesionBase)

        val juegos = repositorio.juegos().first()
        juegoAId = juegos.single { it.nombre == "Pokemon Edicion Rojo Fuego" }.id
        juegoBId = juegos.single { it.nombre == "Pokemon Edicion Verde Hoja" }.id
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun partidas() = PartidasIntercambiadas(partidaA = bytesNuevosA, partidaB = bytesNuevosB)

    private suspend fun ejecutarYCapturar(sesion: SesionDeConsola): Exception? = try {
        ejecutor.escribirEnLaConsola(sesion, juegoAId, AlmacenDeBlobs.sha256Hex(bytesOriginalesA), juegoBId, AlmacenDeBlobs.sha256Hex(bytesOriginalesB), partidas())
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `escribe las dos partidas cuando todo sale bien`() = runTest {
        val excepcion = ejecutarYCapturar(sesionBase)

        assertEquals(null, excepcion)
        assertArrayEquals(bytesNuevosA, sesionBase.bytesDe(rutaA))
        assertArrayEquals(bytesNuevosB, sesionBase.bytesDe(rutaB))
    }

    @Test
    fun `si falla la segunda escritura se deshace la primera`() = runTest {
        val sesion = SesionDeConsolaConFallosProgramados(sesionBase).apply {
            fallos = mapOf(rutaB to mapOf(1 to { IOException("se cayó la red al escribir B") }))
        }

        val excepcion = ejecutarYCapturar(sesion)

        assertTrue(excepcion is ErrorDeIntercambio.SeDeshizo)
        assertArrayEquals("A se restauró a como estaba antes del intercambio", bytesOriginalesA, sesionBase.bytesDe(rutaA))
        assertArrayEquals("B nunca se llegó a escribir", bytesOriginalesB, sesionBase.bytesDe(rutaB))
    }

    @Test
    fun `si tambien falla deshacer A el error dice QuedoAMedias con la version a restaurar`() = runTest {
        val sesion = SesionDeConsolaConFallosProgramados(sesionBase).apply {
            fallos = mapOf(
                rutaB to mapOf(1 to { IOException("se cayó la red al escribir B") }),
                // El 1er intento de escribir A (el intercambio) tiene que funcionar; solo el 2º
                // (el de `restaurar`) falla.
                rutaA to mapOf(2 to { IOException("también se cayó al restaurar A") }),
            )
        }

        val excepcion = ejecutarYCapturar(sesion)

        assertTrue(excepcion is ErrorDeIntercambio.QuedoAMedias)
        excepcion as ErrorDeIntercambio.QuedoAMedias
        assertEquals(juegoAId, excepcion.juegoA)
        // A se quedó con el intercambio aplicado (ni se perdió ni se duplicó nada): la versión
        // de antes sigue en el historial para restaurarla a mano, como dice el contrato.
        assertArrayEquals(bytesNuevosA, sesionBase.bytesDe(rutaA))
        assertArrayEquals(bytesOriginalesB, sesionBase.bytesDe(rutaB))
        assertArrayEquals(bytesOriginalesA, repositorio.bytesDe(excepcion.versionPreviaDeA))
    }
}
