package com.baxner.r36scompanion.data.consola

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandDirectErrorStreamAware
import org.apache.sshd.server.command.CommandDirectInputStreamAware
import org.apache.sshd.server.command.CommandDirectOutputStreamAware
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory

/**
 * Un servidor SSH/SFTP de verdad, en proceso, para probar [ConectorDeConsolaJsch] y
 * [SesionDeConsolaJsch] sin tocar la consola real (ver `AGENTS.md`: "las pruebas van contra
 * servidores falsos en proceso"). Raíz de archivos en [raiz]; usuario y contraseña configurables
 * (por default los de fábrica de ArkOS, `ark`/`ark`); `exec` resuelto por [comandos] (por default,
 * [FabricaDeComandosFalsa]).
 *
 * La llave ED25519 del servidor sale de [SimpleGeneratorHostKeyProvider] (la clase que trae MINA
 * para justo esto: un host key de prueba, nuevo o guardado en un archivo) con `algorithm = "EdDSA"`.
 * **No** sirve generarla aparte con el `KeyPairGenerator.getInstance("Ed25519")` nativo del JDK (ni
 * a mano con BouncyCastle sin pasar por las clases de MINA): esas llaves, aunque válidas, no las
 * reconoce el propio `AbstractGeneratorHostKeyProvider`/`KeyUtils` de MINA al mandar la firma
 * (`IllegalArgumentException: Not an EDDSA public key`, o `IllegalStateException: generator not
 * correctly initialized` si se intenta inicializar el `KeyPairGenerator` genérico "EdDSA" a mano) y
 * la conexión se cae antes de mandar el banner SSH. No es un problema de [ConectorDeConsolaJsch] ni
 * de BouncyCastle para el cliente (que sigue usando `com.jcraft.jsch.bc.SignatureEd25519` para
 * verificarla, como siempre): es nada más que el servidor de prueba tiene que generar y cargar la
 * llave con las clases que MINA mismo entiende.
 */
internal class ServidorSshDePrueba(
    raiz: Path,
    usuario: String = "ark",
    contrasena: String = "ark",
    val comandos: FabricaDeComandosFalsa = FabricaDeComandosFalsa(),
) : AutoCloseable {
    val contadorDeIntentosDeContrasena = AtomicInteger(0)

    private val servidor: SshServer = SshServer.setUpDefaultServer().also { s ->
        s.setPort(0)
        s.keyPairProvider = SimpleGeneratorHostKeyProvider(Files.createTempFile("consola-prueba-hostkey-", ".ser")).apply {
            algorithm = "EdDSA"
            // El `KeyPairGenerator` genérico "EdDSA" de BouncyCastle (no hay net.i2p.crypto.eddsa en
            // el classpath, solo BC) no trae una curva por default: sin esto truena con
            // "generator not correctly initialized". 255 = Ed25519 (448 sería Ed448).
            keySize = 255
        }
        s.passwordAuthenticator = PasswordAuthenticator { usuarioRecibido, contrasenaRecibida, _ ->
            contadorDeIntentosDeContrasena.incrementAndGet()
            usuarioRecibido == usuario && contrasenaRecibida == contrasena
        }
        s.setCommandFactory(comandos)
        s.subsystemFactories = listOf(SftpSubsystemFactory())
        s.fileSystemFactory = VirtualFileSystemFactory(raiz)
    }

    val puerto: Int get() = servidor.port

    fun iniciar(): ServidorSshDePrueba = apply { servidor.start() }

    override fun close() {
        runCatching { servidor.stop(true) }
    }
}

/**
 * Responde `exec` sin tocar ningún proceso de verdad: `hostname`/`uname -n`, `ps -eo args` (lo que
 * [salidaDePs] diga), `echo $HOME` (con [home]) y cualquier `sync...` como éxito silencioso.
 * Cualquier otro comando "no existe" (estado 127), como un shell de verdad. [comandosRecibidos]
 * guarda todo lo que le pidieron, para comprobar en las pruebas que `escribirSeguro` de verdad
 * llama a `sync` con la ruta bien escapada.
 */
internal class FabricaDeComandosFalsa(
    private val home: String = "/home/ark",
    private val salidaDePs: String = "ARGS\n/sbin/init\n",
) : CommandFactory {
    val comandosRecibidos: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun createCommand(channel: ChannelSession, command: String): Command {
        comandosRecibidos += command
        val (texto, estado) = respuestaPara(command)
        return ComandoFalso(texto, estado)
    }

    private fun respuestaPara(comando: String): Pair<String, Int> = when {
        comando == "hostname" -> "rg351mp\n" to 0
        comando == "uname -n" -> "rg351mp\n" to 0
        comando == "ps -eo args" -> salidaDePs to 0
        comando == "echo \$HOME" -> "$home\n" to 0
        comando.startsWith("sync") -> "" to 0
        else -> "" to 127
    }
}

/** Un `Command` de MINA que no hace nada de verdad: solo escribe una respuesta fija y sale. */
private class ComandoFalso(
    private val textoDeSalida: String,
    private val estadoDeSalida: Int,
) : Command, CommandDirectInputStreamAware, CommandDirectOutputStreamAware, CommandDirectErrorStreamAware {
    private lateinit var salida: OutputStream
    private lateinit var callback: ExitCallback

    override fun setInputStream(input: InputStream) = Unit
    override fun setOutputStream(output: OutputStream) {
        salida = output
    }
    override fun setErrorStream(err: OutputStream) = Unit
    override fun setExitCallback(cb: ExitCallback) {
        callback = cb
    }

    override fun start(channel: ChannelSession, env: Environment) {
        salida.write(textoDeSalida.toByteArray(Charsets.UTF_8))
        salida.flush()
        callback.onExit(estadoDeSalida)
    }

    override fun destroy(channel: ChannelSession) = Unit
}
