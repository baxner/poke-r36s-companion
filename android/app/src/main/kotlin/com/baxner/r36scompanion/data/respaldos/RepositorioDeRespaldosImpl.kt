package com.baxner.r36scompanion.data.respaldos

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación real de [RepositorioDeRespaldos] (ver Respaldos.kt y docs/plan.md, fase 2).
 *
 * [db], [almacen], [lector] y [reloj] también los usa [EscritorDePartidasImpl]: en la app son las
 * mismas instancias (un solo `respaldos.db` y un solo almacén de blobs); en las pruebas, cada una
 * apunta a su propia base en memoria y carpeta temporal.
 */
class RepositorioDeRespaldosImpl(
    private val db: RespaldosDatabase,
    private val almacen: AlmacenDeBlobs,
    private val lector: LectorDeResumen,
    private val reloj: Reloj,
) : RepositorioDeRespaldos {
    private val dao get() = db.respaldosDao()
    private val nucleo = NucleoDeRespaldos(db, almacen, lector, reloj)

    override fun juegos(): Flow<List<JuegoRespaldado>> =
        dao.observarJuegos().map { fila -> fila.map { it.aDominio() } }

    override fun versiones(juegoId: Long): Flow<List<VersionRespaldada>> =
        dao.observarVersiones(juegoId).map { fila -> fila.map { it.aDominio() } }

    override suspend fun bytesDe(versionId: Long): ByteArray {
        val version = dao.versionPorId(versionId) ?: throw NoSuchElementException("no existe la versión $versionId")
        return almacen.leer(version.sha256)
    }

    override suspend fun respaldarTodo(sesion: SesionDeConsola, progreso: (ProgresoDeRespaldo) -> Unit): ResultadoDeRespaldo {
        val carpeta = carpetaDelSistema(SistemaGba)
        val archivos = sesion.listar(carpeta)
            .filter { !it.esCarpeta } // ignora subcarpetas, incluida la oculta ".pr36c/" de la propia app
            .mapNotNull { archivo -> tipoYNombreDeArchivo(archivo.nombre)?.let { (tipo, nombre) -> Triple(archivo, tipo, nombre) } }
        // Lo que no es ni ".srm" ni ".state..." (las ROMs, cualquier otro archivo suelto) no
        // calza con ningún patrón de tipoYNombreDeArchivo y queda fuera desde el mapNotNull.

        val nuevas = mutableListOf<VersionRespaldada>()
        var sinCambios = 0
        val fallidos = mutableListOf<Pair<String, String>>()

        archivos.forEachIndexed { indice, (archivo, tipo, nombre) ->
            progreso(ProgresoDeRespaldo(indice, archivos.size, archivo.nombre))
            try {
                val conocida = dao.ultimaVersionDeRuta(archivo.ruta)
                val sinCambioPorFecha = conocida != null && conocida.tamano == archivo.tamano && conocida.modificadoEnConsola == archivo.modificado
                if (sinCambioPorFecha) {
                    sinCambios++
                    return@forEachIndexed
                }

                val bytes = sesion.leer(archivo.ruta)
                val hash = AlmacenDeBlobs.sha256Hex(bytes)
                if (conocida != null && conocida.sha256 == hash) {
                    // El tamaño o la fecha cambiaron (se volvió a guardar desde el juego, un
                    // `touch`...) pero el contenido es el mismo: tampoco hace falta otra versión.
                    sinCambios++
                    return@forEachIndexed
                }

                val juego = nucleo.obtenerOCrearJuego(SistemaGba, nombre)
                nuevas += nucleo.registrarVersion(juego, tipo, archivo.ruta, bytes, archivo.modificado, OrigenDeVersion.CONSOLA)
            } catch (e: Exception) {
                fallidos += archivo.ruta to (e.message ?: e.toString())
            }
        }
        progreso(ProgresoDeRespaldo(archivos.size, archivos.size, null))
        return ResultadoDeRespaldo(nuevas, sinCambios, fallidos)
    }

    override suspend fun exportarZip(destino: OutputStream, juegos: List<Long>?) {
        val idsDeJuegos = juegos ?: dao.idsDeJuegos()
        ZipOutputStream(destino).use { zip ->
            for (juegoId in idsDeJuegos) {
                val ultimaPorRuta = dao.versionesDeJuego(juegoId)
                    .groupBy { it.rutaEnConsola }
                    .mapValues { (_, versiones) -> versiones.maxBy { it.respaldadaEn } }
                for ((rutaEnConsola, version) in ultimaPorRuta) {
                    zip.putNextEntry(ZipEntry(rutaEnConsola.removePrefix("/")))
                    zip.write(almacen.leer(version.sha256))
                    zip.closeEntry()
                }
            }
        }
    }
}
