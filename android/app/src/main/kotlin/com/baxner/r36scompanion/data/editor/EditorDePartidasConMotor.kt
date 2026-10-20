package com.baxner.r36scompanion.data.editor

import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.Puente
import com.baxner.r36scompanion.data.motor.SesionDePartida
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * [EditorDePartidas] real, con el motor PKHeX (ver data/motor/MotorPartidas.kt). `abrir` valida
 * que el motor reconozca los bytes (una carga de prueba) y, si sí, entrega una
 * [SesionDeEdicionConMotor] que todavía no le pidió nada al motor.
 */
class EditorDePartidasConMotor(private val motorPartidas: MotorPartidas) : EditorDePartidas {
    override suspend fun abrir(bytes: ByteArray, nombreDeArchivo: String): SesionDeEdicion {
        // Nada que hacer con la partida: solo comprobar que carga. Si el motor la rechaza, esta
        // llamada sube la MotorException y abrir() falla, como documenta el contrato.
        motorPartidas.conPartida(bytes, nombreDeArchivo) {}
        return SesionDeEdicionConMotor(motorPartidas, bytes, nombreDeArchivo)
    }
}

/**
 * [SesionDeEdicion] real. El motor solo tiene una partida cargada a la vez (ver KDoc de
 * `Editor.kt`), así que ninguna función de aquí la deja cargada: cada una hace su propio
 * `motorPartidas.conPartida(bytesActuales, nombre) { … }` (carga → opera → el bloque cierra la
 * partida al salir, la haya dejado igual o no).
 *
 * Dos sabores de operación:
 * - [leer]: para lecturas y `probar`. Nunca exporta, así que `bytesActuales` no cambia.
 * - [escribir]: para `aplicar` y los cambios de entrenador/mochila/medallas. Exporta al final y
 *   actualiza `bytesActuales` con lo que haya dado el motor.
 */
class SesionDeEdicionConMotor internal constructor(
    private val motorPartidas: MotorPartidas,
    bytesIniciales: ByteArray,
    private val nombreDeArchivo: String,
) : SesionDeEdicion {
    private val bytesIniciales: ByteArray = bytesIniciales.copyOf()

    override val base: String = AlmacenDeBlobs.sha256Hex(this.bytesIniciales)

    override var bytesActuales: ByteArray = this.bytesIniciales.copyOf()
        private set

    override val hayCambios: Boolean
        get() = !bytesActuales.contentEquals(bytesIniciales)

    private suspend fun <T> leer(bloque: suspend SesionDePartida.() -> T): T =
        motorPartidas.conPartida(bytesActuales, nombreDeArchivo, bloque = bloque)

    private suspend fun <T> escribir(bloque: suspend SesionDePartida.() -> T): T =
        motorPartidas.conPartida(bytesActuales, nombreDeArchivo) {
            val resultado = bloque()
            bytesActuales = exportar()
            resultado
        }

    override suspend fun equipo(): List<ResumenDePokemon> = leer { leerEquipo() }

    override suspend fun cajas(): List<Caja> = leer { leerCajas() }

    override suspend fun detalle(casilla: Casilla): PokemonEditable = leer { leerDetalle(casilla) }

    override suspend fun opciones(casilla: Casilla): OpcionesDePokemon = leer {
        llamar("pokemon.options", casilla.aJsonDeHandle()).decodificar(PokemonOptionsDto.serializer()).aOpcionesDePokemon()
    }

    override suspend fun probar(casilla: Casilla, cambio: CambioDePokemon): PokemonEditable = leer {
        actualizarPokemon(casilla, cambio)
        leerDetalle(casilla)
    }

    override suspend fun aplicar(casilla: Casilla, cambio: CambioDePokemon): PokemonEditable = escribir {
        actualizarPokemon(casilla, cambio)
        leerDetalle(casilla)
    }

    override suspend fun entrenador(): Entrenador = leer {
        llamar("trainer.get").decodificar(TrainerCardDto.serializer()).aEntrenador()
    }

    override suspend fun cambiarDinero(dinero: Long) {
        escribir { llamar("trainer.setMoney", Puente.numero(dinero)) }
    }

    override suspend fun cambiarNombre(nombre: String) {
        escribir { llamar("trainer.setName", Puente.texto(nombre)) }
    }

    override suspend fun medallas(): List<Medalla> = leer { leerMedallas() }

    override suspend fun cambiarMedallas(ganadas: List<Boolean>) {
        escribir { llamar("trainer.setBadges", JsonArray(ganadas.map { JsonPrimitive(it) })) }
    }

    override suspend fun mochila(): List<Bolsillo> = leer {
        llamar("inventory.get").decodificar(ListSerializer(PouchDto.serializer())).map { it.aBolsillo() }
    }

    override suspend fun cambiarObjeto(bolsillo: String, id: Int, cantidad: Int) {
        escribir {
            llamar(
                "inventory.setItem",
                Puente.json.encodeToJsonElement(ItemHandleDto.serializer(), ItemHandleDto(pouch = bolsillo, itemId = id)),
                Puente.numero(cantidad),
            )
        }
    }

    override fun deshacerTodo() {
        bytesActuales = bytesIniciales.copyOf()
    }

    override suspend fun revisarAntesDeGuardar(): RevisionDeGuardado = try {
        motorPartidas.conPartida(bytesActuales, nombreDeArchivo) {
            val equipo = leerEquipo()
            val cajas = leerCajas()
            val casillas = equipo.map { it.casilla } + cajas.flatMap { caja -> caja.pokemon.map { it.casilla } }
            val ilegales = casillas.mapNotNull { casilla ->
                val legalidad = leerDetalle(casilla).legalidad
                if (legalidad != null && !legalidad.valida) casilla to legalidad else null
            }
            RevisionDeGuardado(abre = true, pokemonIlegales = ilegales, problemas = emptyList())
        }
    } catch (e: MotorException) {
        RevisionDeGuardado(abre = false, pokemonIlegales = emptyList(), problemas = listOf("${e.codigo}: ${e.message}"))
    }
}

private suspend fun SesionDePartida.leerEquipo(): List<ResumenDePokemon> =
    llamar("party.get").decodificar(ListSerializer(PokemonSummaryDto.serializer())).map { it.aResumenDePokemon() }

private suspend fun SesionDePartida.leerCajas(): List<Caja> {
    val metadatos = llamar("box.list").decodificar(ListSerializer(BoxEntryDto.serializer()))
    val pokemon = llamar("box.get").decodificar(ListSerializer(PokemonSummaryDto.serializer()))
    return aCajas(metadatos, pokemon)
}

private suspend fun SesionDePartida.leerDetalle(casilla: Casilla): PokemonEditable =
    llamar("pokemon.details", casilla.aJsonDeHandle()).decodificar(EditablePokemonDto.serializer()).aPokemonEditable()

private suspend fun SesionDePartida.actualizarPokemon(casilla: Casilla, cambio: CambioDePokemon) {
    llamar("pokemon.update", casilla.aJsonDeHandle(), Puente.json.encodeToJsonElement(PokemonPatchDto.serializer(), cambio.aPatchDto()))
}

private suspend fun SesionDePartida.leerMedallas(): List<Medalla> {
    val elemento = llamar("trainer.badges")
    if (elemento is JsonNull) return emptyList()
    return elemento.decodificar(ListSerializer(BadgeDto.serializer())).map { it.aMedalla() }
}

private fun Casilla.aJsonDeHandle(): JsonElement = Puente.json.encodeToJsonElement(PokemonHandleDto.serializer(), aHandleDto())

private fun <T> JsonElement.decodificar(serializador: KSerializer<T>): T = Puente.json.decodeFromJsonElement(serializador, this)
