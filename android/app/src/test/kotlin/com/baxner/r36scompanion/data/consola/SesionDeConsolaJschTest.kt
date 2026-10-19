package com.baxner.r36scompanion.data.consola

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [SesionDeConsolaJsch] contra un [ServidorSshDePrueba] de verdad: listar/info/leer con nombres
 * reales, `escribirSeguro` (nuevo, reemplazo y un fallo a la mitad), `mover` sin pisar,
 * `crearCarpetas`, `juegosAbiertos` y `ajustesRetroArch`.
 */
class SesionDeConsolaJschTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    // --- listar / info / leer ---

    @Test
    fun `listar devuelve los nombres reales de la consola tal cual, con espacios y todo`() = abrirSesion { sesion, raiz ->
        val nombres = listOf(
            "Pokemon - Version Rouge Feu (F).srm",
            "Sound of Thunder, A (USA) (En,Fr,De,Es,It).srm",
            "Yu-Gi-Oh! GX - Duel Academy (USA).srm",
        )
        val carpetaRoms = File(raiz, "roms/gba").apply { mkdirs() }
        nombres.forEach { File(carpetaRoms, it).writeBytes(byteArrayOf(1, 2, 3)) }

        val listado = sesion.listar("/roms/gba")

        assertEquals(nombres.toSet(), listado.map { it.nombre }.toSet())
        assertEquals(nombres.map { "/roms/gba/$it" }.toSet(), listado.map { it.ruta }.toSet())
        assertTrue(listado.all { !it.esCarpeta && it.tamano == 3L })
    }

    @Test
    fun `info da null si no existe y los datos si existe`() = abrirSesion { sesion, raiz ->
        assertNull(sesion.info("/no/existe.srm"))

        File(raiz, "existe.srm").writeBytes(ByteArray(10))

        val info = sesion.info("/existe.srm")
        assertEquals(10L, info?.tamano)
        assertEquals(false, info?.esCarpeta)
    }

    @Test
    fun `leer trae el archivo completo si cabe en el limite`() = abrirSesion { sesion, raiz ->
        File(raiz, "partida.srm").writeBytes(ByteArray(1000) { it.toByte() })

        val bytes = sesion.leer("/partida.srm", maximo = 2000)

        assertEquals(1000, bytes.size)
    }

    @Test
    fun `leer avisa con MuyGrande si pasa del limite, sin importar lo que diga stat`() = abrirSesion { sesion, raiz ->
        File(raiz, "grande.srm").writeBytes(ByteArray(1000))

        val error = assertFallaCon<ErrorDeConsola.MuyGrande> { sesion.leer("/grande.srm", maximo = 500) }

        assertEquals("/grande.srm", error.ruta)
    }

    @Test
    fun `leer un archivo que no existe da NoExiste`() = abrirSesion { sesion, _ ->
        assertFallaCon<ErrorDeConsola.NoExiste> { sesion.leer("/no/existe.srm") }
    }

    // --- escribirSeguro ---

    @Test
    fun `escribirSeguro crea un archivo nuevo que no existia, sin dejar temporales`() = abrirSesion { sesion, raiz ->
        val bytes = "hola mundo".toByteArray()

        sesion.escribirSeguro("/nuevo.srm", bytes)

        assertArrayEquals(bytes, File(raiz, "nuevo.srm").readBytes())
        assertSinTemporales(raiz, "nuevo.srm")
    }

    @Test
    fun `escribirSeguro reemplaza un archivo existente, sin dejar temporales`() = abrirSesion { sesion, raiz ->
        File(raiz, "partida.srm").writeBytes("contenido viejo".toByteArray())

        sesion.escribirSeguro("/partida.srm", "contenido nuevo, mas largo que el viejo".toByteArray())

        assertArrayEquals("contenido nuevo, mas largo que el viejo".toByteArray(), File(raiz, "partida.srm").readBytes())
        assertSinTemporales(raiz, "partida.srm")
    }

    @Test
    fun `escribirSeguro con un fallo a la mitad deja el original intacto y sin temporales`() = abrirSesion { sesion, raiz ->
        assumeTrue("usa chflags (BSD), especifico de macOS", System.getProperty("os.name").orEmpty().contains("Mac"))
        val archivo = File(raiz, "partida.srm").apply { writeBytes("contenido-original-que-no-se-debe-perder".toByteArray()) }
        marcarInmutable(archivo) // así el segundo de los dos `rename` de escribirSeguro falla de verdad

        try {
            assertFallaCon<ErrorDeConsola> {
                sesion.escribirSeguro("/partida.srm", "esto-nunca-deberia-quedar".toByteArray())
            }
        } finally {
            quitarInmutable(archivo)
        }

        assertArrayEquals("contenido-original-que-no-se-debe-perder".toByteArray(), archivo.readBytes())
        assertSinTemporales(raiz, "partida.srm")
    }

    @Test
    fun `si queda un -prev bueno junto a una ruta corrupta de un intento anterior, no se pierde el bueno`() = abrirSesion { sesion, raiz ->
        // Lo que deja un escribirSeguro que murió en la verificación final sin poder deshacerse
        // (ver el KDoc de SesionDeConsolaJsch.repararIntentoPrevio): el original bueno apartado en
        // "-prev" y la ruta con contenido que nunca llegó a verificar.
        File(raiz, "partida.srm").writeBytes("contenido-corrupto-sin-verificar".toByteArray())
        File(raiz, "partida.srm.pr36c-prev").writeBytes("contenido-bueno-original".toByteArray())

        sesion.escribirSeguro("/partida.srm", "contenido-nuevo-verificado".toByteArray())

        // La escritura de ahora quedó bien puesta y verificada.
        assertArrayEquals("contenido-nuevo-verificado".toByteArray(), File(raiz, "partida.srm").readBytes())
        // El "-prev" de antes no se pudo borrar a ciegas (no se sabía si era la única copia buena):
        // se apartó con otro nombre en vez de perderse o pisarse.
        assertFalse(File(raiz, "partida.srm.pr36c-prev").exists())
        val apartados = raiz.listFiles { archivo -> archivo.name.startsWith("partida.srm.pr36c-prev-") }.orEmpty()
        assertEquals(1, apartados.size)
        assertArrayEquals("contenido-bueno-original".toByteArray(), apartados.single().readBytes())
        assertSinTemporales(raiz, "partida.srm")
    }

    @Test
    fun `escribirSeguro manda sync con la ruta escapada, aunque el nombre sea raro`() {
        val comandos = FabricaDeComandosFalsa()
        abrirSesion(comandos = comandos) { sesion, raiz ->
            val ruta = "/roms/gba/Pokemon - Version Rouge Feu (F).srm"
            File(raiz, "roms/gba").mkdirs()

            sesion.escribirSeguro(ruta, "contenido".toByteArray())

            val comandoDeSync = comandos.comandosRecibidos.firstOrNull { it.startsWith("sync") }
            assertNotNull("se esperaba un comando de sync", comandoDeSync)
            assertTrue(comandoDeSync!!.contains(comillaSimpleSh(ruta)))
        }
    }

    private fun assertSinTemporales(raiz: File, nombre: String) {
        assertFalse(File(raiz, "$nombre.pr36c-tmp").exists())
        assertFalse(File(raiz, "$nombre.pr36c-prev").exists())
    }

    // --- mover ---

    @Test
    fun `mover renombra y nunca pisa un destino que ya existe`() = abrirSesion { sesion, raiz ->
        File(raiz, "origen.srm").writeBytes("contenido".toByteArray())

        sesion.mover("/origen.srm", "/destino.srm")

        assertArrayEquals("contenido".toByteArray(), File(raiz, "destino.srm").readBytes())
        assertFalse(File(raiz, "origen.srm").exists())

        File(raiz, "origen2.srm").writeBytes("otro contenido".toByteArray())
        val error = assertFallaCon<ErrorDeConsola.YaExiste> { sesion.mover("/origen2.srm", "/destino.srm") }

        assertEquals("/destino.srm", error.ruta)
        assertArrayEquals("contenido".toByteArray(), File(raiz, "destino.srm").readBytes())
        assertTrue(File(raiz, "origen2.srm").exists())
    }

    // --- crearCarpetas ---

    @Test
    fun `crearCarpetas crea toda la ruta de un jalon, como mkdir -p`() = abrirSesion { sesion, raiz ->
        sesion.crearCarpetas("/respaldos/2026-10-19/gba")

        assertTrue(File(raiz, "respaldos/2026-10-19/gba").isDirectory)
    }

    @Test
    fun `crearCarpetas no truena si la carpeta ya existia`() = abrirSesion { sesion, raiz ->
        File(raiz, "respaldos").mkdirs()

        sesion.crearCarpetas("/respaldos")

        assertTrue(File(raiz, "respaldos").isDirectory)
    }

    // --- juegosAbiertos ---

    @Test
    fun `juegosAbiertos lee la lista de procesos de verdad por exec y la parsea`() {
        val psDeVerdad = "/usr/local/bin/retroarch -L /home/ark/.config/retroarch/cores/mgba_rumble_libretro.so " +
            "/roms/gba/Pokemon Edicion Rojo Fuego.gba"
        abrirSesion(comandos = FabricaDeComandosFalsa(salidaDePs = psDeVerdad)) { sesion, _ ->
            val juegos = sesion.juegosAbiertos()

            assertEquals(1, juegos.size)
            assertEquals(JuegoAbierto("retroarch", "mgba_rumble", "/roms/gba/Pokemon Edicion Rojo Fuego.gba"), juegos.single())
        }
    }

    @Test
    fun `juegosAbiertos da vacio si no hay ningun retroarch corriendo`() {
        abrirSesion(comandos = FabricaDeComandosFalsa(salidaDePs = "ARGS\n/sbin/init\n-bash\n")) { sesion, _ ->
            assertTrue(sesion.juegosAbiertos().isEmpty())
        }
    }

    // --- ajustesRetroArch ---

    @Test
    fun `ajustesRetroArch lee solo los cfg que existen y resuelve el home de verdad`() = abrirSesion { sesion, raiz ->
        val carpetaCfg = File(raiz, "home/ark/.config/retroarch").apply { mkdirs() }
        File(carpetaCfg, "retroarch.cfg").writeText(
            """
            savestate_auto_load = "true"
            savestate_auto_save = "true"
            savefiles_in_content_dir = "true"
            savestates_in_content_dir = "true"
            savefile_directory = "~/.config/retroarch/saves"
            savestate_directory = "~/.config/retroarch/states"
            autosave_interval = "0"
            """.trimIndent(),
        )

        val ajustes = sesion.ajustesRetroArch()

        assertEquals(1, ajustes.size)
        with(ajustes.single()) {
            assertEquals("/home/ark/.config/retroarch/retroarch.cfg", archivo)
            assertEquals(true, cargaEstadoAutomatico)
            assertEquals(true, guardaEstadoAutomatico)
            assertEquals(true, partidasJuntoALaRom)
            assertEquals(true, estadosJuntoALaRom)
            assertEquals(0, intervaloDeAutoguardado)
            assertEquals("/home/ark/.config/retroarch/saves", carpetaDePartidas)
            assertEquals("/home/ark/.config/retroarch/states", carpetaDeEstados)
        }
    }

    @Test
    fun `ajustesRetroArch lee los dos cfg (64 y 32 bits) si los dos existen`() = abrirSesion { sesion, raiz ->
        File(raiz, "home/ark/.config/retroarch").apply { mkdirs() }
        File(raiz, "home/ark/.config/retroarch/retroarch.cfg").writeText("""autosave_interval = "0"""" + "\n")
        File(raiz, "home/ark/.config/retroarch32").apply { mkdirs() }
        File(raiz, "home/ark/.config/retroarch32/retroarch.cfg").writeText("""autosave_interval = "10"""" + "\n")

        val ajustes = sesion.ajustesRetroArch()

        assertEquals(setOf(0, 10), ajustes.map { it.intervaloDeAutoguardado }.toSet())
    }

    @Test
    fun `ajustesRetroArch da vacio si no hay ningun cfg`() = abrirSesion { sesion, _ ->
        assertTrue(sesion.ajustesRetroArch().isEmpty())
    }

    // --- rutasIntermedias / unirRutaRemota (puros, no necesitan servidor) ---

    @Test
    fun `rutasIntermedias arma cada nivel de la ruta absoluta, del mas corto al mas largo`() {
        assertEquals(listOf("/roms", "/roms/gba", "/roms/gba/respaldos"), rutasIntermedias("/roms/gba/respaldos"))
    }

    @Test
    fun `rutasIntermedias tambien funciona con una ruta relativa`() {
        assertEquals(listOf("a", "a/b"), rutasIntermedias("a/b"))
    }

    @Test
    fun `unirRutaRemota no duplica la diagonal si la carpeta ya trae una`() {
        assertEquals("/roms/gba/x.srm", unirRutaRemota("/roms/gba/", "x.srm"))
        assertEquals("/roms/gba/x.srm", unirRutaRemota("/roms/gba", "x.srm"))
    }

    // --- infraestructura de la prueba ---

    private fun abrirSesion(
        comandos: FabricaDeComandosFalsa = FabricaDeComandosFalsa(),
        bloque: suspend (SesionDeConsola, File) -> Unit,
    ) {
        val raiz = carpetaTemporal.newFolder()
        ServidorSshDePrueba(raiz.toPath(), comandos = comandos).iniciar().use { servidor ->
            runBlocking {
                ConectorDeConsolaJsch(tiempoDeConexionMs = 3_000)
                    .conectar(Candidata("127.0.0.1", servidor.puerto, "rndis0"), Credenciales("ark", "ark"), huellaFijada = null)
                    .use { sesion -> bloque(sesion, raiz) }
            }
        }
    }

    private fun marcarInmutable(archivo: File) {
        check(ProcessBuilder("chflags", "uchg", archivo.absolutePath).start().waitFor() == 0) { "no se pudo marcar inmutable" }
    }

    private fun quitarInmutable(archivo: File) {
        ProcessBuilder("chflags", "nouchg", archivo.absolutePath).start().waitFor()
    }
}
