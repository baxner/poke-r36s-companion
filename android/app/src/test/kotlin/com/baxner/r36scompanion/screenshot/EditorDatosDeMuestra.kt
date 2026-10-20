package com.baxner.r36scompanion.screenshot

import com.baxner.r36scompanion.data.editor.Bolsillo
import com.baxner.r36scompanion.data.editor.Caja
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Entrenador
import com.baxner.r36scompanion.data.editor.Estadisticas
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.Medalla
import com.baxner.r36scompanion.data.editor.Movimiento
import com.baxner.r36scompanion.data.editor.ObjetoEnMochila
import com.baxner.r36scompanion.data.editor.OpcionesDePokemon
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.PokemonEditable
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.ui.editor.aBorrador

/**
 * Datos realistas del Rojo Fuego del usuario para las capturas Roborazzi del editor (ver el encargo
 * de esta fase, punto 6): el mismo equipo de `DatosDeMuestra` (Kadabra, Dragonair, Nidoking,
 * Charizard, Machoke, Seadra), 77 Pokémon en las cajas, $999999 y 5 medallas.
 */
object EditorDatosDeMuestra {
    private const val Kadabra = 64
    private const val Dragonair = 148
    private const val Nidoking = 34
    private const val Charizard = 6
    private const val Machoke = 67
    private const val Seadra = 117

    private const val Restos = 200
    private const val RepartirExp = 182

    private fun equipo(casilla: Casilla, especie: Int, nivel: Int, mote: String, editable: Boolean = true) =
        ResumenDePokemon(casilla = casilla, especie = especie, nivel = nivel, mote = mote, esShiny = especie == Seadra, esHuevo = false, editable = editable)

    val equipoEditable: List<ResumenDePokemon> = listOf(
        equipo(Casilla(OrigenDeCasilla.EQUIPO, 0), Kadabra, 53, "KADABRA"),
        equipo(Casilla(OrigenDeCasilla.EQUIPO, 1), Dragonair, 50, "DRAGONAIR"),
        equipo(Casilla(OrigenDeCasilla.EQUIPO, 2), Nidoking, 52, "NIDOKING"),
        equipo(Casilla(OrigenDeCasilla.EQUIPO, 3), Charizard, 58, "Draco"),
        equipo(Casilla(OrigenDeCasilla.EQUIPO, 4), Machoke, 48, "MACHOKE"),
        equipo(Casilla(OrigenDeCasilla.EQUIPO, 5), Seadra, 45, "SEADRA"),
    )

    /** Nidoking no sería legal así (nivel de captura imposible): para ver la insignia ⚠️ en Equipo y Cajas. */
    val legalidadDeEquipo: Map<Casilla, Legalidad> = mapOf(
        Casilla(OrigenDeCasilla.EQUIPO, 2) to Legalidad(valida = false, mensajes = listOf("Level exceeds max for Encounter Type.")),
    )

    /** Especies chicas de Kanto para llenar la caja 1 (ver el encargo: "77 en cajas"). */
    private val EspeciesDeCaja = listOf(19, 16, 13, 10, 41, 74, 21, 23, 27, 32, 58, 63)

    val cajaUno: Caja = Caja(
        numero = 0,
        nombre = "CAJA 1",
        casillas = 30,
        pokemon = (0 until 30).map { posicion ->
            val especie = EspeciesDeCaja[posicion % EspeciesDeCaja.size]
            ResumenDePokemon(
                casilla = Casilla(OrigenDeCasilla.CAJA, posicion, caja = 0),
                especie = especie,
                nivel = 8 + posicion % 15,
                mote = "",
                esShiny = false,
                esHuevo = false,
                editable = true,
            )
        },
    )
    val cajaDos: Caja = Caja(
        numero = 1,
        nombre = "CAJA 2",
        casillas = 30,
        pokemon = (0 until 30).map { posicion ->
            val especie = EspeciesDeCaja[(posicion + 3) % EspeciesDeCaja.size]
            ResumenDePokemon(
                casilla = Casilla(OrigenDeCasilla.CAJA, posicion, caja = 1),
                especie = especie,
                nivel = 10 + posicion % 10,
                mote = "",
                esShiny = false,
                esHuevo = false,
                editable = true,
            )
        },
    )
    val cajaTres: Caja = Caja(
        numero = 2,
        nombre = "CAJA 3",
        casillas = 30,
        pokemon = (0 until 17).map { posicion ->
            val especie = EspeciesDeCaja[(posicion + 6) % EspeciesDeCaja.size]
            ResumenDePokemon(
                casilla = Casilla(OrigenDeCasilla.CAJA, posicion, caja = 2),
                especie = especie,
                nivel = 12 + posicion % 8,
                mote = "",
                esShiny = false,
                esHuevo = false,
                editable = true,
            )
        },
    )

    /** 30 + 30 + 17 = 77 Pokémon en las cajas (ver el encargo, punto 6). */
    val cajas: List<Caja> = listOf(cajaUno, cajaDos, cajaTres)

    val entrenador: Entrenador = Entrenador(nombre = "ROJO", maximoDeLetras = 7, idDeEntrenador = "12345/54321", dinero = 999_999L)

    /** 5 de 8 medallas de Kanto ganadas (ver el encargo, punto 6). */
    val medallasDeKanto: List<Medalla> = listOf("Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth")
        .mapIndexed { indice, nombre -> Medalla(nombreEnIngles = nombre, ganada = indice < 5) }

    val mochila: List<Bolsillo> = listOf(
        Bolsillo(
            nombre = "Items",
            objetos = listOf(ObjetoEnMochila(id = Restos, cantidad = 2, maximo = 99), ObjetoEnMochila(id = 13, cantidad = 5, maximo = 99)),
            agregables = listOf(19, 20, Restos),
        ),
        Bolsillo(nombre = "KeyItems", objetos = listOf(ObjetoEnMochila(id = 259, cantidad = 1, maximo = 1)), agregables = emptyList()),
        Bolsillo(nombre = "Balls", objetos = listOf(ObjetoEnMochila(id = 4, cantidad = 12, maximo = 99)), agregables = listOf(1, 2, 3, 4)),
        Bolsillo(nombre = "TMHMs", objetos = listOf(ObjetoEnMochila(id = 289, cantidad = 1, maximo = 1)), agregables = emptyList()),
        Bolsillo(nombre = "Berries", objetos = emptyList(), agregables = listOf(133, 134)),
        Bolsillo(nombre = "PCItems", objetos = emptyList(), agregables = emptyList()),
    )

    private val estadisticasDeKadabra = Estadisticas(ps = 102, ataque = 68, defensa = 65, ataqueEspecial = 98, defensaEspecial = 80, velocidad = 103)

    /** El Kadabra del equipo, con Restos, como lo pide el encargo (punto 6). */
    val kadabraEditable: PokemonEditable = PokemonEditable(
        especie = Kadabra,
        forma = 0,
        genero = "male",
        naturaleza = 3,
        habilidad = 22,
        objeto = Restos,
        pokeBall = 4,
        amistad = 120,
        idioma = 7,
        esShiny = false,
        esHuevo = false,
        mote = "KADABRA",
        nivel = 53,
        pid = 3_200_063_177L,
        idDeEntrenador = 12345,
        idSecreto = 54321,
        entrenadorOriginal = "ROJO",
        versionDeOrigen = 4,
        lugarDeCaptura = 25,
        nivelDeCaptura = 14,
        ivs = Estadisticas(ps = 18, ataque = 12, defensa = 20, ataqueEspecial = 25, defensaEspecial = 22, velocidad = 28),
        evs = Estadisticas(ps = 28, ataque = 0, defensa = 0, ataqueEspecial = 196, defensaEspecial = 0, velocidad = 60),
        estadisticas = estadisticasDeKadabra,
        movimientos = listOf(Movimiento(93, 10, 10), Movimiento(50, 15, 15), Movimiento(94, 5, 5), Movimiento(0, 0, 0)),
        legalidad = Legalidad(valida = true, mensajes = emptyList()),
    )

    val opcionesDeKadabra: OpcionesDePokemon = OpcionesDePokemon(
        especies = listOf(63, 64, 65),
        habilidades = listOf(22, 32),
        movimientos = listOf(93, 50, 94, 60, 17, 105),
        objetos = listOf(Restos, 196, 199, RepartirExp),
        lugares = listOf(25, 26),
        bloqueados = setOf("nature"),
    )

    val borradorDeKadabra = kadabraEditable.aBorrador()
}
