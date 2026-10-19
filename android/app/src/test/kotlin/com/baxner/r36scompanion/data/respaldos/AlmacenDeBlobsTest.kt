package com.baxner.r36scompanion.data.respaldos

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlmacenDeBlobsTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var almacen: AlmacenDeBlobs

    @Before
    fun crearAlmacen() {
        almacen = AlmacenDeBlobs(carpetaTemporal.newFolder("blobs"))
    }

    @Test
    fun `guardar devuelve el sha256 del contenido y leer lo recupera igual`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        val hash = almacen.guardar(bytes)

        assertEquals(AlmacenDeBlobs.sha256Hex(bytes), hash)
        assertArrayEquals(bytes, almacen.leer(hash))
    }

    @Test
    fun `guardar en dos carpetas de dos primeros hex`() {
        val hashA = almacen.guardar(byteArrayOf(1, 2, 3))
        val hashB = almacen.guardar(byteArrayOf(4, 5, 6))

        assertTrue(File(carpetaTemporal.root, "blobs/${hashA.take(2)}/$hashA").exists())
        assertTrue(File(carpetaTemporal.root, "blobs/${hashB.take(2)}/$hashB").exists())
    }

    @Test
    fun `guardar el mismo contenido dos veces no duplica el blob`() {
        val bytes = byteArrayOf(9, 9, 9)

        val hash1 = almacen.guardar(bytes)
        val archivo = File(carpetaTemporal.root, "blobs/${hash1.take(2)}/$hash1")
        val modificadoLaPrimeraVez = archivo.lastModified()
        val hash2 = almacen.guardar(bytes)

        assertEquals(hash1, hash2)
        // Sigue siendo el mismo archivo (no se reescribió): un solo blob para el contenido.
        assertEquals(modificadoLaPrimeraVez, archivo.lastModified())
        val carpetaDeHex = File(carpetaTemporal.root, "blobs/${hash1.take(2)}")
        assertEquals(1, carpetaDeHex.listFiles()?.size)
    }

    @Test
    fun `contenidos distintos dan hashes distintos`() {
        val hash1 = almacen.guardar(byteArrayOf(1))
        val hash2 = almacen.guardar(byteArrayOf(2))

        assertTrue(hash1 != hash2)
    }

    @Test
    fun `leer un blob corrompido revienta por la verificacion`() {
        val hash = almacen.guardar(byteArrayOf(1, 2, 3))
        val archivo = File(carpetaTemporal.root, "blobs/${hash.take(2)}/$hash")
        archivo.writeBytes(byteArrayOf(9, 9, 9))

        assertThrows(Exception::class.java) { almacen.leer(hash) }
    }

    @Test
    fun `leer un blob que no existe revienta`() {
        assertThrows(Exception::class.java) { almacen.leer("0".repeat(64)) }
    }
}
