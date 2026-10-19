package com.baxner.r36scompanion.data.consola

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Encuentra la consola probando el puerto SSH en la subred de cada interfaz de anclaje activa
 * ([Anclaje.estadoActual]): la aritmética de qué direcciones probar es pura (ver
 * [direccionesParaBuscar]); lo único impuro aquí es el `connect` a cada una, en paralelo.
 *
 * [puerto] y [tiempoLimitePorHostMs] solo cambian en las pruebas (en la app siempre son [PuertoSsh]
 * y ~400 ms): así se puede probar [buscar] de punta a punta contra sockets de verdad en localhost,
 * sin pelearse con que el 22 es un puerto privilegiado.
 */
class BuscadorPorAnclaje(
    private val anclaje: Anclaje,
    private val puerto: Int = PuertoSsh,
    private val tiempoLimitePorHostMs: Int = 400,
) : BuscadorDeConsola {

    override fun interfacesDeAnclaje(): List<InterfazDeAnclaje> = anclaje.estadoActual().interfaces

    override suspend fun buscar(): List<Candidata> = coroutineScope {
        interfacesDeAnclaje()
            .flatMap { interfaz -> direccionesParaBuscar(interfaz).map { host -> interfaz to host } }
            .map { (interfaz, host) ->
                async(Dispatchers.IO) {
                    if (puertoAbierto(host, puerto, tiempoLimitePorHostMs)) Candidata(host, puerto, interfaz.nombre) else null
                }
            }
            .awaitAll()
            .filterNotNull()
    }
}

/** `true` si algo contesta el puerto [puerto] de [host] dentro de [tiempoLimiteMs]. No autentica nada, solo toca. */
internal fun puertoAbierto(host: String, puerto: Int, tiempoLimiteMs: Int): Boolean =
    try {
        Socket().use { it.connect(InetSocketAddress(host, puerto), tiempoLimiteMs); true }
    } catch (_: IOException) {
        false
    }
