package com.baxner.r36scompanion.data.consola

import java.security.MessageDigest
import java.util.Base64

/*
 * Huella de una llave de servidor SSH, puro (sin JSch): a [ConectorDeConsolaJsch] le basta con
 * los bytes crudos de la llave que ya le da `HostKeyRepository.check(host, key)`, así que no hace
 * falta pasar por `HostKey.getFingerPrint(JSch)` (que además, por reflexión, elige la clase de hash
 * según la config `FingerprintHash`). Aquí se calcula directo y se puede probar sin levantar nada
 * de red: solo bytes de entrada, string de salida.
 */

/**
 * Huella en el formato de OpenSSH: `SHA256:<base64 sin relleno>` del SHA-256 de [blobDeLlave] (el
 * blob tal cual viaja por el cable: `string algoritmo || campos...`, el mismo que usa
 * `ssh-keygen -l -E sha256`). Es la que se fija en [Consola.huella].
 */
internal fun huellaSha256DeLlave(blobDeLlave: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(blobDeLlave)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}

/**
 * El nombre del algoritmo (`ssh-ed25519`, `ssh-rsa`, `ecdsa-sha2-nistp256`…) de [blobDeLlave], leído
 * de su propio encabezado: una llave de servidor SSH siempre empieza con `uint32 longitud` seguido
 * de esos bytes en ASCII (RFC 4251 §5, el mismo formato que [huellaSha256DeLlave] hashea completo).
 * Es [Consola.algoritmoDeLlave].
 */
internal fun algoritmoDeLlaveSsh(blobDeLlave: ByteArray): String {
    require(blobDeLlave.size >= 4) { "llave demasiado corta para traer un encabezado: ${blobDeLlave.size} bytes" }
    val longitud = ((blobDeLlave[0].toInt() and 0xFF) shl 24) or
        ((blobDeLlave[1].toInt() and 0xFF) shl 16) or
        ((blobDeLlave[2].toInt() and 0xFF) shl 8) or
        (blobDeLlave[3].toInt() and 0xFF)
    require(longitud in 0..(blobDeLlave.size - 4)) { "encabezado de llave inválido: dice $longitud bytes y solo hay ${blobDeLlave.size - 4}" }
    return String(blobDeLlave, 4, longitud, Charsets.US_ASCII)
}
