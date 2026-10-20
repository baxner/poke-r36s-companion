package com.baxner.r36scompanion.data.banco

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Base de datos propia del banco (`banco.db`), separada de `respaldos.db`
 * ([com.baxner.r36scompanion.data.respaldos.RespaldosDatabase]). Decisión (ver AGENTS.md,
 * sección del contrato de banco): una base aparte en vez de agregar la tabla a
 * `RespaldosDatabase` porque son dos cosas distintas con ciclos de vida distintos.
 * `RespaldosDatabase` es el historial de lo que hubo en la consola (nunca se borra una versión,
 * "es el historial de partidas del usuario, no una caché"); el banco es al revés, un inventario
 * mutable con su propio estado (EN_BANCO/PENDIENTE_*) que sí se actualiza y borra fila por fila
 * al confirmar o cancelar un movimiento. Mezclarlas en una sola base habría obligado a migrar
 * `RespaldosDatabase` (que ya tiene datos reales del usuario) por algo que no comparte ninguna
 * clave foránea real con `juegos`/`versiones` (el banco solo guarda el id de juego como referencia
 * suelta, para mostrarlo y para [conciliar], no como relación que Room deba mantener). Separarlas
 * deja cada migración evolucionar sola. El esquema se exporta igual a `app/schemas/` (mismo
 * `room.schemaLocation` en build.gradle.kts, agrupado por el nombre de esta clase).
 */
@Database(entities = [PokemonEnBancoEntity::class], version = 1, exportSchema = true)
@TypeConverters(BancoConverters::class)
abstract class BancoDatabase : RoomDatabase() {
    abstract fun bancoDao(): PokemonEnBancoDao

    companion object {
        private const val NombreDeLaBase = "banco.db"

        fun crear(context: Context): BancoDatabase =
            Room.databaseBuilder(context, BancoDatabase::class.java, NombreDeLaBase).build()
    }
}

class BancoConverters {
    @TypeConverter
    fun estadoATexto(valor: EstadoEnBanco): String = valor.name

    @TypeConverter
    fun textoAEstado(valor: String): EstadoEnBanco = EstadoEnBanco.valueOf(valor)
}
