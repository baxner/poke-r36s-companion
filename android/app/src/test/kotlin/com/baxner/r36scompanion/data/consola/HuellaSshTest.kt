package com.baxner.r36scompanion.data.consola

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Vectores de verdad: dos llaves ED25519 generadas con `ssh-keygen -t ed25519`, con su huella
 * comprobada contra `ssh-keygen -lf <pub> -E sha256` (ver el reporte del encargo). Si algo de
 * [huellaSha256DeLlave] se descompone (el formato del base64, el relleno, el prefijo), esto truena
 * contra el mismo cálculo que hace OpenSSH, no solo contra sí mismo.
 */
class HuellaSshTest {
    private val blobLlaveUno = Base64.getDecoder().decode("AAAAC3NzaC1lZDI1NTE5AAAAIH9RKf479zPUqIHKb1LmSZ136dVIWIcUCo7BdzniekdH")
    private val huellaEsperadaUno = "SHA256:BZq9IF3kZVQzgMRVYBnrEdpJ9SCG037y7TryTvoPHWA"

    private val blobLlaveDos = Base64.getDecoder().decode("AAAAC3NzaC1lZDI1NTE5AAAAIO79P/u8FQxqL0NzYl1CipBaITvmUXGLHRFWC2329EKe")
    private val huellaEsperadaDos = "SHA256:GK1maBTFabyY5ugOmoullazuKwOVD5yXrITzKJ53iDk"

    @Test
    fun `huella coincide con la de ssh-keygen para una llave ED25519 real`() {
        assertEquals(huellaEsperadaUno, huellaSha256DeLlave(blobLlaveUno))
    }

    @Test
    fun `huella de otra llave da otro valor, también igual al de ssh-keygen`() {
        assertEquals(huellaEsperadaDos, huellaSha256DeLlave(blobLlaveDos))
    }

    @Test
    fun `la huella nunca lleva relleno de base64`() {
        assertEquals(false, huellaSha256DeLlave(blobLlaveUno).contains("="))
    }

    @Test
    fun `algoritmo de llave se lee del encabezado del blob`() {
        assertEquals("ssh-ed25519", algoritmoDeLlaveSsh(blobLlaveUno))
        assertEquals("ssh-ed25519", algoritmoDeLlaveSsh(blobLlaveDos))
    }

    @Test
    fun `algoritmo con un nombre distinto tambien se lee bien`() {
        // uint32(7) + "ssh-rsa" + relleno cualquiera, para probar que no asume siempre 11 bytes.
        val blob = byteArrayOf(0, 0, 0, 7) + "ssh-rsa".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3)
        assertEquals("ssh-rsa", algoritmoDeLlaveSsh(blob))
    }

    @Test
    fun `blob demasiado corto para un encabezado truena claro`() {
        assertThrows(IllegalArgumentException::class.java) { algoritmoDeLlaveSsh(byteArrayOf(0, 0, 1)) }
    }

    @Test
    fun `encabezado que dice mas bytes de los que hay truena claro`() {
        val blob = byteArrayOf(0, 0, 0, 99) + "ssh-ed25519".toByteArray(Charsets.US_ASCII)
        assertThrows(IllegalArgumentException::class.java) { algoritmoDeLlaveSsh(blob) }
    }
}
