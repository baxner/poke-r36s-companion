package com.baxner.r36scompanion.data.jugar

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Escribe [bytes] en [destino] sin dejarlo nunca a medias (lo usan [RomsDelTelefonoEnArchivos] y
 * [JugarEnElTelefono], ver el encargo de la fase 5, punto 1 y 2): a un temporal en la misma
 * carpeta y renombra encima (`ATOMIC_MOVE`, `REPLACE_EXISTING`). Si algo falla, borra el temporal
 * y [destino] queda exactamente como estaba.
 */
internal fun escribirArchivoAtomico(destino: File, bytes: ByteArray) {
    val carpeta = destino.parentFile ?: error("ruta de escritura atómica sin carpeta: $destino")
    carpeta.mkdirs()
    val temporal = File(carpeta, "${destino.name}.tmp-${UUID.randomUUID()}")
    try {
        temporal.writeBytes(bytes)
        Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        temporal.delete()
        throw e
    }
}
