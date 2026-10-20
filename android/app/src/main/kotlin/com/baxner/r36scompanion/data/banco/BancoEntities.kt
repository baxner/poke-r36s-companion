package com.baxner.r36scompanion.data.banco

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** JSON de [Legalidad] y [Casilla] guardados en las columnas `legalidadJson`/`casillaDeOrigenJson`. */
internal val BancoJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Una fila del banco (ver [PokemonEnBanco] en Banco.kt). */
@Entity(tableName = "pokemon_banco")
data class PokemonEnBancoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bytes: ByteArray,
    val especie: Int?,
    val nivel: Int,
    val mote: String,
    val esShiny: Boolean,
    val esHuevo: Boolean,
    val entrenadorOriginal: String,
    val idDeEntrenador: Int,
    val versionDeOrigen: Int,
    val legalidadJson: String?,
    val estado: EstadoEnBanco,
    val juegoDeOrigen: Long?,
    val casillaDeOrigenJson: String?,
    val depositadoEn: Long,
    val caja: Int,
    val posicion: Int,
)
// Ojo en las pruebas: el equals por defecto de un data class con ByteArray compara `bytes` por
// referencia, no por contenido. Para comparar filas hay que mirar los campos uno por uno (o
// `contentEquals` en el campo `bytes`), no usar `assertEquals` sobre la entidad completa.

fun PokemonEnBancoEntity.aDominio(): PokemonEnBanco = PokemonEnBanco(
    id = id,
    bytes = bytes,
    especie = especie,
    nivel = nivel,
    mote = mote,
    esShiny = esShiny,
    esHuevo = esHuevo,
    entrenadorOriginal = entrenadorOriginal,
    idDeEntrenador = idDeEntrenador,
    versionDeOrigen = versionDeOrigen,
    legalidad = legalidadJson?.let { BancoJson.decodeFromString(Legalidad.serializer(), it) },
    estado = estado,
    juegoDeOrigen = juegoDeOrigen,
    casillaDeOrigen = casillaDeOrigenJson?.let { BancoJson.decodeFromString(Casilla.serializer(), it) },
    depositadoEn = depositadoEn,
    caja = caja,
    posicion = posicion,
)

fun Legalidad.aJson(): String = BancoJson.encodeToString(Legalidad.serializer(), this)

fun Casilla.aJsonDeBanco(): String = BancoJson.encodeToString(Casilla.serializer(), this)

/** Solo las columnas que hacen falta para calcular la primera casilla libre del banco. */
data class CasillaDelBanco(val caja: Int, val posicion: Int)

@Dao
interface PokemonEnBancoDao {
    @Query("SELECT * FROM pokemon_banco ORDER BY caja, posicion")
    fun observarTodos(): Flow<List<PokemonEnBancoEntity>>

    @Query("SELECT * FROM pokemon_banco WHERE id = :id")
    suspend fun porId(id: Long): PokemonEnBancoEntity?

    @Query("SELECT * FROM pokemon_banco WHERE estado != 'EN_BANCO'")
    suspend fun pendientes(): List<PokemonEnBancoEntity>

    @Query("SELECT caja, posicion FROM pokemon_banco")
    suspend fun casillasOcupadas(): List<CasillaDelBanco>

    @Insert
    suspend fun insertar(fila: PokemonEnBancoEntity): Long

    @Update
    suspend fun actualizar(fila: PokemonEnBancoEntity)

    @Query("DELETE FROM pokemon_banco WHERE id = :id")
    suspend fun eliminar(id: Long)
}
