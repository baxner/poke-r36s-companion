package com.baxner.r36scompanion.data.jugar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baxner.r36scompanion.data.consola.JuegoAbierto
import com.baxner.r36scompanion.data.intercambio.EjecutorDeIntercambioConEscritor
import com.baxner.r36scompanion.data.intercambio.ErrorDeIntercambio
import com.baxner.r36scompanion.data.intercambio.SesionDeConsolaConFallosProgramados
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.Cambio
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.EscritorDePartidasImpl
import com.baxner.r36scompanion.data.respaldos.LectorDeResumen
import com.baxner.r36scompanion.data.respaldos.LectorDeResumenFalso
import com.baxner.r36scompanion.data.respaldos.RelojFalso
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldosImpl
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.respaldos.RespaldosDatabase
import com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [JugarEnElTelefono] (Prestamos + IntercambiosReales) con Room en memoria (Robolectric), una
 * [SesionDeConsolaFalsa] y un [EscritorDePartidasImpl]/[EjecutorDeIntercambioConEscritor] de
 * verdad: se prueba de punta a punta, igual que [EscritorDePartidasImplTest] y
 * [EjecutorDeIntercambioConEscritorTest] (ver AGENTS.md: nunca contra la consola real).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JugarEnElTelefonoTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var db: RespaldosDatabase
    private lateinit var repositorio: RepositorioDeRespaldosImpl
    private lateinit var escritor: EscritorDePartidasImpl
    private lateinit var ejecutor: EjecutorDeIntercambioConEscritor
    private lateinit var lector: LectorDeResumenFalso
    private lateinit var reloj: RelojFalso
    private lateinit var sesion: SesionDeConsolaFalsa
    private lateinit var carpetaDeJugar: File

    private val rutaA = "/roms/gba/Pokemon Edicion Rojo Fuego.srm"
    private val rutaB = "/roms/gba/Pokemon Edicion Verde Hoja.srm"
    private val bytesOriginalesA = "original-a".toByteArray()
    private val bytesOriginalesB = "original-b".toByteArray()

    private var juegoAId = 0L
    private var juegoBId = 0L

    private fun jugar(lectorPropio: LectorDeResumen = lector) =
        JugarEnElTelefono(carpetaDeJugar, repositorio, escritor, ejecutor, lectorPropio, reloj)

    @Before
    fun preparar() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RespaldosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val almacen = AlmacenDeBlobs(carpetaTemporal.newFolder("blobs"))
        lector = LectorDeResumenFalso()
        reloj = RelojFalso()
        repositorio = RepositorioDeRespaldosImpl(db, almacen, lector, reloj)
        escritor = EscritorDePartidasImpl(db, almacen, lector, reloj)
        ejecutor = EjecutorDeIntercambioConEscritor(escritor)
        sesion = SesionDeConsolaFalsa()
        carpetaDeJugar = carpetaTemporal.newFolder("jugar")

        sesion.ponerArchivo(rutaA, bytesOriginalesA, modificado = 1000)
        sesion.ponerArchivo(rutaB, bytesOriginalesB, modificado = 1000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/0"))
        lector.definir("Pokemon Edicion Verde Hoja.srm", ResumenGuardado(version = "LeafGreen", entrenador = "Rojo", idDeEntrenador = "1/0"))
        repositorio.respaldarTodo(sesion)

        val juegos = repositorio.juegos().first()
        juegoAId = juegos.single { it.nombre == "Pokemon Edicion Rojo Fuego" }.id
        juegoBId = juegos.single { it.nombre == "Pokemon Edicion Verde Hoja" }.id
    }

    @After
    fun cerrar() {
        db.close()
    }

    // ---------------------------------------------------------------------------------------
    // Prestamos
    // ---------------------------------------------------------------------------------------

    @Test
    fun `llevar usa la ultima version de PARTIDA`() = runTest {
        val prestamo = jugar().llevar(juegoAId)

        assertEquals(AlmacenDeBlobs.sha256Hex(bytesOriginalesA), prestamo.base)
        assertEquals(prestamo.base, prestamo.actual)
        assertEquals(0, prestamo.vecesGuardada)
        assertNull(prestamo.ultimaVezGuardada)
    }

    @Test
    fun `llevar sin partida respaldada deja base null y partida vacia`() = runTest {
        val jugar = jugar()
        val idSinRespaldo = 999_999L

        val prestamo = jugar.llevar(idSinRespaldo)

        assertNull(prestamo.base)
        assertNull(prestamo.actual)
        assertArrayEquals(ByteArray(0), jugar.partida(idSinRespaldo))
    }

    @Test
    fun `llevar devuelve el mismo prestamo si ya estaba prestada`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        jugar.guardar(juegoAId, "cambio".toByteArray())

        val segundo = jugar.llevar(juegoAId)

        assertEquals(1, segundo.vecesGuardada) // no se reinició: es el mismo préstamo
    }

    @Test
    fun `guardar sube el contador y el hash, de forma atomica`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        reloj.avanzar(5_000)

        val actualizado = jugar.guardar(juegoAId, "nuevo contenido".toByteArray())

        assertEquals(1, actualizado.vecesGuardada)
        assertEquals(AlmacenDeBlobs.sha256Hex("nuevo contenido".toByteArray()), actualizado.actual)
        assertEquals(reloj.ahoraMs(), actualizado.ultimaVezGuardada)
        assertArrayEquals("nuevo contenido".toByteArray(), jugar.partida(juegoAId))
    }

    @Test
    fun `el prestamo persiste entre instancias`() = runTest {
        jugar().apply {
            llevar(juegoAId)
            guardar(juegoAId, "guardado en el telefono".toByteArray())
        }

        val segundaInstancia = jugar()
        val prestamo = segundaInstancia.prestada(juegoAId)

        assertNotNull(prestamo)
        assertEquals(1, prestamo!!.vecesGuardada)
        assertArrayEquals("guardado en el telefono".toByteArray(), segundaInstancia.partida(juegoAId))
    }

    @Test
    fun `regresar sin haber guardado no escribe y termina el prestamo`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)

        val resultado = jugar.regresar(sesion, juegoAId)

        assertNull(resultado)
        assertArrayEquals(bytesOriginalesA, sesion.bytesDe(rutaA)) // no se tocó la consola
        assertNull(jugar.prestada(juegoAId))
    }

    @Test
    fun `regresar escribe en la consola y termina el prestamo`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        jugar.guardar(juegoAId, "editado en el telefono".toByteArray())

        val resultado = jugar.regresar(sesion, juegoAId)

        assertNotNull(resultado)
        assertArrayEquals("editado en el telefono".toByteArray(), sesion.bytesDe(rutaA))
        assertNull(jugar.prestada(juegoAId))
    }

    @Test
    fun `conflicto deja el prestamo intacto`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        jugar.guardar(juegoAId, "editado en el telefono".toByteArray())
        sesion.ponerArchivo(rutaA, "se jugo en la consola mientras tanto".toByteArray(), modificado = 2000)

        val excepcion = runCatching { jugar.regresar(sesion, juegoAId) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeEscritura.Conflicto)
        val prestamo = jugar.prestada(juegoAId)
        assertNotNull(prestamo) // el préstamo sigue como estaba
        assertEquals(1, prestamo!!.vecesGuardada)
        assertArrayEquals("se jugo en la consola mientras tanto".toByteArray(), sesion.bytesDe(rutaA))
    }

    @Test
    fun `regresarAunqueCambio escribe pese al conflicto`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        jugar.guardar(juegoAId, "editado en el telefono".toByteArray())
        sesion.ponerArchivo(rutaA, "se jugo en la consola mientras tanto".toByteArray(), modificado = 2000)

        val resultado = jugar.regresarAunqueCambio(sesion, juegoAId)

        assertNotNull(resultado)
        assertArrayEquals("editado en el telefono".toByteArray(), sesion.bytesDe(rutaA))
        assertNull(jugar.prestada(juegoAId))
    }

    @Test
    fun `juego abierto deja el prestamo intacto`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        jugar.guardar(juegoAId, "editado".toByteArray())
        sesion.juegosAbiertosSimulados = listOf(JuegoAbierto("retroarch", "mgba_rumble", rutaA))

        val excepcion = runCatching { jugar.regresar(sesion, juegoAId) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeEscritura.JuegoAbierto)
        assertNotNull(jugar.prestada(juegoAId))
        assertArrayEquals(bytesOriginalesA, sesion.bytesDe(rutaA)) // nunca se escribió
    }

    @Test
    fun `descartar mueve la partida del telefono a descartadas sin tocar la consola`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)
        jugar.guardar(juegoAId, "para descartar".toByteArray())

        jugar.descartar(juegoAId)

        assertNull(jugar.prestada(juegoAId))
        assertArrayEquals(bytesOriginalesA, sesion.bytesDe(rutaA)) // la consola no se tocó
        val descartadas = File(carpetaDeJugar, "descartadas").listFiles()
        assertEquals(1, descartadas?.size)
        assertArrayEquals("para descartar".toByteArray(), descartadas!!.single().readBytes())
    }

    // ---------------------------------------------------------------------------------------
    // Avisos
    // ---------------------------------------------------------------------------------------

    @Test
    fun `avisa PartidaVacia si la partida esta vacia`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Esmeralda.srm", ByteArray(100) { 0xFF.toByte() }, modificado = 1000)
        repositorio.respaldarTodo(sesion)
        val juegoVacioId = repositorio.juegos().first().single { it.nombre == "Pokemon Edicion Esmeralda" }.id

        val avisos = jugar().avisosAntesDeLlevar(juegoVacioId)

        assertTrue(avisos.contains(AvisoAntesDeJugar.PartidaVacia))
    }

    @Test
    fun `avisa NoReconocida si el motor no reconoce un juego de pokemon`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Hack Raro.srm", "datos-random".toByteArray(), modificado = 1000)
        repositorio.respaldarTodo(sesion)
        val juegoHackId = repositorio.juegos().first().single { it.nombre == "Pokemon Hack Raro" }.id

        val avisos = jugar().avisosAntesDeLlevar(juegoHackId)

        assertTrue(avisos.contains(AvisoAntesDeJugar.NoReconocida))
    }

    @Test
    fun `avisa avance solo en el estado si el estado automatico es casi tan nuevo como la partida`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.state.auto", "estado".toByteArray(), modificado = 1200)
        repositorio.respaldarTodo(sesion)

        val avisos = jugar().avisosAntesDeLlevar(juegoAId)

        val aviso = avisos.filterIsInstance<AvisoAntesDeJugar.AvanceSoloEnElEstado>().single()
        assertEquals(1200L, aviso.estadoModificado)
        assertEquals(1000L, aviso.partidaModificada)
    }

    @Test
    fun `no avisa avance en el estado si es mucho mas viejo que la partida`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.state.auto", "estado".toByteArray(), modificado = 100)
        repositorio.respaldarTodo(sesion)

        val avisos = jugar().avisosAntesDeLlevar(juegoAId)

        assertTrue(avisos.none { it is AvisoAntesDeJugar.AvanceSoloEnElEstado })
    }

    // ---------------------------------------------------------------------------------------
    // Exclusión préstamo / intercambio
    // ---------------------------------------------------------------------------------------

    @Test
    fun `no se puede llevar una partida que ya esta en un intercambio real`() = runTest {
        val jugar = jugar()
        jugar.empezar(juegoAId, juegoBId)

        val excepcion = runCatching { jugar.llevar(juegoAId) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeJuego.YaEstaEnElTelefono)
    }

    @Test
    fun `no se puede empezar un intercambio con una partida ya prestada`() = runTest {
        val jugar = jugar()
        jugar.llevar(juegoAId)

        val excepcion = runCatching { jugar.empezar(juegoAId, juegoBId) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeJuego.YaEstaEnElTelefono)
    }

    @Test
    fun `no se puede empezar un segundo intercambio real`() = runTest {
        val jugar = jugar()
        val primero = jugar.empezar(juegoAId, juegoBId)

        val excepcion = runCatching { jugar.empezar(juegoBId, juegoAId) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeJuego.HayOtroIntercambio)
        assertEquals(primero.id, (excepcion as ErrorDeJuego.HayOtroIntercambio).id)
    }

    // ---------------------------------------------------------------------------------------
    // IntercambiosReales
    // ---------------------------------------------------------------------------------------

    @Test
    fun `empezar toma la ultima version de PARTIDA de cada juego`() = runTest {
        val intercambio = jugar().empezar(juegoAId, juegoBId)

        assertEquals(AlmacenDeBlobs.sha256Hex(bytesOriginalesA), intercambio.baseA)
        assertEquals(AlmacenDeBlobs.sha256Hex(bytesOriginalesB), intercambio.baseB)
        assertEquals(EstadoDeIntercambioReal.JUGANDO, intercambio.estado)
        assertEquals(0, intercambio.vecesGuardadaA)
        assertEquals(0, intercambio.vecesGuardadaB)
    }

    @Test
    fun `guardar A y B sube cada contador por separado`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)

        jugar.guardar(intercambio.id, jugador = 0, bytes = "a-jugado".toByteArray())
        val actualizado = jugar.guardar(intercambio.id, jugador = 1, bytes = "b-jugado".toByteArray())

        assertEquals(1, actualizado.vecesGuardadaA)
        assertEquals(1, actualizado.vecesGuardadaB)
        val (bytesA, bytesB) = jugar.partidas(intercambio.id)
        assertArrayEquals("a-jugado".toByteArray(), bytesA)
        assertArrayEquals("b-jugado".toByteArray(), bytesB)
    }

    @Test
    fun `terminarDeJugar pasa a POR_ESCRIBIR`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)

        val terminado = jugar.terminarDeJugar(intercambio.id)

        assertEquals(EstadoDeIntercambioReal.POR_ESCRIBIR, terminado.estado)
    }

    @Test
    fun `cambios compara el resumen original contra el actual`() = runTest {
        // El lector falso solo distingue por nombre de archivo, no por contenido: para probar un
        // cambio de verdad usamos uno propio que sí distingue el contenido de cada .sav.
        val lectorPorContenido = LectorDeResumen { bytes, _ ->
            when (String(bytes)) {
                "original-a" -> ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/0", medallas = 2)
                "a-jugado" -> ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/0", medallas = 3)
                else -> null
            }
        }
        val jugar = jugar(lectorPorContenido)
        val intercambio = jugar.empezar(juegoAId, juegoBId)
        jugar.guardar(intercambio.id, jugador = 0, bytes = "a-jugado".toByteArray())

        val cambios = jugar.cambios(intercambio.id)

        assertEquals(3, cambios.resumenA?.medallas)
        assertTrue(cambios.cambiosA.any { it is Cambio.Medallas })
    }

    @Test
    fun `escribir las dos cuando ambas guardaron`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)
        jugar.guardar(intercambio.id, jugador = 0, bytes = "a-final".toByteArray())
        jugar.guardar(intercambio.id, jugador = 1, bytes = "b-final".toByteArray())

        jugar.escribir(sesion, intercambio.id)

        assertArrayEquals("a-final".toByteArray(), sesion.bytesDe(rutaA))
        assertArrayEquals("b-final".toByteArray(), sesion.bytesDe(rutaB))
        assertNull(jugar.pendiente().first())
    }

    @Test
    fun `escribir solo la que guardo`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)
        jugar.guardar(intercambio.id, jugador = 0, bytes = "solo-a".toByteArray())

        jugar.escribir(sesion, intercambio.id)

        assertArrayEquals("solo-a".toByteArray(), sesion.bytesDe(rutaA))
        assertArrayEquals(bytesOriginalesB, sesion.bytesDe(rutaB)) // B no cambió
        assertNull(jugar.pendiente().first())
    }

    @Test
    fun `escribir ninguna solo termina el intercambio`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)

        jugar.escribir(sesion, intercambio.id)

        assertArrayEquals(bytesOriginalesA, sesion.bytesDe(rutaA))
        assertArrayEquals(bytesOriginalesB, sesion.bytesDe(rutaB))
        assertNull(jugar.pendiente().first())
    }

    @Test
    fun `si falla escribir B el intercambio queda pendiente y la consola como estaba`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)
        jugar.guardar(intercambio.id, jugador = 0, bytes = "a-final".toByteArray())
        jugar.guardar(intercambio.id, jugador = 1, bytes = "b-final".toByteArray())

        val sesionConFallos = SesionDeConsolaConFallosProgramados(sesion).apply {
            fallos = mapOf(rutaB to mapOf(1 to { IOException("se cayó la red al escribir B") }))
        }

        val excepcion = runCatching { jugar.escribir(sesionConFallos, intercambio.id) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeIntercambio.SeDeshizo)
        assertArrayEquals("A se restauró a como estaba antes del intercambio", bytesOriginalesA, sesion.bytesDe(rutaA))
        assertArrayEquals("B nunca se llegó a escribir", bytesOriginalesB, sesion.bytesDe(rutaB))
        val pendiente = jugar.pendiente().first()
        assertNotNull("sigue pendiente: se puede reintentar o descartar", pendiente)
        assertEquals(intercambio.id, pendiente!!.id)
        assertEquals(1, pendiente.vecesGuardadaA)
        assertEquals(1, pendiente.vecesGuardadaB)
    }

    @Test
    fun `descartar intercambio mueve las dos partidas a descartadas`() = runTest {
        val jugar = jugar()
        val intercambio = jugar.empezar(juegoAId, juegoBId)
        jugar.guardar(intercambio.id, jugador = 0, bytes = "a-descartado".toByteArray())
        jugar.guardar(intercambio.id, jugador = 1, bytes = "b-descartado".toByteArray())

        jugar.descartar(intercambio.id)

        assertNull(jugar.pendiente().first())
        assertArrayEquals(bytesOriginalesA, sesion.bytesDe(rutaA))
        assertArrayEquals(bytesOriginalesB, sesion.bytesDe(rutaB))
        val descartadas = File(carpetaDeJugar, "descartadas").listFiles()?.map { it.name } ?: emptyList()
        assertEquals(2, descartadas.size)
        assertTrue(descartadas.any { it.endsWith("-a.sav") })
        assertTrue(descartadas.any { it.endsWith("-b.sav") })
    }
}
