package com.baxner.r36scompanion.data.banco

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.EditablePokemonDto
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.editor.PokemonSummaryDto
import com.baxner.r36scompanion.data.editor.aCasilla
import com.baxner.r36scompanion.data.editor.aHandleDto
import com.baxner.r36scompanion.data.editor.aLegalidad
import com.baxner.r36scompanion.data.editor.aPokemonEditable
import com.baxner.r36scompanion.data.gen3.PartidaGen3
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.Puente
import com.baxner.r36scompanion.data.motor.SesionDePartida
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.Reloj
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * [Banco] real, sobre el motor PKHeX ([MotorPartidas]) y la base propia [BancoDatabase] (ver su
 * KDoc para la decisión de usar una base aparte). Ver el KDoc de `Banco.kt` para las dos fases de
 * cada movimiento.
 */
class BancoConMotor(
    private val db: BancoDatabase,
    private val motorPartidas: MotorPartidas,
    private val reloj: Reloj,
) : Banco {
    private val dao get() = db.bancoDao()

    override fun pokemon(): Flow<List<PokemonEnBanco>> = dao.observarTodos().map { filas -> filas.map { it.aDominio() } }

    override suspend fun prepararDeposito(juegoId: Long, partida: ByteArray, nombreDeArchivo: String, casilla: Casilla): MovimientoPreparado {
        require(casilla.origen == OrigenDeCasilla.CAJA && casilla.caja != null) { "en la v1 solo se deposita desde las cajas" }
        val caja = casilla.caja
        val partidaGen3 = PartidaGen3.leer(partida) ?: error("no es una partida de la 3.ª generación")
        check(!partidaGen3.casillaVacia(caja, casilla.posicion)) { "la casilla $casilla está vacía" }

        val (detalle, bytesExportados, resumenAntes) = motorPartidas.conPartida(partida, nombreDeArchivo) {
            val detalle = llamar("pokemon.details", casilla.aJsonDeHandle()).decodificar(EditablePokemonDto.serializer()).aPokemonEditable()
            val bytes = llamar("pokemon.export", casilla.aJsonDeHandle()).bytesDelCampo("bytes")
            Triple(detalle, bytes, resumenCompletoDePokemon())
        }

        val partidaSinEl = partidaGen3.conCasillaVacia(caja, casilla.posicion)
        val resumenDespues = motorPartidas.conPartida(partidaSinEl, nombreDeArchivo) { resumenCompletoDePokemon() }
        verificarSoloFalta(resumenAntes, resumenDespues, casilla)

        val (nuevaCaja, nuevaPosicion) = primeraCasillaLibreEnElBanco()
        val id = dao.insertar(
            PokemonEnBancoEntity(
                bytes = bytesExportados,
                especie = detalle.especie,
                nivel = detalle.nivel,
                mote = detalle.mote,
                esShiny = detalle.esShiny,
                esHuevo = detalle.esHuevo,
                entrenadorOriginal = detalle.entrenadorOriginal,
                idDeEntrenador = detalle.idDeEntrenador,
                versionDeOrigen = detalle.versionDeOrigen,
                legalidadJson = detalle.legalidad?.aJson(),
                estado = EstadoEnBanco.PENDIENTE_DE_DEPOSITO,
                juegoDeOrigen = juegoId,
                casillaDeOrigenJson = casilla.aJsonDeBanco(),
                depositadoEn = reloj.ahoraMs(),
                caja = nuevaCaja,
                posicion = nuevaPosicion,
            ),
        )
        return MovimientoPreparado(
            pokemonId = id,
            juegoId = juegoId,
            partidaNueva = partidaSinEl,
            base = AlmacenDeBlobs.sha256Hex(partida),
            casilla = casilla,
            legalidadEnDestino = null,
            cambios = emptyList(),
        )
    }

    override suspend fun prepararRetiro(pokemonId: Long, juegoId: Long, partida: ByteArray, nombreDeArchivo: String): MovimientoPreparado {
        val fila = dao.porId(pokemonId) ?: throw NoSuchElementException("no existe el Pokémon $pokemonId en el banco")
        check(fila.estado == EstadoEnBanco.EN_BANCO) { "el Pokémon $pokemonId ya tiene un movimiento pendiente" }

        val (añadido, previa, partidaConEl) = motorPartidas.conPartida(partida, nombreDeArchivo) {
            val previa = llamar("box.previewFile", Puente.bytes(fila.bytes)).decodificar(FilePreviewDto.serializer())
            // Sin `allowUnofficial`: si ningún juego puede tener esta especie, que falle con
            // `conversion-failed` en vez de meterla igual (ver el contrato: "o si el juego no
            // puede tener ese Pokémon").
            val añadido = llamar("box.addFromFile", Puente.bytes(fila.bytes)).decodificar(AddedPokemonDto.serializer())
            Triple(añadido, previa, exportar())
        }

        val casillaDestino = añadido.at.aCasilla()
        dao.actualizar(
            fila.copy(
                estado = EstadoEnBanco.PENDIENTE_DE_RETIRO,
                juegoDeOrigen = juegoId,
                casillaDeOrigenJson = casillaDestino.aJsonDeBanco(),
            ),
        )
        return MovimientoPreparado(
            pokemonId = pokemonId,
            juegoId = juegoId,
            partidaNueva = partidaConEl,
            base = AlmacenDeBlobs.sha256Hex(partida),
            casilla = casillaDestino,
            legalidadEnDestino = previa.legality?.aLegalidad(),
            cambios = previa.changes.map { it.aTexto() },
        )
    }

    override suspend fun confirmar(movimiento: MovimientoPreparado) {
        val fila = filaPendiente(movimiento.pokemonId)
        when (fila.estado) {
            EstadoEnBanco.PENDIENTE_DE_DEPOSITO -> dao.actualizar(fila.copy(estado = EstadoEnBanco.EN_BANCO))
            EstadoEnBanco.PENDIENTE_DE_RETIRO -> dao.eliminar(fila.id)
            EstadoEnBanco.EN_BANCO -> error("no hay nada pendiente que confirmar para ${movimiento.pokemonId}")
        }
    }

    override suspend fun cancelar(movimiento: MovimientoPreparado) {
        val fila = filaPendiente(movimiento.pokemonId)
        when (fila.estado) {
            EstadoEnBanco.PENDIENTE_DE_DEPOSITO -> dao.eliminar(fila.id)
            EstadoEnBanco.PENDIENTE_DE_RETIRO -> dao.actualizar(fila.copy(estado = EstadoEnBanco.EN_BANCO))
            EstadoEnBanco.EN_BANCO -> error("no hay nada pendiente que cancelar para ${movimiento.pokemonId}")
        }
    }

    override suspend fun pendientes(): List<PokemonEnBanco> = dao.pendientes().map { it.aDominio() }

    override suspend fun conciliar(pokemonId: Long, partidaEnConsola: ByteArray, nombreDeArchivo: String) {
        val fila = dao.porId(pokemonId) ?: throw NoSuchElementException("no existe el Pokémon $pokemonId en el banco")
        if (fila.estado == EstadoEnBanco.EN_BANCO) return // nada pendiente que conciliar

        val casilla = fila.casillaDeOrigenJson?.let { BancoJson.decodeFromString(Casilla.serializer(), it) }
            ?: error("el Pokémon $pokemonId no tiene casilla registrada para conciliar")
        val cajaDeLaCasilla = casilla.caja ?: error("casilla sin caja: $casilla")
        val partidaGen3 = PartidaGen3.leer(partidaEnConsola) ?: error("no es una partida de la 3.ª generación")
        val vacia = partidaGen3.casillaVacia(cajaDeLaCasilla, casilla.posicion)

        when (fila.estado) {
            // Depósito: si la casilla de origen sigue ocupada, la escritura nunca llegó a la
            // consola (se cancela, el Pokémon se queda en el juego); si está vacía, sí llegó (se
            // confirma, el Pokémon se queda en el banco).
            EstadoEnBanco.PENDIENTE_DE_DEPOSITO -> if (vacia) dao.actualizar(fila.copy(estado = EstadoEnBanco.EN_BANCO)) else dao.eliminar(fila.id)
            // Retiro: si la casilla de destino ya tiene algo, la escritura sí llegó (se confirma,
            // el Pokémon sale del banco); si sigue vacía, no llegó (se cancela, vuelve EN_BANCO).
            EstadoEnBanco.PENDIENTE_DE_RETIRO -> if (!vacia) dao.eliminar(fila.id) else dao.actualizar(fila.copy(estado = EstadoEnBanco.EN_BANCO))
            EstadoEnBanco.EN_BANCO -> Unit
        }
    }

    override suspend fun mover(pokemonId: Long, caja: Int, posicion: Int) {
        val fila = dao.porId(pokemonId) ?: throw NoSuchElementException("no existe el Pokémon $pokemonId en el banco")
        val ocupada = dao.casillasOcupadas().any { it.caja == caja && it.posicion == posicion && !(it.caja == fila.caja && it.posicion == fila.posicion) }
        check(!ocupada) { "la casilla $caja/$posicion del banco ya está ocupada" }
        dao.actualizar(fila.copy(caja = caja, posicion = posicion))
    }

    private suspend fun filaPendiente(pokemonId: Long): PokemonEnBancoEntity =
        dao.porId(pokemonId) ?: throw NoSuchElementException("no existe el Pokémon $pokemonId en el banco")

    private suspend fun primeraCasillaLibreEnElBanco(): Pair<Int, Int> {
        val ocupadas = dao.casillasOcupadas().map { it.caja to it.posicion }.toSet()
        var caja = 0
        while (true) {
            for (posicion in 0 until PartidaGen3.CasillasPorCaja) {
                if ((caja to posicion) !in ocupadas) return caja to posicion
            }
            caja++
        }
    }

    private fun Casilla.aJsonDeHandle(): JsonElement = Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), aHandleDto())

    private suspend fun SesionDePartida.resumenCompletoDePokemon(): List<PokemonSummaryDto> =
        llamar("party.get").decodificar(ListSerializer(PokemonSummaryDto.serializer())) +
            llamar("box.get").decodificar(ListSerializer(PokemonSummaryDto.serializer()))

    /**
     * Comprueba, con lo que de verdad dice el motor, que entre [antes] y [después] solo falta el
     * Pokémon de [casillaVaciada]: el mismo total menos uno, y el resto igual por especie y nivel
     * en sus casillas (ver docs/plan.md, "Banco (fase 4)", probado con el Rojo Fuego real).
     */
    private fun verificarSoloFalta(antes: List<PokemonSummaryDto>, despues: List<PokemonSummaryDto>, casillaVaciada: Casilla) {
        check(despues.size == antes.size - 1) {
            "se esperaba un Pokémon menos tras vaciar $casillaVaciada (antes ${antes.size}, después ${despues.size})"
        }
        val mapaDespues = despues.associateBy { it.at.aCasilla() }
        for (pokemon in antes) {
            val casilla = pokemon.at.aCasilla()
            if (casilla == casillaVaciada) continue
            val enDespues = mapaDespues[casilla] ?: error("falta un Pokémon que no era el que se sacó: $casilla")
            check(enDespues.speciesId == pokemon.speciesId && enDespues.level == pokemon.level) {
                "cambió otro Pokémon además del que se sacó, en $casilla"
            }
        }
    }
}
