package com.baxner.r36scompanion.data.respaldos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Base de datos de los respaldos (fase 2, ver docs/plan.md). Versión 1: todavía no hay nada que
 * migrar. Si el esquema cambia a futuro, sumar una [androidx.room.migration.Migration] real en
 * vez de `fallbackToDestructiveMigration`: es el historial de partidas del usuario, no una caché. El
 * JSON de cada versión exportada (`room.schemaLocation` en app/build.gradle.kts) queda en
 * app/schemas/ para poder escribir esas migraciones contra el esquema real.
 *
 * Los bytes de cada versión NO viven aquí: están en [AlmacenDeBlobs], aparte, direccionados por
 * el `sha256` de `versiones`.
 */
@Database(entities = [JuegoEntity::class, VersionEntity::class], version = 1, exportSchema = true)
@TypeConverters(RespaldosConverters::class)
abstract class RespaldosDatabase : RoomDatabase() {
    abstract fun respaldosDao(): RespaldosDao

    companion object {
        private const val NombreDeLaBase = "respaldos.db"

        fun crear(context: Context): RespaldosDatabase =
            Room.databaseBuilder(context, RespaldosDatabase::class.java, NombreDeLaBase).build()
    }
}

/** Room no serializa enums de Kotlin por su cuenta: se persiste el nombre de la constante. */
class RespaldosConverters {
    @TypeConverter
    fun tipoDeArchivoATexto(valor: TipoDeArchivo): String = valor.name

    @TypeConverter
    fun textoATipoDeArchivo(valor: String): TipoDeArchivo = TipoDeArchivo.valueOf(valor)

    @TypeConverter
    fun origenATexto(valor: OrigenDeVersion): String = valor.name

    @TypeConverter
    fun textoAOrigen(valor: String): OrigenDeVersion = OrigenDeVersion.valueOf(valor)

    @TypeConverter
    fun estadoDePartidaATexto(valor: EstadoDePartida): String = valor.name

    @TypeConverter
    fun textoAEstadoDePartida(valor: String): EstadoDePartida = EstadoDePartida.valueOf(valor)
}
