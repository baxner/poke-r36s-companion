package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.motor.Puente
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/*
 * Formas crudas del motor para la Pokédex global: `game.version` y `encounters.search` (ver
 * engine-bridge/node_modules/@pkhex-everywhere/engine/dist/generated/types.d.ts, `SaveVersion` y
 * `EncounterRow`, solo lectura).
 */

/** Lo que da `game.version`: el `GameVersion` numérico de PKHeX de la partida cargada, para pasárselo a `encounters.search`. */
@Serializable
data class SaveVersionDto(val version: String = "", val versionId: Int = 0, val generation: String = "", val generationId: Int = 0)

/** Una fila de `encounters.search`. */
@Serializable
data class EncounterRowDto(val speciesId: Int = 0, val name: String = "", val location: String = "", val levelRange: String = "", val isEgg: Boolean = false)

internal fun <T> JsonElement.decodificar(serializador: KSerializer<T>): T = Puente.json.decodeFromJsonElement(serializador, this)

/**
 * Junta las filas de `encounters.search` de un juego en lo que muestra la Pokédex: un renglón por
 * (lugar, forma) con el rango de niveles ("2-5" o "3"), en español con [traducirLugar] (por ejemplo
 * `Textos.lugarGen3DesdeIngles`; si no conoce el lugar, se queda el nombre del motor), y un solo
 * renglón de crianza si alguna fila es de huevo. Puro.
 */
fun agruparEncuentros(
    filas: List<EncounterRowDto>,
    juegoId: Long,
    versionId: Int,
    traducirLugar: (String) -> String?,
): List<DondeAtrapar> {
    val salvajes = filas.filterNot { it.isEgg || com.baxner.r36scompanion.data.textos.TextosDelMotor.formaDeEncuentro(it.name, it.isEgg) == null }
    val agrupados = salvajes
        .groupBy { (traducirLugar(it.location) ?: it.location) to com.baxner.r36scompanion.data.textos.TextosDelMotor.formaDeEncuentro(it.name, it.isEgg) }
        .map { (clave, grupo) ->
            val niveles = grupo.flatMap { nivelesDe(it.levelRange) }
            val texto = if (niveles.isEmpty()) "" else if (niveles.min() == niveles.max()) "${niveles.min()}" else "${niveles.min()}-${niveles.max()}"
            DondeAtrapar(juegoId = juegoId, versionId = versionId, lugar = clave.first, niveles = texto, esHuevo = false, forma = clave.second)
        }
    val crianza = if (filas.any { it.isEgg || it.name.equals("Egg", ignoreCase = true) }) {
        listOf(DondeAtrapar(juegoId = juegoId, versionId = versionId, lugar = "", niveles = "", esHuevo = true, forma = null))
    } else {
        emptyList()
    }
    return agrupados + crianza
}

/** "15-25" → [15, 25]; "3" → [3]; lo que no sea número se ignora. */
private fun nivelesDe(rango: String): List<Int> = rango.split('-').mapNotNull { it.trim().toIntOrNull() }
