package com.baxner.r36scompanion.data.respaldos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
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
 * [RepositorioDeRespaldosImpl] con Room en memoria (Robolectric) y una [SesionDeConsolaFalsa]: ver
 * docs/plan.md, fase 2, y el encargo de esta tarea para la lista de casos cubiertos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositorioDeRespaldosImplTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var db: RespaldosDatabase
    private lateinit var almacen: AlmacenDeBlobs
    private lateinit var lector: LectorDeResumenFalso
    private lateinit var reloj: RelojFalso
    private lateinit var repositorio: RepositorioDeRespaldosImpl
    private lateinit var sesion: SesionDeConsolaFalsa

    @Before
    fun preparar() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RespaldosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        almacen = AlmacenDeBlobs(carpetaTemporal.newFolder("blobs"))
        lector = LectorDeResumenFalso()
        reloj = RelojFalso()
        repositorio = RepositorioDeRespaldosImpl(db, almacen, lector, reloj)
        sesion = SesionDeConsolaFalsa()
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun resumenDePrueba(medallas: Int = 1) =
        ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "12345/00001", medallas = medallas)

    private suspend fun juegoUnico() = repositorio.juegos().first().single()

    @Test
    fun `respaldo inicial crea el juego y su version`() = runTest {
        val bytes = "partida-version-1".toByteArray()
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", bytes, modificado = 1000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", resumenDePrueba())

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(1, resultado.nuevas.size)
        assertEquals(0, resultado.sinCambios)
        assertTrue(resultado.fallidos.isEmpty())

        val juego = juegoUnico()
        assertEquals("gba", juego.sistema)
        assertEquals("Pokemon Edicion Rojo Fuego", juego.nombre)
        assertEquals("/roms/gba/Pokemon Edicion Rojo Fuego.srm", juego.rutaDePartida)
        assertTrue(juego.esPokemon)
        assertTrue(juego.editable)
        assertEquals(EstadoDePartida.CON_DATOS, juego.estadoDePartida)
        assertEquals(resumenDePrueba(), juego.resumen)
        assertNotNull(juego.ultimoRespaldo)

        val versiones = repositorio.versiones(juego.id).first()
        assertEquals(1, versiones.size)
        val version = versiones.single()
        assertEquals(TipoDeArchivo.PARTIDA, version.tipo)
        assertEquals(OrigenDeVersion.CONSOLA, version.origen)
        assertEquals(AlmacenDeBlobs.sha256Hex(bytes), version.sha256)
        assertArrayEquals(bytes, repositorio.bytesDe(version.id))
    }

    @Test
    fun `segundo respaldo sin cambios no baja el archivo ni crea version nueva`() = runTest {
        val bytes = "partida-version-1".toByteArray()
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", bytes, modificado = 1000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", resumenDePrueba())
        repositorio.respaldarTodo(sesion)
        val lecturasTrasElPrimero = sesion.lecturas

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(0, resultado.nuevas.size)
        assertEquals(1, resultado.sinCambios)
        assertEquals(lecturasTrasElPrimero, sesion.lecturas) // no se volvió a leer el archivo
        assertEquals(1, repositorio.versiones(juegoUnico().id).first().size)
    }

    @Test
    fun `un archivo que cambio crea una version nueva, mas reciente primero`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", "version-1".toByteArray(), modificado = 1000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", resumenDePrueba(medallas = 1))
        repositorio.respaldarTodo(sesion)
        val juegoId = juegoUnico().id

        reloj.avanzar(60_000)
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", "version-2".toByteArray(), modificado = 2000)
        lector.definir("Pokemon Edicion Rojo Fuego.srm", resumenDePrueba(medallas = 2))

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(1, resultado.nuevas.size)
        assertEquals(0, resultado.sinCambios)
        val versiones = repositorio.versiones(juegoId).first()
        assertEquals(2, versiones.size)
        assertEquals("version-2".toByteArray().let { AlmacenDeBlobs.sha256Hex(it) }, versiones[0].sha256) // la más nueva primero
        assertEquals(2, juegoUnico().resumen?.medallas)
    }

    @Test
    fun `una partida vacia en todo 0xFF no le pregunta nada al motor`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Verde Hoja.srm", ByteArray(131072) { 0xFF.toByte() }, modificado = 1000)

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(1, resultado.nuevas.size)
        val juego = juegoUnico()
        assertEquals(EstadoDePartida.VACIA, juego.estadoDePartida)
        assertNull(juego.resumen)
        assertEquals(0, lector.llamadas)
    }

    @Test
    fun `un juego que no es de pokemon queda sin resumen y sin ser editable`() = runTest {
        sesion.ponerArchivo("/roms/gba/Avatar - The Legend of Aang.srm", "partida".toByteArray(), modificado = 1000)
        // No se configura el lector para este nombre: el motor "no lo reconoce" (null).

        repositorio.respaldarTodo(sesion)

        val juego = juegoUnico()
        assertEquals("Avatar - The Legend of Aang", juego.nombre)
        assertTrue(!juego.esPokemon)
        assertTrue(!juego.editable)
        assertNull(juego.resumen)
        assertEquals(EstadoDePartida.CON_DATOS, juego.estadoDePartida)
    }

    @Test
    fun `un hack con Pokemon en el nombre que el motor no reconoce se respalda pero no es editable`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Lets Go Eevee.srm", "basura-del-hack".toByteArray(), modificado = 1000)
        // Tampoco se configura el lector: como con Let's Go Eevee de verdad (docs/plan.md), el motor no la reconoce.

        repositorio.respaldarTodo(sesion)

        val juego = juegoUnico()
        assertTrue(juego.esPokemon) // por el nombre
        assertTrue(!juego.editable) // el motor no pudo
        assertNull(juego.resumen)
    }

    @Test
    fun `nombres raros con parentesis comas acentos y signos no truenan`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon - Version Rouge Feu (F).srm", "a".toByteArray(), modificado = 1)
        sesion.ponerArchivo("/roms/gba/Sound of Thunder, A (USA) (En,Fr,De,Es,It).srm", "b".toByteArray(), modificado = 1)
        sesion.ponerArchivo("/roms/gba/Yu-Gi-Oh! GX - Duel Academy (USA).state.auto", "c".toByteArray(), modificado = 1)
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.state1", "d".toByteArray(), modificado = 1)
        // Una ROM y una carpeta oculta junto a las partidas: se tienen que ignorar sin reventar.
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.gba", "rom".toByteArray(), modificado = 1)
        sesion.ponerCarpeta("/roms/gba/.pr36c")

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(4, resultado.nuevas.size)
        assertTrue(resultado.fallidos.isEmpty())
        val nombres = repositorio.juegos().first().map { it.nombre }.toSet()
        assertEquals(
            setOf("Pokemon - Version Rouge Feu (F)", "Sound of Thunder, A (USA) (En,Fr,De,Es,It)", "Yu-Gi-Oh! GX - Duel Academy (USA)", "Pokemon Edicion Rojo Fuego"),
            nombres,
        )
    }

    @Test
    fun `dos partidas con el mismo contenido comparten un solo blob`() = runTest {
        val mismosBytes = ByteArray(131072) { 0xFF.toByte() }
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Verde Hoja.srm", mismosBytes, modificado = 1)
        sesion.ponerArchivo("/roms/gba/Pokemon - Version Vert Feuille (F).srm", mismosBytes, modificado = 1)

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(2, resultado.nuevas.size)
        val hash = resultado.nuevas.map { it.sha256 }.toSet()
        assertEquals(1, hash.size) // mismo hash para los dos
        val carpetaDelHash = File(carpetaTemporal.root, "blobs/${hash.single().take(2)}")
        assertEquals(1, carpetaDelHash.listFiles()?.size) // un solo archivo en el almacén
    }

    @Test
    fun `un archivo que falla se anota en fallidos y los demas se siguen respaldando`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", "a".toByteArray(), modificado = 1)
        sesion.ponerArchivo("/roms/gba/Avatar - The Legend of Aang.srm", "b".toByteArray(), modificado = 1)
        sesion.fallarAlLeer("/roms/gba/Pokemon Edicion Rojo Fuego.srm") { RuntimeException("la consola se desconectó") }

        val resultado = repositorio.respaldarTodo(sesion)

        assertEquals(1, resultado.nuevas.size)
        assertEquals("Avatar - The Legend of Aang", repositorio.juegos().first().single { it.nombre == "Avatar - The Legend of Aang" }.nombre)
        assertEquals(1, resultado.fallidos.size)
        assertEquals("/roms/gba/Pokemon Edicion Rojo Fuego.srm", resultado.fallidos.single().first)
        assertTrue(resultado.fallidos.single().second.contains("desconectó"))
    }

    @Test
    fun `juegos() ordena del respaldo mas reciente al mas viejo`() = runTest {
        sesion.ponerArchivo("/roms/gba/Primero.srm", "1".toByteArray(), modificado = 1)
        repositorio.respaldarTodo(sesion)
        reloj.avanzar(1_000)
        sesion.ponerArchivo("/roms/gba/Segundo.srm", "2".toByteArray(), modificado = 1)
        repositorio.respaldarTodo(sesion)

        val nombres = repositorio.juegos().first().map { it.nombre }

        assertEquals(listOf("Segundo", "Primero"), nombres)
    }

    @Test
    fun `exportarZip guarda la ultima version de cada archivo con la ruta de la consola`() = runTest {
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", "version-1".toByteArray(), modificado = 1)
        repositorio.respaldarTodo(sesion)
        reloj.avanzar(1_000)
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.srm", "version-2".toByteArray(), modificado = 2)
        sesion.ponerArchivo("/roms/gba/Pokemon Edicion Rojo Fuego.state1", "estado".toByteArray(), modificado = 1)
        repositorio.respaldarTodo(sesion)

        val salida = ByteArrayOutputStream()
        repositorio.exportarZip(salida)

        val entradas = mutableMapOf<String, ByteArray>()
        ZipInputStream(salida.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entrada = zip.nextEntry ?: break
                entradas[entrada.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("roms/gba/Pokemon Edicion Rojo Fuego.srm", "roms/gba/Pokemon Edicion Rojo Fuego.state1"), entradas.keys)
        assertArrayEquals("version-2".toByteArray(), entradas.getValue("roms/gba/Pokemon Edicion Rojo Fuego.srm"))
        assertArrayEquals("estado".toByteArray(), entradas.getValue("roms/gba/Pokemon Edicion Rojo Fuego.state1"))
    }
}
