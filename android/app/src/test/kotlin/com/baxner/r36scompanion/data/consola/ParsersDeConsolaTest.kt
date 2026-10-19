package com.baxner.r36scompanion.data.consola

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersDeConsolaTest {

    // --- parsearJuegosAbiertos ---

    @Test
    fun `reconoce retroarch de 64 bits con una ROM con espacios, tal cual la manda la consola`() {
        val psEoArgs = """
            ARGS
            /sbin/init
            /usr/sbin/sshd: ark@pts/0
            -bash
            ps -eo args
            /usr/local/bin/retroarch -L /home/ark/.config/retroarch/cores/mgba_rumble_libretro.so /roms/gba/Pokemon Edicion Rojo Fuego.gba
        """.trimIndent()

        val juegos = parsearJuegosAbiertos(psEoArgs)

        assertEquals(1, juegos.size)
        assertEquals(JuegoAbierto("retroarch", "mgba_rumble", "/roms/gba/Pokemon Edicion Rojo Fuego.gba"), juegos.single())
    }

    @Test
    fun `reconoce retroarch32 con su propia carpeta de nucleos`() {
        val psEoArgs = "/usr/local/bin/retroarch32 -L /home/ark/.config/retroarch32/cores/mgba_libretro.so " +
            "/roms/gba/Sound of Thunder, A (USA) (En,Fr,De,Es,It).gba"

        val juegos = parsearJuegosAbiertos(psEoArgs)

        assertEquals(1, juegos.size)
        with(juegos.single()) {
            assertEquals("retroarch32", emulador)
            assertEquals("mgba", nucleo)
            assertEquals("/roms/gba/Sound of Thunder, A (USA) (En,Fr,De,Es,It).gba", rom)
        }
    }

    @Test
    fun `sin retroarch corriendo da una lista vacia, sin que ps o grep cuenten`() {
        val psEoArgs = """
            ARGS
            /sbin/init
            -bash
            ps -eo args
            grep retroarch
            sudo /usr/local/bin/perfmax
        """.trimIndent()

        assertTrue(parsearJuegosAbiertos(psEoArgs).isEmpty())
    }

    @Test
    fun `reconoce mas de un juego abierto a la vez`() {
        val psEoArgs = """
            /usr/local/bin/retroarch -L /home/ark/.config/retroarch/cores/mgba_rumble_libretro.so /roms/gba/Juego Uno.gba
            /usr/local/bin/retroarch32 -L /home/ark/.config/retroarch32/cores/fceumm_libretro.so /roms/nes/Juego Dos.nes
        """.trimIndent()

        val juegos = parsearJuegosAbiertos(psEoArgs)

        assertEquals(2, juegos.size)
        assertEquals(setOf("retroarch", "retroarch32"), juegos.map { it.emulador }.toSet())
    }

    // --- parsearRetroArchCfg / ajustesDesdeMapa ---

    private val cfgReal = """
        savestate_auto_load = "true"
        savestate_auto_save = "true"
        savefiles_in_content_dir = "true"
        savestates_in_content_dir = "true"
        savefile_directory = "~/.config/retroarch/saves"
        savestate_directory = "~/.config/retroarch/states"
        autosave_interval = "0"
    """.trimIndent()

    @Test
    fun `parsea el cfg real de la consola a pares clave-valor`() {
        val valores = parsearRetroArchCfg(cfgReal)

        assertEquals("true", valores["savestate_auto_load"])
        assertEquals("0", valores["autosave_interval"])
        assertEquals("~/.config/retroarch/saves", valores["savefile_directory"])
    }

    @Test
    fun `ignora lineas vacias y las que no calzan con clave-valor, sin tronar`() {
        val conRuido = "\n$cfgReal\n# esto no es un comentario real de RetroArch pero tampoco debe tronar\nalgo_raro sin comillas\n"

        val valores = parsearRetroArchCfg(conRuido)

        assertEquals("true", valores["savestate_auto_save"])
        assertTrue("algo_raro" !in valores)
    }

    @Test
    fun `ajustesDesdeMapa arma el DTO completo del cfg real de la consola`() {
        val ajustes = ajustesDesdeMapa("/home/ark/.config/retroarch/retroarch.cfg", parsearRetroArchCfg(cfgReal))

        assertEquals(
            AjustesRetroArch(
                archivo = "/home/ark/.config/retroarch/retroarch.cfg",
                cargaEstadoAutomatico = true,
                guardaEstadoAutomatico = true,
                partidasJuntoALaRom = true,
                estadosJuntoALaRom = true,
                carpetaDePartidas = "~/.config/retroarch/saves",
                carpetaDeEstados = "~/.config/retroarch/states",
                intervaloDeAutoguardado = 0,
            ),
            ajustes,
        )
    }

    @Test
    fun `ajuste que no aparece en el archivo queda null, no false`() {
        val ajustes = ajustesDesdeMapa("x.cfg", mapOf("autosave_interval" to "0"))

        assertNull(ajustes.cargaEstadoAutomatico)
        assertNull(ajustes.carpetaDePartidas)
        assertEquals(0, ajustes.intervaloDeAutoguardado)
    }

    @Test
    fun `booleano se lee sin importar mayusculas`() {
        val ajustes = ajustesDesdeMapa("x.cfg", mapOf("savestate_auto_load" to "TRUE", "savestate_auto_save" to "False"))

        assertEquals(true, ajustes.cargaEstadoAutomatico)
        assertEquals(false, ajustes.guardaEstadoAutomatico)
    }

    // --- expandirHome ---

    @Test
    fun `expande el home al inicio de una ruta con virgulilla`() {
        assertEquals("/home/ark/.config/retroarch/saves", expandirHome("~/.config/retroarch/saves", "/home/ark"))
    }

    @Test
    fun `no toca una ruta que ya es absoluta`() {
        assertEquals("/otra/ruta/saves", expandirHome("/otra/ruta/saves", "/home/ark"))
    }

    @Test
    fun `null se queda null`() {
        assertNull(expandirHome(null, "/home/ark"))
    }

    @Test
    fun `no le importa si el home trae diagonal al final`() {
        assertEquals("/home/ark/.config/retroarch/saves", expandirHome("~/.config/retroarch/saves", "/home/ark/"))
    }
}
