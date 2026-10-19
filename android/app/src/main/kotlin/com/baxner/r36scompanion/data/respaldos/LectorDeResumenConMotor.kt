package com.baxner.r36scompanion.data.respaldos

import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.ResumenDePartida

/**
 * [LectorDeResumen] real, con el motor PKHeX dentro de Android (ver data/motor/MotorPartidas.kt).
 * Esta clase no se puede probar en la JVM (necesita el WebView real: ver
 * `MotorWebViewTest`, en `androidTest`); su única lógica que no es "llamarle al motor" es
 * [traducirResumen], separada para poder probarla aquí sin el motor.
 *
 * Cualquier [MotorException] (`invalid-save`, un hack que pide elegir formato…) se toma como
 * "el motor no la reconoce": null. El archivo igual se respalda (ver
 * [RepositorioDeRespaldosImpl.respaldarTodo]); solo se queda sin resumen y sin ser editable.
 */
class LectorDeResumenConMotor(private val motorPartidas: MotorPartidas) : LectorDeResumen {
    override suspend fun resumir(bytes: ByteArray, nombreDeArchivo: String): ResumenGuardado? =
        try {
            motorPartidas.conPartida(bytes, nombreDeArchivo) { traducirResumen(resumen()) }
        } catch (_: MotorException) {
            null
        }
}

/** Traduce lo que entiende el motor ([ResumenDePartida]) al resumen que guardan los respaldos ([ResumenGuardado]). Pura. */
fun traducirResumen(resumen: ResumenDePartida): ResumenGuardado = ResumenGuardado(
    version = resumen.partida.version,
    entrenador = resumen.entrenador.name,
    idDeEntrenador = resumen.entrenador.id,
    dinero = resumen.entrenador.money,
    horas = resumen.progreso.playTime?.hours,
    minutos = resumen.progreso.playTime?.minutes,
    segundos = resumen.progreso.playTime?.seconds,
    medallas = resumen.progreso.badges?.earned,
    medallasTotales = resumen.progreso.badges?.total,
    vistos = resumen.progreso.pokedex?.seen,
    atrapados = resumen.progreso.pokedex?.caught,
    equipo = resumen.equipo.map {
        PokemonCorto(
            especie = it.species,
            especieId = it.speciesId,
            nivel = it.level,
            mote = it.nickname,
            shiny = it.isShiny,
            huevo = it.isEgg,
        )
    },
    formato = resumen.partida.format?.id,
)
