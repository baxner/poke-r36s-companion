package com.baxner.r36scompanion.data.respaldos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RespaldosDao {
    @Query("SELECT * FROM juegos WHERE sistema = :sistema AND nombre = :nombre")
    suspend fun buscarJuego(sistema: String, nombre: String): JuegoEntity?

    @Query("SELECT * FROM juegos WHERE id = :id")
    suspend fun juegoPorId(id: Long): JuegoEntity?

    @Insert
    suspend fun insertarJuego(juego: JuegoEntity): Long

    @Query(
        "UPDATE juegos SET esPokemon = :esPokemon, editable = :editable, estadoDePartida = :estadoDePartida, " +
            "resumenJson = :resumenJson, ultimoRespaldo = :ultimoRespaldo WHERE id = :id",
    )
    suspend fun actualizarResumenDeJuego(
        id: Long,
        esPokemon: Boolean,
        editable: Boolean,
        estadoDePartida: EstadoDePartida,
        resumenJson: String?,
        ultimoRespaldo: Long,
    )

    @Query("UPDATE juegos SET ultimoRespaldo = :momento WHERE id = :id")
    suspend fun tocarUltimoRespaldo(id: Long, momento: Long)

    /** De lo más reciente a lo más viejo; los que nunca se han respaldado (`ultimoRespaldo` null) quedan al final. */
    @Query("SELECT * FROM juegos ORDER BY ultimoRespaldo DESC")
    fun observarJuegos(): Flow<List<JuegoEntity>>

    @Query("SELECT id FROM juegos")
    suspend fun idsDeJuegos(): List<Long>

    @Insert
    suspend fun insertarVersion(version: VersionEntity): Long

    @Query("SELECT * FROM versiones WHERE id = :id")
    suspend fun versionPorId(id: Long): VersionEntity?

    @Query("SELECT * FROM versiones WHERE juegoId = :juegoId ORDER BY respaldadaEn DESC")
    fun observarVersiones(juegoId: Long): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versiones WHERE juegoId = :juegoId")
    suspend fun versionesDeJuego(juegoId: Long): List<VersionEntity>

    /** La versión más reciente conocida de un archivo, para decidir si hace falta bajarlo de nuevo. */
    @Query("SELECT * FROM versiones WHERE rutaEnConsola = :ruta ORDER BY respaldadaEn DESC LIMIT 1")
    suspend fun ultimaVersionDeRuta(ruta: String): VersionEntity?
}
