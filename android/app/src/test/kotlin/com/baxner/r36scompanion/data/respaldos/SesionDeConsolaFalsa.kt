package com.baxner.r36scompanion.data.respaldos

import com.baxner.r36scompanion.data.consola.AjustesRetroArch
import com.baxner.r36scompanion.data.consola.ArchivoRemoto
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.consola.ErrorDeConsola
import com.baxner.r36scompanion.data.consola.JuegoAbierto
import com.baxner.r36scompanion.data.consola.SesionDeConsola

/**
 * [SesionDeConsola] en memoria para las pruebas de `data/respaldos/` (ver AGENTS.md: nunca contra
 * la consola ni el emulador de verdad). Cada archivo es sus bytes más su fecha de modificación
 * (segundos UTC, como SFTP); las carpetas se registran aparte para que `listar` también pueda
 * devolver subcarpetas (p. ej. una `.pr36c/` oculta, para probar que se ignora).
 *
 * [juegosAbiertos] y los fallos de [escribirSeguro]/[mover] son inyectables: los casos de
 * seguridad del escritor (juego abierto, falla a medio camino) se simulan poniéndolos antes de
 * llamar al método bajo prueba.
 */
class SesionDeConsolaFalsa(
    override val consola: Consola = Consola(
        host = "consola-falsa",
        puerto = 22,
        huella = "SHA256:falsa",
        algoritmoDeLlave = "ssh-ed25519",
        nombreDeEquipo = "falsa",
    ),
) : SesionDeConsola {
    private data class ArchivoFalso(var bytes: ByteArray, var modificado: Long)

    private val archivos = linkedMapOf<String, ArchivoFalso>()
    private val carpetas = sortedSetOf("/")

    var juegosAbiertosSimulados: List<JuegoAbierto> = emptyList()
    var falloEnEscribirSeguro: (() -> Throwable)? = null
    var falloEnMover: (() -> Throwable)? = null
    private val fallosAlLeer = mutableMapOf<String, () -> Throwable>()

    /** Pone o reemplaza un archivo, como si ya estuviera en la consola antes de la prueba. */
    fun ponerArchivo(ruta: String, bytes: ByteArray, modificado: Long) {
        archivos[ruta] = ArchivoFalso(bytes, modificado)
        registrarAncestros(ruta)
    }

    /** Registra una carpeta vacía (o con contenido que no importa) para que `listar` la devuelva. */
    fun ponerCarpeta(ruta: String) {
        carpetas += ruta
        registrarAncestros(ruta)
    }

    fun existeArchivo(ruta: String): Boolean = ruta in archivos

    fun bytesDe(ruta: String): ByteArray? = archivos[ruta]?.bytes?.copyOf()

    /** La próxima vez que se intente leer [ruta], lanza lo que devuelva [fallo]. */
    fun fallarAlLeer(ruta: String, fallo: () -> Throwable) {
        fallosAlLeer[ruta] = fallo
    }

    /** Registra todas las carpetas ancestras de [ruta] (un archivo o una carpeta), sin incluirla a ella misma. */
    private fun registrarAncestros(ruta: String) {
        var carpeta = ruta.substringBeforeLast('/', "")
        while (carpeta.isNotEmpty() && carpetas.add(carpeta)) {
            carpeta = carpeta.substringBeforeLast('/', "")
        }
        carpetas += "/"
    }

    override suspend fun listar(carpeta: String): List<ArchivoRemoto> {
        val normalizada = if (carpeta.length > 1) carpeta.trimEnd('/') else carpeta
        val resultado = mutableListOf<ArchivoRemoto>()
        for ((ruta, archivo) in archivos) {
            if (ruta.substringBeforeLast('/', "") == normalizada) {
                resultado += ArchivoRemoto(ruta, ruta.substringAfterLast('/'), archivo.bytes.size.toLong(), archivo.modificado, esCarpeta = false)
            }
        }
        for (ruta in carpetas) {
            if (ruta != normalizada && ruta != "/" && ruta.substringBeforeLast('/', "") == normalizada) {
                resultado += ArchivoRemoto(ruta, ruta.substringAfterLast('/'), 0, 0, esCarpeta = true)
            }
        }
        return resultado
    }

    override suspend fun info(ruta: String): ArchivoRemoto? = archivos[ruta]?.let {
        ArchivoRemoto(ruta, ruta.substringAfterLast('/'), it.bytes.size.toLong(), it.modificado, esCarpeta = false)
    }

    /** Cuántas veces se llamó [leer] de verdad (para comprobar que "sin cambios" no baja nada). */
    var lecturas: Int = 0
        private set

    override suspend fun leer(ruta: String, maximo: Long): ByteArray {
        fallosAlLeer.remove(ruta)?.let { throw it() }
        lecturas++
        return archivos[ruta]?.bytes?.copyOf() ?: throw ErrorDeConsola.NoExiste(ruta)
    }

    override suspend fun escribirSeguro(ruta: String, bytes: ByteArray) {
        falloEnEscribirSeguro?.let { throw it() }
        val anterior = archivos[ruta]
        archivos[ruta] = ArchivoFalso(bytes.copyOf(), anterior?.modificado ?: 0L)
        registrarAncestros(ruta)
    }

    override suspend fun mover(desde: String, hacia: String) {
        falloEnMover?.let { throw it() }
        if (hacia in archivos) throw ErrorDeConsola.YaExiste(hacia)
        val archivo = archivos.remove(desde) ?: throw ErrorDeConsola.NoExiste(desde)
        archivos[hacia] = archivo
        registrarAncestros(hacia)
    }

    override suspend fun crearCarpetas(carpeta: String) {
        carpetas += carpeta
        registrarAncestros(carpeta)
    }

    override suspend fun juegosAbiertos(): List<JuegoAbierto> = juegosAbiertosSimulados

    override suspend fun ajustesRetroArch(): List<AjustesRetroArch> = emptyList()

    override fun close() {}
}
