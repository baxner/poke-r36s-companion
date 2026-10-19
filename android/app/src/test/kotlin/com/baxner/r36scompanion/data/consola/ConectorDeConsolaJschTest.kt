package com.baxner.r36scompanion.data.consola

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [ConectorDeConsolaJsch] contra un [ServidorSshDePrueba] de verdad (Apache MINA SSHD, en
 * proceso): la huella (primera vez, fijada igual, fijada distinta sin mandar la contraseña) y las
 * credenciales. Nunca toca la consola real, como pide `AGENTS.md`.
 */
class ConectorDeConsolaJschTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private val conector = ConectorDeConsolaJsch(tiempoDeConexionMs = 3_000)
    private val credencialesDeFabrica = Credenciales(usuario = "ark", contrasena = "ark")

    @Test
    fun `primera conexion no trae huella fijada, acepta y la devuelve`() = runBlocking {
        ServidorSshDePrueba(carpetaTemporal.newFolder("consola").toPath()).iniciar().use { servidor ->
            conector.conectar(candidataDe(servidor), credencialesDeFabrica, huellaFijada = null).use { sesion ->
                assertTrue(sesion.consola.huella.matches(Regex("^SHA256:[A-Za-z0-9+/]+$")))
                assertEquals("ssh-ed25519", sesion.consola.algoritmoDeLlave)
                assertEquals("rg351mp", sesion.consola.nombreDeEquipo)
                assertEquals(servidor.puerto, sesion.consola.puerto)
                assertEquals(1, servidor.contadorDeIntentosDeContrasena.get())
            }
        }
    }

    @Test
    fun `huella fijada que coincide conecta igual que la primera vez`() = runBlocking {
        ServidorSshDePrueba(carpetaTemporal.newFolder("consola").toPath()).iniciar().use { servidor ->
            val huella = conector.conectar(candidataDe(servidor), credencialesDeFabrica, huellaFijada = null)
                .use { it.consola.huella }

            conector.conectar(candidataDe(servidor), credencialesDeFabrica, huellaFijada = huella).use { sesion ->
                assertEquals(huella, sesion.consola.huella)
            }
        }
    }

    @Test
    fun `huella distinta se rechaza antes de autenticar - la contrasena nunca se manda`() = runBlocking {
        val huellaVieja = ServidorSshDePrueba(carpetaTemporal.newFolder("viejo").toPath()).iniciar().use { servidorViejo ->
            conector.conectar(candidataDe(servidorViejo), credencialesDeFabrica, huellaFijada = null).use { it.consola.huella }
        }

        ServidorSshDePrueba(carpetaTemporal.newFolder("nuevo").toPath()).iniciar().use { servidorNuevo ->
            // Hay que saber la huella real del servidor "nuevo" (otra llave, generada aparte) para
            // poder comprobar que el error trae la huella que de verdad mandó.
            val huellaNueva = conector.conectar(candidataDe(servidorNuevo), credencialesDeFabrica, huellaFijada = null)
                .use { it.consola.huella }
            assertNotEquals("las dos llaves son distintas: si coincidieran el escenario no probaría nada", huellaVieja, huellaNueva)
            val intentosDeContrasenaHastaAqui = servidorNuevo.contadorDeIntentosDeContrasena.get()

            val error = assertFallaCon<ErrorDeConsola.HuellaDistinta> {
                conector.conectar(candidataDe(servidorNuevo), credencialesDeFabrica, huellaFijada = huellaVieja)
            }

            assertEquals(huellaVieja, error.esperada)
            assertEquals(huellaNueva, error.recibida)
            assertEquals(
                "la contraseña no se debió mandar en el intento con huella distinta",
                intentosDeContrasenaHastaAqui,
                servidorNuevo.contadorDeIntentosDeContrasena.get(),
            )
        }
    }

    @Test
    fun `contrasena incorrecta da CredencialesIncorrectas`() = runBlocking {
        ServidorSshDePrueba(carpetaTemporal.newFolder("consola").toPath()).iniciar().use { servidor ->
            val error = assertFallaCon<ErrorDeConsola.CredencialesIncorrectas> {
                conector.conectar(candidataDe(servidor), Credenciales(usuario = "ark", contrasena = "mala"), huellaFijada = null)
            }
            assertTrue(error.message!!.isNotBlank())
        }
    }

    @Test
    fun `sin nadie escuchando da SinConexion`() = runBlocking {
        val puertoLibre = java.net.ServerSocket(0).use { it.localPort }

        val error = assertFallaCon<ErrorDeConsola.SinConexion> {
            conector.conectar(Candidata("127.0.0.1", puertoLibre, "rndis0"), credencialesDeFabrica, huellaFijada = null)
        }

        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun `la implementacion de BouncyCastle para ed25519 existe y es una Signature de JSch`() {
        val clase = Class.forName("com.jcraft.jsch.bc.SignatureEd25519")
        assertTrue(com.jcraft.jsch.Signature::class.java.isAssignableFrom(clase))
        assertFalse(clase.getDeclaredConstructor().newInstance() == null)
    }

    private fun candidataDe(servidor: ServidorSshDePrueba) = Candidata("127.0.0.1", servidor.puerto, "rndis0")
}

/** Corre [bloque] y afirma que lanzó justo un [T] (y no otra cosa, ni nada). Lo devuelve para seguir revisándolo. */
internal inline fun <reified T : Throwable> assertFallaCon(crossinline bloque: suspend () -> Unit): T {
    val resultado = runCatching { runBlocking { bloque() } }
    val excepcion = resultado.exceptionOrNull()
    assertTrue("se esperaba que lanzara ${T::class.simpleName} pero no lanzó nada", excepcion != null)
    assertTrue(
        "se esperaba ${T::class.simpleName} pero lanzó ${excepcion?.let { it::class.qualifiedName }}: ${excepcion?.message}",
        excepcion is T,
    )
    return excepcion as T
}
