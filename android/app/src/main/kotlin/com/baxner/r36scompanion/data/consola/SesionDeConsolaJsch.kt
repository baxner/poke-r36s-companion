package com.baxner.r36scompanion.data.consola

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Una conexión abierta con la consola: [sesionSsh] para `exec` (canales independientes, se pueden
 * abrir varios a la vez sobre la misma sesión) y [sftp] para todo lo de archivos (un solo canal,
 * que JSch no espera que se use desde dos corrutinas a la vez: [turno] lo serializa, igual que
 * [com.baxner.r36scompanion.data.motor.MotorPartidas] con el motor).
 */
internal class SesionDeConsolaJsch(
    private val sesionSsh: Session,
    private val sftp: ChannelSftp,
    override val consola: Consola,
) : SesionDeConsola {
    private val turno = Mutex()

    override suspend fun listar(carpeta: String): List<ArchivoRemoto> = withContext(Dispatchers.IO) {
        turno.withLock {
            val entradas = try {
                sftp.ls(carpeta)
            } catch (e: SftpException) {
                throw traducir(e, carpeta)
            }
            entradas
                .filterIsInstance<ChannelSftp.LsEntry>()
                .filter { it.filename != "." && it.filename != ".." }
                .map { entrada -> archivoRemotoDe(unirRutaRemota(carpeta, entrada.filename), entrada.attrs) }
        }
    }

    override suspend fun info(ruta: String): ArchivoRemoto? = withContext(Dispatchers.IO) {
        turno.withLock { attrsOCrudo(ruta)?.let { archivoRemotoDe(ruta, it) } }
    }

    override suspend fun leer(ruta: String, maximo: Long): ByteArray = withContext(Dispatchers.IO) {
        turno.withLock {
            attrsOCrudo(ruta) ?: throw ErrorDeConsola.NoExiste(ruta)
            try {
                descargarBytes(ruta, maximo)
            } catch (e: SftpException) {
                throw traducir(e, ruta)
            }
        }
    }

    override suspend fun escribirSeguro(ruta: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        turno.withLock {
            val temporal = ruta + ExtensionTemporal
            val anterior = ruta + ExtensionAnterior

            repararIntentoPrevio(ruta, anterior, temporal)
            subirYVerificar(temporal, bytes)

            val existiaOriginal = attrsOCrudo(ruta) != null
            if (existiaOriginal) {
                try {
                    sftp.rename(ruta, anterior)
                } catch (e: Exception) {
                    eliminarSiExiste(temporal)
                    throw traducir(e, ruta)
                }
            }

            try {
                sftp.rename(temporal, ruta)
            } catch (e: Exception) {
                if (!existiaOriginal) {
                    eliminarSiExiste(temporal)
                    throw traducir(e, ruta)
                }
                runCatching { sftp.rename(anterior, ruta) }
                if (attrsOCrudo(ruta) != null) {
                    eliminarSiExiste(temporal)
                    throw traducir(e, ruta)
                }
                // Ni el reemplazo ni el deshacer funcionaron: no se toca nada más. El original
                // sigue íntegro bajo `anterior` y lo nuevo bajo `temporal`; no se perdió ninguna
                // partida, pero hace falta mirarlo a mano (por ejemplo con `listar` en la carpeta).
                throw ErrorDeConsola.VerificacionFallida(
                    ruta,
                    "no se pudo escribir $ruta ni restaurar el original solo: quedó a salvo en $anterior (lo nuevo en $temporal)",
                )
            }

            try {
                verificarContenido(ruta, bytes)
            } catch (e: ErrorDeConsola.VerificacionFallida) {
                if (!existiaOriginal) {
                    eliminarSiExiste(ruta)
                    throw e
                }
                runCatching { sftp.rm(ruta) }
                runCatching { sftp.rename(anterior, ruta) }
                if (attrsOCrudo(anterior) == null && attrsOCrudo(ruta) != null) throw e
                throw ErrorDeConsola.VerificacionFallida(
                    ruta,
                    "lo escrito en $ruta no verificó y no se pudo restaurar solo: el original está a salvo en $anterior",
                )
            }

            if (existiaOriginal) eliminarSiExiste(anterior)
            forzarSync(ruta)
        }
    }

    override suspend fun mover(desde: String, hacia: String): Unit = withContext(Dispatchers.IO) {
        turno.withLock {
            if (attrsOCrudo(hacia) != null) throw ErrorDeConsola.YaExiste(hacia)
            try {
                sftp.rename(desde, hacia)
            } catch (e: Exception) {
                if (attrsOCrudo(hacia) != null) throw ErrorDeConsola.YaExiste(hacia)
                throw traducir(e, desde)
            }
        }
    }

    override suspend fun crearCarpetas(carpeta: String): Unit = withContext(Dispatchers.IO) {
        turno.withLock {
            for (parcial in rutasIntermedias(carpeta)) {
                if (attrsOCrudo(parcial)?.isDir == true) continue
                try {
                    sftp.mkdir(parcial)
                } catch (e: Exception) {
                    if (attrsOCrudo(parcial)?.isDir == true) continue // alguien más la creó justo ahora
                    throw traducir(e, parcial)
                }
            }
        }
    }

    override suspend fun juegosAbiertos(): List<JuegoAbierto> = withContext(Dispatchers.IO) {
        parsearJuegosAbiertos(ejecutarComando(sesionSsh, "ps -eo args", TiempoDeComandoMs).salida)
    }

    override suspend fun ajustesRetroArch(): List<AjustesRetroArch> = withContext(Dispatchers.IO) {
        val home = homeDelUsuario()
        listOfNotNull(
            leerAjustesSiExiste("${home.trimEnd('/')}/.config/retroarch/retroarch.cfg", home),
            leerAjustesSiExiste("${home.trimEnd('/')}/.config/retroarch32/retroarch.cfg", home),
        )
    }

    override fun close() {
        runCatching { sftp.exit() }
        runCatching { sesionSsh.disconnect() }
    }

    // --- Lo de abajo son los pasos de arriba, nombrados aparte para que cada uno se lea solo. ---

    private fun attrsOCrudo(ruta: String): SftpATTRS? = try {
        sftp.stat(ruta)
    } catch (e: SftpException) {
        if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw traducir(e, ruta)
    }

    private fun archivoRemotoDe(ruta: String, attrs: SftpATTRS): ArchivoRemoto = ArchivoRemoto(
        ruta = ruta,
        nombre = ruta.substringAfterLast('/'),
        tamano = attrs.size,
        // SFTP manda el mtime como 32 bits sin signo (RFC venidera de SFTPv3, campo ACMODTIME):
        // con `and 0xFFFFFFFFL` queda bien incluso pasado el 2038, cuando el int de Java ya se
        // vería negativo.
        modificado = attrs.mTime.toLong() and 0xFFFFFFFFL,
        esCarpeta = attrs.isDir,
    )

    /** Descarga [ruta] completa, cortando (con [ErrorDeConsola.MuyGrande]) si se pasa de [limite] según lo que de verdad va llegando, no según lo que haya dicho `stat`. */
    private fun descargarBytes(ruta: String, limite: Long): ByteArray =
        sftp.get(ruta).use { entrada ->
            val salida = ByteArrayOutputStream()
            val trozo = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val leidos = entrada.read(trozo)
                if (leidos < 0) break
                total += leidos
                if (total > limite) throw ErrorDeConsola.MuyGrande(ruta, total)
                salida.write(trozo, 0, leidos)
            }
            salida.toByteArray()
        }

    /**
     * Si un intento anterior murió a medias, [anterior] todavía existe. Dos casos, y solo uno se
     * puede resolver solo:
     * - [ruta] **no** está: el reemplazo nunca llegó a pasar (murió subiendo, verificando el
     *   temporal, o justo después de apartar el original pero antes de poner el temporal en su
     *   lugar). Se restaura sola: [anterior] vuelve a ser [ruta].
     * - [ruta] **sí** está: acá NO se puede saber desde aquí si el reemplazo ya había terminado
     *   bien (y [anterior] es basura por borrar) o si murió más adelante, en la verificación final
     *   de [escribirSeguro], justo en el caso donde su propio deshacer tampoco pudo completarse —
     *   ese es exactamente el escenario que deja dicho en su mensaje de [ErrorDeConsola.VerificacionFallida]:
     *   "el original está a salvo en [anterior]" mientras [ruta] quedó con contenido que nunca
     *   verificó. Si aquí se asumiera "ya terminó bien" y se borrara [anterior] a ciegas, un intento
     *   nuevo borraría la única copia buena y dejaría la que no sirve. Por eso nunca se borra: se
     *   aparta con un nombre único (que no pisa nada, `anterior-<nanoTime>`) y se sigue — la
     *   escritura de ahora deja [ruta] en un estado nuevo y verificado de todas formas, y lo que se
     *   apartó queda ahí para revisarlo luego a mano (por ejemplo con [listar]).
     *
     * Un [temporal] suelto de un intento que nunca llegó a los `rename` se borra: todavía no
     * reemplaza nada, no hay nada que perder.
     */
    private fun repararIntentoPrevio(ruta: String, anterior: String, temporal: String) {
        if (attrsOCrudo(anterior) != null) {
            if (attrsOCrudo(ruta) == null) {
                runCatching { sftp.rename(anterior, ruta) }
            } else {
                val apartado = "$anterior-${System.nanoTime()}"
                if (attrsOCrudo(apartado) == null) runCatching { sftp.rename(anterior, apartado) }
            }
        }
        eliminarSiExiste(temporal)
    }

    private fun subirYVerificar(temporal: String, bytes: ByteArray) {
        try {
            sftp.put(ByteArrayInputStream(bytes), temporal, ChannelSftp.OVERWRITE)
        } catch (e: Exception) {
            eliminarSiExiste(temporal)
            throw traducir(e, temporal)
        }
        try {
            verificarContenido(temporal, bytes)
        } catch (e: ErrorDeConsola.VerificacionFallida) {
            eliminarSiExiste(temporal)
            throw e
        }
    }

    private fun verificarContenido(ruta: String, esperado: ByteArray) {
        val real = try {
            descargarBytes(ruta, TamanoMaximoDeVerificacion)
        } catch (e: Exception) {
            throw ErrorDeConsola.VerificacionFallida(ruta, "no se pudo releer $ruta para verificar: ${e.message}")
        }
        if (!real.contentEquals(esperado)) {
            throw ErrorDeConsola.VerificacionFallida(
                ruta,
                "lo escrito en $ruta no coincide: ${real.size} de ${esperado.size} bytes esperados",
            )
        }
    }

    private fun eliminarSiExiste(ruta: String) {
        if (attrsOCrudo(ruta) != null) runCatching { sftp.rm(ruta) }
    }

    /**
     * `sync -f <ruta>` (sincroniza solo el sistema de archivos de [ruta], no el equipo entero) y,
     * si eso falla (toolchain sin `-f`, lo que sea), un `sync` normal como respaldo. No es fatal
     * si los dos fallan: el archivo ya quedó escrito y verificado byte por byte en
     * [escribirSeguro]; esto solo adelanta cuándo toca la tarjeta de verdad. El contrato no trae
     * un logger inyectado para dejar constancia del fallo, así que se documenta aquí en vez de
     * registrarlo en algún lado.
     */
    private fun forzarSync(ruta: String) {
        runCatching {
            ejecutarComando(sesionSsh, "sync -f ${comillaSimpleSh(ruta)} 2>/dev/null || sync", TiempoDeComandoMs)
        }
    }

    private fun homeDelUsuario(): String =
        ejecutarComando(sesionSsh, "echo \$HOME", TiempoDeComandoMs).salida.trim().ifBlank { "/home/${sesionSsh.userName}" }

    private fun leerAjustesSiExiste(ruta: String, home: String): AjustesRetroArch? {
        if (attrsOCrudo(ruta) == null) return null
        val contenido = try {
            descargarBytes(ruta, TamanoMaximoDeVerificacion).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw traducir(e, ruta)
        }
        val ajustes = ajustesDesdeMapa(ruta, parsearRetroArchCfg(contenido))
        return ajustes.copy(
            carpetaDePartidas = expandirHome(ajustes.carpetaDePartidas, home),
            carpetaDeEstados = expandirHome(ajustes.carpetaDeEstados, home),
        )
    }

    private fun traducir(e: Exception, ruta: String): ErrorDeConsola = when {
        e is ErrorDeConsola -> e
        e is SftpException && e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE -> ErrorDeConsola.NoExiste(ruta)
        else -> ErrorDeConsola.SinConexion(e.message ?: "fallo de red con la consola ($ruta)", e)
    }

    companion object {
        private const val ExtensionTemporal = ".pr36c-tmp"
        private const val ExtensionAnterior = ".pr36c-prev"
        private const val TiempoDeComandoMs = 8_000
        private const val TamanoMaximoDeVerificacion = 64L * 1024 * 1024
    }
}

/** `carpeta/nombre`, sin duplicar la diagonal si [carpeta] ya termina en una. */
internal fun unirRutaRemota(carpeta: String, nombre: String): String =
    if (carpeta.endsWith("/")) "$carpeta$nombre" else "$carpeta/$nombre"

/**
 * Las rutas que hay que ir creando para que exista [carpeta], de la más corta a la más larga
 * (`mkdir -p` por SFTP, que no lo trae): `/roms/gba/respaldos` da `[/roms, /roms/gba,
 * /roms/gba/respaldos]`.
 */
internal fun rutasIntermedias(carpeta: String): List<String> {
    val prefijo = if (carpeta.startsWith("/")) "/" else ""
    val segmentos = carpeta.split("/").filter { it.isNotEmpty() }
    var acumulado = prefijo
    return segmentos.map { segmento ->
        acumulado = if (acumulado.isEmpty() || acumulado == "/") "$acumulado$segmento" else "$acumulado/$segmento"
        acumulado
    }
}
