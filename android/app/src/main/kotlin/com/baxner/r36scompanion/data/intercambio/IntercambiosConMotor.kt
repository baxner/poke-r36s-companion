package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.EditablePokemonDto
import com.baxner.r36scompanion.data.editor.PokemonHandleDto
import com.baxner.r36scompanion.data.editor.PokemonPatchDto
import com.baxner.r36scompanion.data.editor.aCasilla
import com.baxner.r36scompanion.data.editor.aHandleDto
import com.baxner.r36scompanion.data.editor.aLegalidad
import com.baxner.r36scompanion.data.editor.aResumenDePokemon
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.Puente
import com.baxner.r36scompanion.data.motor.SesionDePartida
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * [Intercambios] real, sobre el motor PKHeX ([MotorPartidas]). Ver el KDoc de `Intercambio.kt`
 * para la receta probada contra el Rojo Fuego real: `transfer.open` → (llegadas con evolución por
 * [EvolucionesPorIntercambio]) → `transfer.preview`/`transfer.commit`.
 */
class IntercambiosConMotor(private val motorPartidas: MotorPartidas) : Intercambios {

    override suspend fun vistaPrevia(a: ByteArray, nombreA: String, b: ByteArray, nombreB: String, oferta: OfertaDeIntercambio): VistaPreviaDeIntercambio =
        conTransferenciaAbierta(a, nombreA, b, nombreB) {
            val (ofertaJson, evoluciones) = construirOferta(oferta)
            val vistaPrevia = llamar("transfer.preview", ofertaJson).decodificar(TransferPreviewDto.serializer())
            interpretarVistaPrevia(vistaPrevia, evoluciones)
        }

    override suspend fun intercambiar(a: ByteArray, nombreA: String, b: ByteArray, nombreB: String, oferta: OfertaDeIntercambio): PartidasIntercambiadas =
        conTransferenciaAbierta(a, nombreA, b, nombreB) {
            val (ofertaJson, _) = construirOferta(oferta)
            val resultado = llamar("transfer.commit", ofertaJson)
            PartidasIntercambiadas(
                partidaA = resultado.campo("save").bytesDelCampo(),
                partidaB = resultado.campo("partner").bytesDelCampo(),
            )
        }

    /** Abre la transferencia con [a] cargada y [b] como compañera, y siempre la cierra al salir (`transfer.close`). */
    private suspend fun <T> conTransferenciaAbierta(a: ByteArray, nombreA: String, b: ByteArray, nombreB: String, bloque: suspend SesionDePartida.() -> T): T =
        motorPartidas.conPartida(a, nombreA) {
            llamar("transfer.open", Puente.bytes(b), Puente.texto(nombreB), Puente.texto(null))
            try {
                bloque()
            } finally {
                runCatching { llamar("transfer.close") }
            }
        }

    /**
     * El `TransferOffer` a mandar, y qué evolución (si alguna) le tocó a cada casilla ofrecida,
     * para que [interpretarVistaPrevia] pueda anotarla en la [LlegadaPrevista] correspondiente.
     */
    private suspend fun SesionDePartida.construirOferta(
        oferta: OfertaDeIntercambio,
    ): Pair<JsonElement, Map<Pair<Direccion, Casilla>, EvolucionesPorIntercambio.Evolucion>> {
        val handlesEnvia = oferta.envia.map { it.aHandleDto() }
        val handlesRecibe = oferta.recibe.map { it.aHandleDto() }
        val evoluciones = mutableMapOf<Pair<Direccion, Casilla>, EvolucionesPorIntercambio.Evolucion>()
        val llegadas = mutableListOf<LlegadaConBytes>()
        if (oferta.evolucionar) {
            oferta.envia.zip(handlesEnvia).forEach { (casilla, handle) ->
                agregarSiEvoluciona(Direccion.DE_A_HACIA_B, casilla, handle, "mine", evoluciones, llegadas)
            }
            oferta.recibe.zip(handlesRecibe).forEach { (casilla, handle) ->
                agregarSiEvoluciona(Direccion.DE_B_HACIA_A, casilla, handle, "partner", evoluciones, llegadas)
            }
        }
        return aJsonDeOferta(handlesEnvia, handlesRecibe, llegadas) to evoluciones
    }

    /** Si el Pokémon de [handle] (en la partida [cual], "mine" o "partner") evoluciona por intercambio, lo agrega a [llegadas]. */
    private suspend fun SesionDePartida.agregarSiEvoluciona(
        direccion: Direccion,
        casilla: Casilla,
        handle: PokemonHandleDto,
        cual: String,
        evoluciones: MutableMap<Pair<Direccion, Casilla>, EvolucionesPorIntercambio.Evolucion>,
        llegadas: MutableList<LlegadaConBytes>,
    ) {
        val handleJson = Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), handle)
        val detalle = llamar("transfer.details", handleJson, Puente.texto(cual)).decodificar(EditablePokemonDto.serializer())
        val especie = detalle.species ?: return
        val evolucion = EvolucionesPorIntercambio.alRecibir(especie, detalle.heldItem, detalle.isEgg) ?: return
        val bytes = llamar("transfer.export", handleJson, Puente.texto(cual)).bytesDelCampo()
        val patch = PokemonPatchDto(species = evolucion.hacia, heldItem = if (evolucion.gastaObjeto) 0 else null)
        llegadas += LlegadaConBytes(at = handle, bytes = bytes, patch = patch)
        evoluciones[direccion to casilla] = evolucion
    }

    private fun interpretarVistaPrevia(
        preview: TransferPreviewDto,
        evoluciones: Map<Pair<Direccion, Casilla>, EvolucionesPorIntercambio.Evolucion>,
    ): VistaPreviaDeIntercambio {
        val llegadas = preview.offers.map { ofrecido ->
            val direccion = ofrecido.direction.aDireccion()
            val casillaOrigen = ofrecido.from.at.aCasilla()
            LlegadaPrevista(
                direccion = direccion,
                sale = ofrecido.from.aResumenDePokemon(),
                llega = ofrecido.arrives.aResumenDePokemon(),
                evolucion = evoluciones[direccion to casillaOrigen],
                cambios = ofrecido.changes.map { it.aTexto() },
                cambiosEnPartidas = ofrecido.saveChanges.map { it.aTexto() },
                legalidad = ofrecido.legality.aLegalidad(),
            )
        }
        val rechazos = preview.refused.map { Rechazo(direccion = it.direction.aDireccion(), casilla = it.at.aCasilla(), motivo = it.reason) }
        return VistaPreviaDeIntercambio(llegadas = llegadas, rechazos = rechazos)
    }
}
