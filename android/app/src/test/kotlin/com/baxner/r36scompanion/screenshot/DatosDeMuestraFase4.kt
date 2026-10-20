package com.baxner.r36scompanion.screenshot

import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.pokedex.DondeAtrapar
import com.baxner.r36scompanion.data.pokedex.EntradaDePokedex
import com.baxner.r36scompanion.data.pokedex.ResumenDePokedex
import com.baxner.r36scompanion.data.pokedex.Ubicacion
import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado

/**
 * Datos de la fase 4 para las capturas Roborazzi (Pokédex global, Banco, Intercambio rápido): la
 * Pokédex real del usuario en Rojo Fuego (78 atrapados, 111 vistos: ver docs/plan.md, "Primeras
 * pruebas con las partidas reales", y el KDoc de `PartidaGen3.kt`), un Spearow Nv3 en el banco y
 * el intercambio Kadabra → Alakazam (docs/plan.md, "Prueba técnica del intercambio real"... y
 * "Diseño técnico de las fases 3 a 5"). Reutiliza [DatosDeMuestra.juegoRojoFuego] para no
 * duplicar al mismo el usuario dos veces.
 */
object DatosDeMuestraFase4 {
    const val Bulbasaur = 1
    const val Spearow = 21
    const val Kadabra = 64
    const val Alakazam = 65

    val juegoEsmeralda = JuegoRespaldado(
        id = 6,
        sistema = "gba",
        nombre = "Pokemon Edicion Esmeralda",
        rutaDePartida = "/roms/gba/Pokemon Edicion Esmeralda.srm",
        esPokemon = true,
        editable = true,
        ultimoRespaldo = DatosDeMuestra.juegoRojoFuego.ultimoRespaldo,
        resumen = ResumenGuardado(version = "Emerald", entrenador = "Rojo", idDeEntrenador = "12345/54321", atrapados = 40, vistos = 60),
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )

    val juegosEditables = listOf(DatosDeMuestra.juegoRojoFuego, juegoEsmeralda)
    val juegosPorId = juegosEditables.associateBy { it.id }

    /** Las 386 entradas de la Pokédex global: 78 atrapadas (74 las tiene, 4 ya no), 33 solo vistas (111 en total). */
    val entradasPokedex: List<EntradaDePokedex> = (1..386).map { especie ->
        when {
            especie <= 78 -> EntradaDePokedex(
                especie = especie,
                vistaEn = setOf(DatosDeMuestra.juegoRojoFuego.id),
                atrapadaEn = setOf(DatosDeMuestra.juegoRojoFuego.id),
                ubicaciones = if (especie > 74) emptyList() else listOf(ubicacionDeMuestra(especie)),
            )
            especie <= 111 -> EntradaDePokedex(
                especie = especie,
                vistaEn = setOf(DatosDeMuestra.juegoRojoFuego.id),
                atrapadaEn = emptySet(),
                ubicaciones = emptyList(),
            )
            else -> EntradaDePokedex(especie = especie, vistaEn = emptySet(), atrapadaEn = emptySet(), ubicaciones = emptyList())
        }
    }

    private fun ubicacionDeMuestra(especie: Int): Ubicacion = when (especie) {
        Kadabra -> Ubicacion.EnPartida(DatosDeMuestra.juegoRojoFuego.id, Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0), nivel = 54, esShiny = false)
        else -> Ubicacion.EnPartida(DatosDeMuestra.juegoRojoFuego.id, Casilla(OrigenDeCasilla.EQUIPO, posicion = especie % 6), nivel = 30 + especie % 20, esShiny = especie == 41)
    }

    val resumenPokedex = ResumenDePokedex(total = 386, registradas = 78, tenidas = 74, vistas = 111)

    /** Spearow: registrado (atrapado y visto) en Rojo Fuego, pero ya no en la partida — lo depositó en el banco. */
    val entradaDeSpearow = EntradaDePokedex(
        especie = Spearow,
        vistaEn = setOf(DatosDeMuestra.juegoRojoFuego.id),
        atrapadaEn = setOf(DatosDeMuestra.juegoRojoFuego.id),
        ubicaciones = listOf(Ubicacion.EnBanco(pokemonId = 1, nivel = 3, esShiny = false)),
    )
    val dondeAtraparSpearow = listOf(
        DondeAtrapar(juegoId = DatosDeMuestra.juegoRojoFuego.id, versionId = 4, lugar = "Route 3", niveles = "3-5", esHuevo = false),
    )

    /** Kadabra: registrado y en la caja 1, casilla 1 de Rojo Fuego. No sale por encuentro normal (solo evoluciona de Abra). */
    val entradaDeKadabra = entradasPokedex[Kadabra - 1]
    val dondeAtraparKadabra = emptyList<DondeAtrapar>()

    val pokemonSpearowEnBanco = PokemonEnBanco(
        id = 1,
        bytes = ByteArray(80),
        especie = Spearow,
        nivel = 3,
        mote = "",
        esShiny = false,
        esHuevo = false,
        entrenadorOriginal = "ROJO",
        idDeEntrenador = 12345,
        versionDeOrigen = 4,
        legalidad = null,
        estado = EstadoEnBanco.EN_BANCO,
        juegoDeOrigen = DatosDeMuestra.juegoRojoFuego.id,
        casillaDeOrigen = Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0),
        depositadoEn = 0,
        caja = 0,
        posicion = 0,
    )

    val pokemonPendienteDeDeposito = pokemonSpearowEnBanco.copy(
        id = 2,
        especie = Bulbasaur,
        nivel = 12,
        mote = "",
        estado = EstadoEnBanco.PENDIENTE_DE_DEPOSITO,
        caja = 0,
        posicion = 1,
    )

    val resumenDePokemonKadabra = ResumenDePokemon(
        casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0),
        especie = Kadabra,
        nivel = 54,
        mote = "Kadabra",
        esShiny = false,
        esHuevo = false,
        editable = true,
    )

    val resumenDePokemonSeadra = ResumenDePokemon(
        casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 1, caja = 0),
        especie = 117,
        nivel = 45,
        mote = "",
        esShiny = true,
        esHuevo = false,
        editable = true,
    )

    val resumenDePokemonMew = ResumenDePokemon(
        casilla = Casilla(OrigenDeCasilla.CAJA, posicion = 0, caja = 0),
        especie = 151,
        nivel = 30,
        mote = "",
        esShiny = false,
        esHuevo = false,
        editable = false,
    )
}
