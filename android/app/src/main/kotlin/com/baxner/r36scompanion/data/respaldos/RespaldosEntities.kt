package com.baxner.r36scompanion.data.respaldos

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json

/** JSON de los [ResumenGuardado] guardados en `juegos.resumenJson` y `versiones.resumenJson`. */
internal val RespaldosJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Un juego de la consola (ver [JuegoRespaldado]). Se identifica por ([sistema], [nombre]): el
 * nombre de su ROM sin extensión, igual al de sus archivos de partida y estado. Las columnas de
 * "estado actual" ([esPokemon], [editable], [resumenJson], [estadoDePartida], [ultimoRespaldo])
 * son un resumen de su última versión de tipo [TipoDeArchivo.PARTIDA] — se recalculan ahí mismo
 * cuando se registra una ([NucleoDeRespaldos.registrarVersion]) en vez de volver a consultar
 * `versiones` cada vez que se lista `juegos` (igual que `DeviceEntity.lastDeviceJson` en Vigía).
 */
@Entity(tableName = "juegos", indices = [Index("sistema", "nombre", unique = true)])
data class JuegoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sistema: String,
    val nombre: String,
    val rutaDePartida: String,
    val esPokemon: Boolean,
    val editable: Boolean,
    val ultimoRespaldo: Long?,
    val resumenJson: String?,
    val estadoDePartida: EstadoDePartida,
)

fun JuegoEntity.aDominio(): JuegoRespaldado = JuegoRespaldado(
    id = id,
    sistema = sistema,
    nombre = nombre,
    rutaDePartida = rutaDePartida,
    esPokemon = esPokemon,
    editable = editable,
    ultimoRespaldo = ultimoRespaldo,
    resumen = resumenJson?.let { RespaldosJson.decodeFromString(ResumenGuardado.serializer(), it) },
    estadoDePartida = estadoDePartida,
)

/** Una versión respaldada de un archivo (ver [VersionRespaldada]). */
@Entity(
    tableName = "versiones",
    foreignKeys = [
        ForeignKey(entity = JuegoEntity::class, parentColumns = ["id"], childColumns = ["juegoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("juegoId"), Index("rutaEnConsola")],
)
data class VersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val juegoId: Long,
    val tipo: TipoDeArchivo,
    val rutaEnConsola: String,
    val sha256: String,
    val tamano: Long,
    val modificadoEnConsola: Long,
    val respaldadaEn: Long,
    val origen: OrigenDeVersion,
    val estadoDePartida: EstadoDePartida,
    val resumenJson: String?,
)

fun VersionEntity.aDominio(): VersionRespaldada = VersionRespaldada(
    id = id,
    juegoId = juegoId,
    tipo = tipo,
    rutaEnConsola = rutaEnConsola,
    sha256 = sha256,
    tamano = tamano,
    modificadoEnConsola = modificadoEnConsola,
    respaldadaEn = respaldadaEn,
    origen = origen,
    estadoDePartida = estadoDePartida,
    resumen = resumenJson?.let { RespaldosJson.decodeFromString(ResumenGuardado.serializer(), it) },
)
