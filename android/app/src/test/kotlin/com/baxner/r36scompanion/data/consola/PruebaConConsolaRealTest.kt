package com.baxner.r36scompanion.data.consola

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Contra la consola de verdad del usuario, a mano: solo corre si `PR36C_CONSOLA=host:puerto` (por
 * ejemplo el túnel adb → S24 → consola en `127.0.0.1:8022`). Nunca va en las pruebas normales.
 *
 * Solo lee las partidas y los ajustes. Lo único que escribe es una carpeta oculta de prueba,
 * `/roms/gba/.pr36c/prueba-<hora>/`, que hay que borrar a mano al terminar (la sesión no tiene
 * "borrar" a propósito). Ver docs/plan.md, "Pruebas con la consola real".
 */
class PruebaConConsolaRealTest {
    private val destino = System.getenv("PR36C_CONSOLA")
    private val conector = ConectorDeConsolaJsch()

    private fun candidata(): Candidata {
        assumeTrue("sin PR36C_CONSOLA: no hay consola real para probar", !destino.isNullOrBlank())
        val (host, puerto) = destino!!.split(":").let { it[0] to (it.getOrNull(1)?.toInt() ?: PuertoSsh) }
        return Candidata(host, puerto, "túnel")
    }

    @Test
    fun conectaFijaLaHuellaYLeeLasPartidas() = runBlocking {
        val candidata = candidata()
        val primera = conector.conectar(candidata, Credenciales(), huellaFijada = null)
        val huella = primera.consola.huella
        println("consola: ${primera.consola}")
        primera.use { sesion ->
            assertEquals("rg351mp", sesion.consola.nombreDeEquipo)

            val partidas = sesion.listar("/roms/gba").filter { it.nombre.endsWith(".srm") }
            println("partidas en la consola: ${partidas.size}")
            assertTrue("esperaba las partidas del usuario", partidas.size >= 11)

            // Comparar con el primer respaldo (2026-10-19): las que no se hayan jugado desde entonces son idénticas.
            val sumas = File("../../muestras/2026-10-19/SHA256SUMS").takeIf { it.exists() }?.readLines()
                ?.associate { linea -> linea.substringAfter("  ") to linea.substringBefore("  ") } ?: emptyMap()
            for (partida in partidas) {
                val bytes = sesion.leer(partida.ruta)
                assertEquals(partida.tamano, bytes.size.toLong())
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val antes = sumas[partida.nombre]
                println("  ${partida.nombre}: ${bytes.size} B, ${if (antes == null) "sin respaldo previo" else if (antes == sha) "igual al respaldo" else "CAMBIÓ desde el respaldo"}")
            }

            println("juegos abiertos: ${sesion.juegosAbiertos()}")
            val ajustes = sesion.ajustesRetroArch()
            ajustes.forEach { println("ajustes: $it") }
            assertTrue(ajustes.any { it.cargaEstadoAutomatico == true })
        }

        // Con la huella fijada entra igual…
        conector.conectar(candidata, Credenciales(), huellaFijada = huella).use { assertEquals(huella, it.consola.huella) }
        // …y con otra, no (sin mandar la contraseña).
        try {
            conector.conectar(candidata, Credenciales(), huellaFijada = "SHA256:otraConsolaQueNoEsLaReal")
            fail("tenía que rechazar una huella distinta")
        } catch (e: ErrorDeConsola.HuellaDistinta) {
            assertEquals(huella, e.recibida)
        }
    }

    @Test
    fun escribeSeguroEnUnaCarpetaOcultaDePrueba() = runBlocking {
        val carpeta = "/roms/gba/.pr36c/prueba-${System.currentTimeMillis()}"
        println("carpeta de prueba (borrar a mano al terminar): $carpeta")
        conector.conectar(candidata(), Credenciales(), huellaFijada = null).use { sesion ->
            sesion.crearCarpetas(carpeta)
            val ruta = "$carpeta/Partida de prueba (F), con comas.srm"

            val primera = ByteArray(131_072) { (it % 251).toByte() }
            sesion.escribirSeguro(ruta, primera)
            assertArrayEquals(primera, sesion.leer(ruta))

            val segunda = ByteArray(131_072) { (it % 239).toByte() }
            sesion.escribirSeguro(ruta, segunda) // reemplazo: el rename tiene que funcionar en exFAT
            assertArrayEquals(segunda, sesion.leer(ruta))

            val sobras = sesion.listar(carpeta).map { it.nombre }.filter { "pr36c" in it }
            assertTrue("no tienen que quedar temporales: $sobras", sobras.isEmpty())

            val movida = "$carpeta/movida.srm"
            sesion.mover(ruta, movida)
            assertNull(sesion.info(ruta))
            assertNotNull(sesion.info(movida))
            try {
                sesion.crearCarpetas("$carpeta/otra")
                sesion.escribirSeguro("$carpeta/otra/x.srm", primera)
                sesion.mover("$carpeta/otra/x.srm", movida)
                fail("mover no debe pisar un archivo que ya existe")
            } catch (_: ErrorDeConsola.YaExiste) {
            }
            println("contenido final: ${sesion.listar(carpeta).map { it.nombre }}")
        }
    }
}
