package com.baxner.r36scompanion.data.jugar

import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [RomsDelTelefonoEnArchivos] con una [SesionDeConsolaFalsa] (ver AGENTS.md: nunca contra la
 * consola real) y una carpeta temporal en vez de `noBackupFilesDir`.
 */
class RomsDelTelefonoEnArchivosTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var sesion: SesionDeConsolaFalsa
    private lateinit var carpeta: File

    private val rutaDePartida = "/roms/gba/Pokemon Edicion Rojo Fuego.srm"
    private val carpetaRemota = "/roms/gba"

    private val juego = JuegoRespaldado(
        id = 1,
        sistema = "gba",
        nombre = "Pokemon Edicion Rojo Fuego",
        rutaDePartida = rutaDePartida,
        esPokemon = true,
        editable = true,
        ultimoRespaldo = null,
        resumen = null,
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )

    @Before
    fun preparar() {
        sesion = SesionDeConsolaFalsa()
        carpeta = carpetaTemporal.newFolder("roms")
    }

    private fun telefono(espacioLibre: () -> Long = { Long.MAX_VALUE }) = RomsDelTelefonoEnArchivos(carpeta, espacioLibre)

    /** Una ROM de GBA válida: tamaño ≥ 0xC0, byte 3 = 0xEA, código en 0xAC y título en 0xA0. */
    private fun romValida(codigo: String = "BPRE", titulo: String = "POKEMON FIRE"): ByteArray {
        val bytes = ByteArray(0xC0)
        bytes[3] = 0xEA.toByte()
        titulo.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0xA0)
        codigo.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0xAC)
        return bytes
    }

    private fun zipCon(vararg entradas: Pair<String, ByteArray>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            for ((nombre, contenido) in entradas) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido)
                zip.closeEntry()
            }
        }
        return salida.toByteArray()
    }

    @Test
    fun `copia una ROM gba`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(), modificado = 1000)

        val rom = telefono().traer(sesion, juego)

        assertEquals("BPRE", rom.codigoDeJuego)
        assertEquals("POKEMON FIRE", rom.tituloInterno)
        assertArrayEquals(romValida(), rom.archivo.readBytes())
        assertEquals(1, sesion.lecturas)
    }

    @Test
    fun `reconoce la extension en mayusculas`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.GBA", romValida(), modificado = 1000)

        val rom = telefono().traer(sesion, juego)

        assertEquals("BPRE", rom.codigoDeJuego)
    }

    @Test
    fun `extrae la rom de un zip`() = runTest {
        sesion.ponerArchivo(
            "$carpetaRemota/Pokemon Edicion Rojo Fuego.zip",
            zipCon("Pokemon Edicion Rojo Fuego.GBA" to romValida()),
            modificado = 1000,
        )

        val rom = telefono().traer(sesion, juego)

        assertEquals("BPRE", rom.codigoDeJuego)
        assertArrayEquals(romValida(), rom.archivo.readBytes())
    }

    @Test
    fun `prefiere gba sobre zip si estan los dos`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(codigo = "BPRE"), modificado = 1000)
        sesion.ponerArchivo(
            "$carpetaRemota/Pokemon Edicion Rojo Fuego.zip",
            zipCon("x.gba" to romValida(codigo = "ZZZZ")),
            modificado = 1000,
        )

        val rom = telefono().traer(sesion, juego)

        assertEquals("BPRE", rom.codigoDeJuego)
    }

    @Test
    fun `7z sin gba ni zip lanza FormatoNoSoportado`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.7z", "lo que sea".toByteArray(), modificado = 1000)

        val excepcion = runCatching { telefono().traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeRom.FormatoNoSoportado)
        assertEquals("7z", (excepcion as ErrorDeRom.FormatoNoSoportado).extension)
    }

    @Test
    fun `sin ninguna rom lanza NoEncontrada`() = runTest {
        val excepcion = runCatching { telefono().traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeRom.NoEncontrada)
    }

    @Test
    fun `datos que no son de gba lanzan NoEsDeGba`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", ByteArray(200), modificado = 1000)

        val excepcion = runCatching { telefono().traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeRom.NoEsDeGba)
    }

    @Test
    fun `zip sin gba adentro lanza NoEsDeGba`() = runTest {
        sesion.ponerArchivo(
            "$carpetaRemota/Pokemon Edicion Rojo Fuego.zip",
            zipCon("leeme.txt" to "hola".toByteArray()),
            modificado = 1000,
        )

        val excepcion = runCatching { telefono().traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeRom.NoEsDeGba)
    }

    @Test
    fun `no vuelve a leer si no cambio en la consola`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(), modificado = 1000)
        val telefono = telefono()

        telefono.traer(sesion, juego)
        assertEquals(1, sesion.lecturas)

        val segunda = telefono.traer(sesion, juego)

        assertEquals(1, sesion.lecturas) // no volvió a leer
        assertEquals("BPRE", segunda.codigoDeJuego)
    }

    @Test
    fun `vuelve a copiar si cambio en la consola`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(codigo = "BPRE"), modificado = 1000)
        val telefono = telefono()
        telefono.traer(sesion, juego)

        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(codigo = "BPGE"), modificado = 2000)
        val segunda = telefono.traer(sesion, juego)

        assertEquals(2, sesion.lecturas)
        assertEquals("BPGE", segunda.codigoDeJuego)
    }

    @Test
    fun `si falla la lectura no queda nada nuevo ni temporales`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(), modificado = 1000)
        sesion.fallarAlLeer("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba") { IOException("se cayó la red") }

        val excepcion = runCatching { telefono().traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is IOException)
        val archivos = carpeta.listFiles() ?: emptyArray()
        assertTrue("no debe quedar ningún archivo nuevo ni temporal: ${archivos.map { it.name }}", archivos.isEmpty())
    }

    @Test
    fun `la copia vieja sigue si falla al volver a copiar`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(codigo = "BPRE"), modificado = 1000)
        val telefono = telefono()
        val original = telefono.traer(sesion, juego)

        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(codigo = "BPGE"), modificado = 2000)
        sesion.fallarAlLeer("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba") { IOException("se cayó la red") }

        val excepcion = runCatching { telefono.traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is IOException)
        val siguenIguales = telefono.rom(juego.id)
        assertEquals(original.codigoDeJuego, siguenIguales?.codigoDeJuego)
        assertArrayEquals(romValida(codigo = "BPRE"), original.archivo.readBytes())
    }

    @Test
    fun `sin espacio libre lanza SinEspacio`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(), modificado = 1000)

        val excepcion = runCatching { telefono(espacioLibre = { 0L }).traer(sesion, juego) }.exceptionOrNull()

        assertTrue(excepcion is ErrorDeRom.SinEspacio)
    }

    @Test
    fun `guardadas se recupera en otra instancia desde el disco`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(), modificado = 1000)
        telefono().traer(sesion, juego)

        val otraInstancia = telefono()
        val lista = otraInstancia.guardadas().first()

        assertEquals(1, lista.size)
        assertEquals("BPRE", lista.single().codigoDeJuego)
        assertEquals(juego.id, otraInstancia.rom(juego.id)?.juegoId)
    }

    @Test
    fun `borrar quita la rom y sus metadatos`() = runTest {
        sesion.ponerArchivo("$carpetaRemota/Pokemon Edicion Rojo Fuego.gba", romValida(), modificado = 1000)
        val telefono = telefono()
        telefono.traer(sesion, juego)

        telefono.borrar(juego.id)

        assertEquals(null, telefono.rom(juego.id))
        assertTrue((carpeta.listFiles() ?: emptyArray()).isEmpty())
    }
}
