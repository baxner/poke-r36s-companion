package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.jugar.ErrorDeRom
import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import com.baxner.r36scompanion.data.jugar.RomsDelTelefono
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [RomsDelTelefono] en memoria para las pruebas de la fase 5 (ver AGENTS.md). */
class RomsFalsas : RomsDelTelefono {
    private val romsPorJuego = mutableMapOf<Long, RomEnElTelefono>()
    private val _guardadas = MutableStateFlow<List<RomEnElTelefono>>(emptyList())

    /** Si [traer] debe fallar la próxima vez (p. ej. [ErrorDeRom.FormatoNoSoportado]); se consume una sola vez. */
    var errorAlTraer: ErrorDeRom? = null

    /** Lanzado por [rom]/[traer] cuando no hay sesión y tampoco ROM: por defecto no hay ninguna (null). */
    var vecesLlamadoTraer = 0

    fun ponerRom(rom: RomEnElTelefono) {
        romsPorJuego[rom.juegoId] = rom
        actualizar()
    }

    override fun guardadas(): StateFlow<List<RomEnElTelefono>> = _guardadas

    override suspend fun rom(juegoId: Long): RomEnElTelefono? = romsPorJuego[juegoId]

    override suspend fun traer(sesion: SesionDeConsola, juego: JuegoRespaldado): RomEnElTelefono {
        vecesLlamadoTraer++
        errorAlTraer?.let {
            errorAlTraer = null
            throw it
        }
        romsPorJuego[juego.id]?.let { return it }
        val nueva = RomEnElTelefono(
            juegoId = juego.id,
            archivo = File("roms-de-prueba/${juego.nombre}.gba"),
            rutaEnConsola = "${juego.rutaDePartida.substringBeforeLast('/')}/${juego.nombre}.gba",
            tamanoEnConsola = 1_000_000L,
            modificadoEnConsola = 1_000L,
            codigoDeJuego = "BPRE",
            tituloInterno = juego.nombre.uppercase(),
            tamano = 1_000_000L,
        )
        romsPorJuego[juego.id] = nueva
        actualizar()
        return nueva
    }

    override suspend fun borrar(juegoId: Long) {
        romsPorJuego.remove(juegoId)
        actualizar()
    }

    private fun actualizar() {
        _guardadas.value = romsPorJuego.values.toList()
    }
}
