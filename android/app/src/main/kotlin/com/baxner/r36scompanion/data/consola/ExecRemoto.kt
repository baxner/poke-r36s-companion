package com.baxner.r36scompanion.data.consola

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream

/** Lo que devuelve [ejecutarComando]: el estado de salida y lo que escribió en stdout/stderr. */
internal data class ResultadoExec(val estado: Int, val salida: String, val error: String)

/**
 * Corre [comando] en la consola por un canal `exec` y junta toda su salida (bloquea el hilo que lo
 * llama: siempre desde `Dispatchers.IO`). El patrón es el de siempre con JSch: leer el stream hasta
 * el EOF y solo entonces preguntar el estado de salida, porque a veces le gana la carrera al propio
 * EOF del stream por uno o dos mensajes del protocolo (el "exit-status" puede llegar justo después);
 * se espera a que el canal cierre de verdad, acotado a [tiempoLimiteMs] extra, antes de leerlo.
 */
internal fun ejecutarComando(sesionSsh: Session, comando: String, tiempoLimiteMs: Int = 8_000): ResultadoExec {
    val canal = sesionSsh.openChannel("exec") as ChannelExec
    canal.setCommand(comando)
    canal.setInputStream(null)
    val bufferDeError = ByteArrayOutputStream()
    canal.setErrStream(bufferDeError, false)
    val entrada = canal.inputStream
    canal.connect(tiempoLimiteMs)
    val bufferDeSalida = ByteArrayOutputStream()
    val trozo = ByteArray(8192)
    while (true) {
        val leidos = entrada.read(trozo)
        if (leidos < 0) break
        bufferDeSalida.write(trozo, 0, leidos)
    }
    val limite = System.currentTimeMillis() + tiempoLimiteMs
    while (!canal.isClosed && System.currentTimeMillis() < limite) {
        Thread.sleep(10)
    }
    val estado = canal.exitStatus
    canal.disconnect()
    return ResultadoExec(
        estado = estado,
        salida = bufferDeSalida.toString(Charsets.UTF_8.name()),
        error = bufferDeError.toString(Charsets.UTF_8.name()),
    )
}
