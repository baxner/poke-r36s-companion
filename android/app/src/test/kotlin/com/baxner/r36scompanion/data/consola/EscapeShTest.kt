package com.baxner.r36scompanion.data.consola

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * No basta con mirar el string escapado: aquí se manda de verdad a un `/bin/sh` (el mismo que usa
 * la consola) y se comprueba que recupera el nombre exacto, byte por byte — y que un nombre que
 * intenta meter un comando (`$(...)`, backticks, `;`, `#`) no ejecuta nada, solo se imprime igual
 * que cualquier otro.
 */
class EscapeShTest {
    private val nombresReales = listOf(
        "Pokemon - Version Rouge Feu (F).srm",
        "Sound of Thunder, A (USA) (En,Fr,De,Es,It).srm",
        "Yu-Gi-Oh! GX - Duel Academy (USA).srm",
        "Pokémon Esmeralda - la del usuario's.srm", // acentos y una comilla simple
        "simple.srm",
        "",
        "'",
        "''",
        "a\nb",
        "   espacios al borde   ",
    )

    @Test
    fun `un shell de verdad recupera cada nombre real exacto, byte por byte`() {
        for (nombre in nombresReales) {
            assertEquals("para el nombre: ${nombre.replace("\n", "\\n")}", nombre, ejecutarEnShDeVerdad(nombre))
        }
    }

    @Test
    fun `envuelve en comillas simples lo que no tiene nada especial`() {
        assertEquals("'sin nada especial'", comillaSimpleSh("sin nada especial"))
    }

    @Test
    fun `una comilla simple se parte en tres piezas`() {
        assertEquals("'can'\\''t'", comillaSimpleSh("can't"))
    }

    @Test
    fun `un nombre que intenta inyectar un comando no ejecuta nada`() {
        val carpeta = Files.createTempDirectory("escape-sh-test")
        val marca = carpeta.resolve("se-ejecuto").toFile()
        val intento = "\$(touch ${marca.absolutePath}) \$(echo inyectado) \$(touch ${marca.absolutePath}); touch ${marca.absolutePath} #"

        val recuperado = ejecutarEnShDeVerdad(intento)

        assertEquals(intento, recuperado)
        assertFalse("el shell SÍ ejecutó algo de adentro del nombre escapado", marca.exists())
    }

    /** `printf '%s' <comillaSimpleSh(valor)>` por un `/bin/sh` de verdad: el mismo patrón que usa `SesionDeConsolaJsch.forzarSync`. */
    private fun ejecutarEnShDeVerdad(valor: String): String {
        val proceso = ProcessBuilder("/bin/sh", "-c", "printf '%s' " + comillaSimpleSh(valor)).start()
        val salida = proceso.inputStream.readBytes().toString(Charsets.UTF_8)
        val error = proceso.errorStream.readBytes().toString(Charsets.UTF_8)
        val estado = proceso.waitFor()
        check(estado == 0) { "sh salió con $estado; stderr: $error" }
        return salida
    }
}
