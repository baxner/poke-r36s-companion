package com.baxner.r36scompanion.data.pokedex

import com.baxner.r36scompanion.data.banco.Banco
import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.editor.PokemonSummaryDto
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.editor.aResumenDePokemon
import com.baxner.r36scompanion.data.gen3.PartidaGen3
import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.Puente
import com.baxner.r36scompanion.data.motor.SesionDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull

/**
 * [PokedexGlobal] real, sobre [RepositorioDeRespaldos] (la última versión de partida de cada
 * juego de Pokémon `editable`), [PartidaGen3.pokedex] (vista/atrapada por juego) y
 * [MotorPartidas] (`party.get`/`box.get` para dónde está cada Pokémon ahora, `encounters.search`
 * para dónde atraparlo) más el [Banco]. Ver el KDoc de `PokedexGlobal.kt`.
 */
class PokedexGlobalConRespaldos(
    private val repositorio: RepositorioDeRespaldos,
    private val banco: Banco,
    private val motorPartidas: MotorPartidas,
    /** El motor da los lugares en inglés: `Textos::lugarGen3DesdeIngles` en la app. */
    private val traducirLugar: (String) -> String? = { null },
) : PokedexGlobal {

    override suspend fun entradas(): List<EntradaDePokedex> {
        val partidasPorJuego = juegosEditablesConSuUltimaPartida()

        // "registrado" (visto/atrapado): PartidaGen3.pokedex() de cada partida, sin pasar por el motor.
        val registros: Map<Long, PartidaGen3.Pokedex> = partidasPorJuego.mapNotNull { (juego, bytes) ->
            PartidaGen3.leer(bytes)?.let { juego.id to it.pokedex() }
        }.toMap()

        // "lo tienes": dónde está cada Pokémon ahora, en cada partida (equipo + cajas) y en el banco.
        val ubicacionesEnPartidas = partidasPorJuego.flatMap { (juego, bytes) ->
            motorPartidas.conPartida(bytes, juego.nombre) { todosLosPokemonDelJuego() }
                .mapNotNull { resumen -> resumen.especie?.let { especie -> especie to Ubicacion.EnPartida(juego.id, resumen.casilla, resumen.nivel, resumen.esShiny) } }
        }
        val ubicacionesEnBanco = banco.pokemon().first()
            .filter { it.estado == EstadoEnBanco.EN_BANCO } // los pendientes son transitorios: no se cuentan dos veces.
            .mapNotNull { pokemon -> pokemon.especie?.let { especie -> especie to Ubicacion.EnBanco(pokemon.id, pokemon.nivel, pokemon.esShiny) } }
        val ubicacionesPorEspecie = (ubicacionesEnPartidas + ubicacionesEnBanco).groupBy({ it.first }, { it.second })

        return (1..PartidaGen3.EspeciesNacionales).map { especie ->
            EntradaDePokedex(
                especie = especie,
                vistaEn = registros.filterValues { especie in it.vistos }.keys,
                atrapadaEn = registros.filterValues { especie in it.atrapados }.keys,
                ubicaciones = ubicacionesPorEspecie[especie] ?: emptyList(),
            )
        }
    }

    override suspend fun resumen(): ResumenDePokedex {
        val todas = entradas()
        return ResumenDePokedex(
            total = todas.size,
            registradas = todas.count { it.registrada },
            tenidas = todas.count { it.laTiene },
            vistas = todas.count { it.vistaEn.isNotEmpty() },
        )
    }

    override suspend fun dondeAtrapar(especie: Int): List<DondeAtrapar> =
        juegosEditablesConSuUltimaPartida().flatMap { (juego, bytes) ->
            try {
                motorPartidas.conPartida(bytes, juego.nombre) {
                    val version = llamar("game.version").let { if (it == JsonNull) null else it.decodificar(SaveVersionDto.serializer()) } ?: return@conPartida emptyList()
                    llamar("encounters.search", Puente.numero(version.versionId), Puente.numero(especie))
                        .decodificar(ListSerializer(EncounterRowDto.serializer()))
                        .let { filas -> agruparEncuentros(filas, juego.id, version.versionId, traducirLugar) }
                }
            } catch (e: MotorException) {
                emptyList() // esta partida no soporta `encounters` (SaveCapability): no sale por ninguno de sus juegos.
            }
        }

    /** Los juegos `editable` con los bytes de su última versión de tipo PARTIDA (si el motor nunca la leyó, se descarta). */
    private suspend fun juegosEditablesConSuUltimaPartida(): List<Pair<JuegoRespaldado, ByteArray>> =
        repositorio.juegos().first().filter { it.editable }.mapNotNull { juego ->
            val ultimaVersion = repositorio.versiones(juego.id).first().firstOrNull { it.tipo == TipoDeArchivo.PARTIDA } ?: return@mapNotNull null
            juego to repositorio.bytesDe(ultimaVersion.id)
        }

    private suspend fun SesionDePartida.todosLosPokemonDelJuego(): List<ResumenDePokemon> =
        llamar("party.get").decodificar(ListSerializer(PokemonSummaryDto.serializer())).map { it.aResumenDePokemon() } +
            llamar("box.get").decodificar(ListSerializer(PokemonSummaryDto.serializer())).map { it.aResumenDePokemon() }
}
