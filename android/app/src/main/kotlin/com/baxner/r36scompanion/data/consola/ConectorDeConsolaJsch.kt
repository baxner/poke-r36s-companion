package com.baxner.r36scompanion.data.consola

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entra a la consola por SSH con JSch (`com.github.mwiede:jsch`; BouncyCastle para ED25519, ver
 * abajo). Dos decisiones que vale la pena dejar escritas:
 *
 * 1. **ED25519 siempre por BouncyCastle, nunca por la plataforma.** JSch trae dos implementaciones
 *    de la firma `ssh-ed25519`: [com.jcraft.jsch.jce.SignatureEd25519] (sobre el `Signature` de la
 *    JVM) y `com.jcraft.jsch.bc.SignatureEd25519` (sobre BouncyCastle), y por *default* elige la
 *    primera si `JavaVersion.getVersion() >= 15` (así arranca el `Hashtable` de config de
 *    `JSch.<clinit>`, visto decompilando el jar). La JVM de las pruebas (JDK 17) cae justo en ese
 *    caso: el JDK trae EdDSA nativo desde la 15 (JEP 339), así que por default **no pasaría por
 *    BouncyCastle para nada** y las pruebas no dirían nada sobre si Android (que no siempre trae
 *    ese `Signature`/`KeyFactory` en su propio `Security`) puede verificar la llave de la consola.
 *    Por eso aquí se fija `"ssh-ed25519" -> "com.jcraft.jsch.bc.SignatureEd25519"` en la propia
 *    sesión (no en `JSch.setConfig` global, para no afectar nada fuera de esta conexión): así el
 *    teléfono y las pruebas ejercitan la misma clase.
 * 2. **Orden de algoritmos de llave de servidor fijo** ([OrdenDeAlgoritmosDeLlave]): para que la
 *    huella sea estable entre conexiones aunque la consola algún día ofrezca más de un tipo de
 *    llave, en vez de confiar en que la lista por default de JSch no cambie entre versiones de la
 *    librería. Hoy la consola real solo trae ED25519 (`docs/plan.md`), así que en la práctica
 *    siempre se usa esa; el resto queda de respaldo, en el mismo orden de preferencia que trae
 *    JSch por default (ED25519, ECDSA, RSA-SHA2), por si algún día hace falta.
 */
class ConectorDeConsolaJsch(private val tiempoDeConexionMs: Int = 8_000) : ConectorDeConsola {

    override suspend fun conectar(candidata: Candidata, credenciales: Credenciales, huellaFijada: String?): SesionDeConsola =
        withContext(Dispatchers.IO) {
            val repositorio = RepositorioDeHuellaFijada(huellaFijada)
            var sesionCreada: Session? = null
            try {
                val sesion = JSch().getSession(credenciales.usuario, candidata.host, candidata.puerto)
                sesionCreada = sesion
                sesion.setHostKeyRepository(repositorio)
                sesion.setPassword(credenciales.contrasena)
                sesion.setConfig("StrictHostKeyChecking", "yes")
                sesion.setConfig("PreferredAuthentications", "password")
                sesion.setConfig("server_host_key", OrdenDeAlgoritmosDeLlave)
                sesion.setConfig(AlgoritmoEd25519, ImplementacionBouncyCastleEd25519)
                sesion.setConfig(AlgoritmoEd448, ImplementacionBouncyCastleEd448)
                sesion.connect(tiempoDeConexionMs)

                // Si connect() no lanzó, el host key ya pasó por check() (ver RepositorioDeHuellaFijada):
                // antes de la fase de autenticación, nunca después (así nunca manda la contraseña
                // a algo con una huella distinta a la fijada).
                val aceptada = checkNotNull(repositorio.aceptada) {
                    "JSch conectó sin pasar por HostKeyRepository.check(): no debería pasar nunca"
                }

                val sftp = sesion.openChannel("sftp") as ChannelSftp
                sftp.connect(tiempoDeConexionMs)

                val consola = Consola(
                    host = candidata.host,
                    puerto = candidata.puerto,
                    huella = aceptada.huella,
                    algoritmoDeLlave = aceptada.algoritmo,
                    nombreDeEquipo = nombreDelEquipo(sesion),
                )
                SesionDeConsolaJsch(sesion, sftp, consola)
            } catch (e: Exception) {
                runCatching { sesionCreada?.disconnect() }
                throw traducirFalloDeConexion(e, repositorio, huellaFijada)
            }
        }

    /** `hostname` por exec (o `uname -n` si no existe); si ninguno contesta, el propio host/IP. */
    private fun nombreDelEquipo(sesionSsh: Session): String {
        fun salidaDe(comando: String): String? =
            runCatching { ejecutarComando(sesionSsh, comando, tiempoDeConexionMs) }
                .getOrNull()
                ?.takeIf { it.estado == 0 }
                ?.salida
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        return salidaDe("hostname") ?: salidaDe("uname -n") ?: sesionSsh.host
    }

    companion object {
        /** Ver el punto 2 del KDoc de la clase. */
        internal const val OrdenDeAlgoritmosDeLlave =
            "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256"
        private const val AlgoritmoEd25519 = "ssh-ed25519"
        private const val AlgoritmoEd448 = "ssh-ed448"
        private const val ImplementacionBouncyCastleEd25519 = "com.jcraft.jsch.bc.SignatureEd25519"
        private const val ImplementacionBouncyCastleEd448 = "com.jcraft.jsch.bc.SignatureEd448"
    }
}

/** Traduce lo que lanzó JSch (o lo que haya pasado antes) a un [ErrorDeConsola] con sentido para la UI. */
private fun traducirFalloDeConexion(e: Exception, repositorio: RepositorioDeHuellaFijada, huellaFijada: String?): ErrorDeConsola {
    val huellaRecibida = repositorio.huellaDistinta
    if (huellaRecibida != null) {
        return ErrorDeConsola.HuellaDistinta(esperada = huellaFijada.orEmpty(), recibida = huellaRecibida)
    }
    val mensaje = e.message.orEmpty()
    if (mensaje.startsWith("Auth fail") || mensaje.startsWith("Auth cancel")) {
        return ErrorDeConsola.CredencialesIncorrectas(e)
    }
    return ErrorDeConsola.SinConexion(mensaje.ifBlank { "no se pudo conectar con la consola" }, e)
}

/**
 * El [HostKeyRepository] de un intento de conexión (uno nuevo por intento: dos conexiones a la vez
 * no comparten estado). Sin [huellaFijada] (primera vez) acepta lo que sea y guarda qué recibió en
 * [aceptada]. Con [huellaFijada], compara: si coincide hace lo mismo que la primera vez; si no,
 * devuelve [HostKeyRepository.CHANGED] **sin llamar a nada más** — con `StrictHostKeyChecking=yes`
 * eso le basta a JSch para cortar la conexión ahí mismo, durante el intercambio de llaves y antes
 * de intentar ninguna autenticación (`Session.connect` llama a este `check` antes de tocar
 * `UserAuth`), así que [ConectorDeConsolaJsch.conectar] nunca llega a mandar la contraseña.
 */
private class RepositorioDeHuellaFijada(private val huellaFijada: String?) : HostKeyRepository {
    var aceptada: HuellaAceptada? = null
        private set
    var huellaDistinta: String? = null
        private set

    override fun check(host: String, key: ByteArray): Int {
        val huella = huellaSha256DeLlave(key)
        if (huellaFijada != null && huellaFijada != huella) {
            huellaDistinta = huella
            return HostKeyRepository.CHANGED
        }
        aceptada = HuellaAceptada(huella, algoritmoDeLlaveSsh(key))
        return HostKeyRepository.OK
    }

    override fun add(hostkey: HostKey?, userinfo: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String? = null
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

private data class HuellaAceptada(val huella: String, val algoritmo: String)
