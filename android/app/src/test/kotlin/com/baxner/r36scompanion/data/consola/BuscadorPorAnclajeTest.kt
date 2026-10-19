package com.baxner.r36scompanion.data.consola

import java.net.ServerSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `127.0.0.1` es la única dirección de loopback que este entorno deja usar para escuchar de
 * verdad (otras como `127.0.0.12` fallan con "Can't assign requested address": macOS no las trae
 * aliasadas por default). Por eso cada interfaz de prueba se arma con una /30 cuya única IP "de
 * host" quede justo en `127.0.0.1` (el teléfono se pone en `127.0.0.2` o `.3`, que nunca se tocan,
 * solo sirven para la aritmética de [direccionesParaBuscar]).
 */
class BuscadorPorAnclajeTest {

    @Test
    fun `interfacesDeAnclaje delega en el estado actual del anclaje`() {
        val interfaz = InterfazDeAnclaje("rndis0", "10.0.0.1", 24)
        val buscador = BuscadorPorAnclaje(FakeAnclaje(listOf(interfaz)))

        assertEquals(listOf(interfaz), buscador.interfacesDeAnclaje())
    }

    @Test
    fun `sin interfaces de anclaje no busca nada`() = runTest {
        val buscador = BuscadorPorAnclaje(FakeAnclaje(emptyList()))

        assertTrue(buscador.buscar().isEmpty())
    }

    @Test
    fun `encuentra la unica direccion de la subred que de verdad contesta el puerto`() = servidorEnLoopback { puerto ->
        runTest {
            val interfaz = InterfazDeAnclaje("rndis0", "127.0.0.2", 30) // candidata única: 127.0.0.1
            val buscador = BuscadorPorAnclaje(FakeAnclaje(listOf(interfaz)), puerto = puerto, tiempoLimitePorHostMs = 200)

            val encontradas = buscador.buscar()

            assertEquals(listOf(Candidata("127.0.0.1", puerto, "rndis0")), encontradas)
        }
    }

    @Test
    fun `junta candidatas de varias interfaces y descarta las que no contestan`() = servidorEnLoopback { puerto ->
        runTest {
            val conRespuesta = InterfazDeAnclaje("rndis-a", "127.0.0.2", 30) // candidata única: 127.0.0.1 (escuchando)
            val sinRespuesta = InterfazDeAnclaje("rndis-b", "127.0.0.6", 30) // candidata única: 127.0.0.5 (nadie)
            val buscador = BuscadorPorAnclaje(FakeAnclaje(listOf(conRespuesta, sinRespuesta)), puerto = puerto, tiempoLimitePorHostMs = 200)

            val encontradas = buscador.buscar()

            assertEquals(listOf(Candidata("127.0.0.1", puerto, "rndis-a")), encontradas)
        }
    }

    @Test
    fun `puertoAbierto es true cuando alguien escucha`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { servidor ->
            assertTrue(puertoAbierto("127.0.0.1", servidor.localPort, 500))
        }
    }

    @Test
    fun `puertoAbierto es false cuando el puerto esta cerrado (rechazo rapido)`() {
        val puertoLibre = ServerSocket(0).use { it.localPort }
        assertFalse(puertoAbierto("127.0.0.1", puertoLibre, 500))
    }

    @Test
    fun `puertoAbierto es false cuando nadie contesta nada (se acaba el tiempo)`() {
        // TEST-NET-1 (RFC 5737): no se puede enrutar de verdad, así que ni rechaza ni contesta.
        assertFalse(puertoAbierto("192.0.2.1", 22, 200))
    }

    /** Un servidor TCP real en 127.0.0.1, en un puerto libre, cerrado solo al final del bloque. */
    private fun servidorEnLoopback(bloque: (puerto: Int) -> Unit) {
        ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1")).use { servidor ->
            bloque(servidor.localPort)
        }
    }
}

private class FakeAnclaje(private val interfaces: List<InterfazDeAnclaje>) : Anclaje {
    override fun observar(): Flow<EstadoDelAnclaje> = emptyFlow()
    override fun estadoActual(): EstadoDelAnclaje = estadoDelAnclaje(cableConectado = interfaces.isNotEmpty(), interfaces = interfaces)
    override fun abrirAjustesDeAnclaje(): Boolean = false
}
