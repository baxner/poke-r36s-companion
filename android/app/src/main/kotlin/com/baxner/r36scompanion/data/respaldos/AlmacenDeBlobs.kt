package com.baxner.r36scompanion.data.respaldos

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Almacén de blobs direccionado por contenido (ver docs/plan.md, "Respaldos"): cada versión
 * respaldada se guarda una sola vez, con el nombre de su propio SHA-256, así que dos archivos
 * idénticos (dos juegos con la misma partida en blanco, o la misma versión vista dos veces) no
 * duplican espacio.
 *
 * Diseño en dos niveles (`<2 primeros hex>/<hash completo>`), como `.git/objects`: con miles de
 * blobs, una sola carpeta con todos los archivos se vuelve lenta de listar en algunos sistemas
 * de archivos.
 *
 * [base] es inyectable para que las pruebas usen una carpeta temporal y no toquen `filesDir` de
 * verdad; en la app vive en `File(context.filesDir, "respaldos/blobs")`.
 */
class AlmacenDeBlobs(private val base: File) {

    /** Guarda [bytes] si no estaba ya (escritura atómica: temporal + rename) y devuelve su SHA-256. */
    fun guardar(bytes: ByteArray): String {
        val hash = sha256Hex(bytes)
        val destino = rutaDe(hash)
        if (destino.exists()) return hash

        val carpeta = destino.parentFile ?: throw IOException("ruta de blob sin carpeta: $destino")
        carpeta.mkdirs()
        val temporal = File.createTempFile("blob-", ".tmp", carpeta)
        try {
            temporal.writeBytes(bytes)
            if (destino.exists()) {
                // Alguien más (otra llamada, otro proceso) ya lo guardó mientras escribíamos: el
                // contenido es el mismo porque el nombre es su propio hash, así que no hay nada
                // que resolver; solo se descarta el temporal.
                temporal.delete()
            } else if (!temporal.renameTo(destino)) {
                throw IOException("no se pudo mover el blob $hash a $destino")
            }
        } finally {
            temporal.delete() // no-op si el rename ya lo movió
        }
        return hash
    }

    /** Lee el blob de [hash] y verifica que su contenido siga correspondiendo a ese hash. */
    fun leer(hash: String): ByteArray {
        val archivo = rutaDe(hash)
        if (!archivo.exists()) throw IOException("no existe el blob $hash")
        val bytes = archivo.readBytes()
        val real = sha256Hex(bytes)
        if (real != hash) throw IOException("el blob $hash no coincide con su contenido (quedó con hash $real)")
        return bytes
    }

    private fun rutaDe(hash: String): File = File(base, "${hash.take(2)}/$hash")

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
