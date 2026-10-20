package com.baxner.r36scompanion.data.jugar

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Escribe [bytes] en [destino] sin dejarlo nunca a medias (lo usan [RomsDelTelefonoEnArchivos] y
 * [JugarEnElTelefono], ver el encargo de la fase 5, punto 1 y 2): a un temporal en la misma
 * carpeta y renombra encima (`ATOMIC_MOVE`, `REPLACE_EXISTING`). Si algo falla, borra el temporal
 * y [destino] queda exactamente como estaba. Corre en [Dispatchers.IO]: lo llaman ViewModels
 * desde el hilo principal y el `fsync` (o una ROM de 32 MB) trabaría la pantalla.
 */
internal suspend fun escribirArchivoAtomico(destino: File, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
    val carpeta = destino.parentFile ?: error("ruta de escritura atómica sin carpeta: $destino")
    carpeta.mkdirs()
    val temporal = File(carpeta, "${destino.name}.tmp-${UUID.randomUUID()}")
    try {
        // Al disco antes de renombrar: si el teléfono se apaga de golpe, queda lo viejo o lo nuevo
        // completo, nunca un archivo vacío con el nombre bueno.
        FileOutputStream(temporal).use { salida ->
            salida.write(bytes)
            salida.fd.sync()
        }
        Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        temporal.delete()
        throw e
    }
}
