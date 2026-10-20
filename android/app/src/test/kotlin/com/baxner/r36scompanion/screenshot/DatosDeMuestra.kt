package com.baxner.r36scompanion.screenshot

import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.OrigenDeVersion
import com.baxner.r36scompanion.data.respaldos.PokemonCorto
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada

/**
 * Datos falsos realistas para las capturas Roborazzi (ver el encargo, punto 9): el Rojo Fuego de
 * "Rojo" con su equipo real (docs/plan.md, "Primeras pruebas con las partidas reales": Kadabra,
 * Dragonair, Nidoking, Charizard, Machoke, Seadra), Verde Hoja vacía, un hack que el motor no
 * reconoce y un juego que no es de Pokémon. Los números de Pokédex nacional son los reales.
 */
object DatosDeMuestra {
    private const val Kadabra = 64
    private const val Dragonair = 148
    private const val Nidoking = 34
    private const val Charizard = 6
    private const val Machoke = 67
    private const val Horsea = 116
    private const val Seadra = 117

    val equipoDeRojoFuego = listOf(
        PokemonCorto(especie = "Kadabra", especieId = Kadabra, nivel = 54, mote = "Kadabra"),
        PokemonCorto(especie = "Dragonair", especieId = Dragonair, nivel = 50),
        PokemonCorto(especie = "Nidoking", especieId = Nidoking, nivel = 52),
        PokemonCorto(especie = "Charizard", especieId = Charizard, nivel = 58, mote = "Draco"),
        PokemonCorto(especie = "Machoke", especieId = Machoke, nivel = 48),
        PokemonCorto(especie = "Seadra", especieId = Seadra, nivel = 45, shiny = true),
    )

    val resumenRojoFuego = ResumenGuardado(
        version = "FireRed",
        entrenador = "Rojo",
        idDeEntrenador = "12345/54321",
        dinero = 48_250,
        horas = 34,
        minutos = 12,
        segundos = 0,
        medallas = 5,
        medallasTotales = 8,
        vistos = 92,
        atrapados = 78,
        equipo = equipoDeRojoFuego,
    )

    val juegoRojoFuego = JuegoRespaldado(
        id = 1,
        sistema = "gba",
        nombre = "Pokemon Edicion Rojo Fuego",
        rutaDePartida = "/roms/gba/Pokemon Edicion Rojo Fuego.srm",
        esPokemon = true,
        editable = true,
        ultimoRespaldo = FechaBase,
        resumen = resumenRojoFuego,
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )

    val juegoVerdeHoja = JuegoRespaldado(
        id = 2,
        sistema = "gba",
        nombre = "Pokemon Edicion Verde Hoja",
        rutaDePartida = "/roms/gba/Pokemon Edicion Verde Hoja.srm",
        esPokemon = true,
        editable = false,
        ultimoRespaldo = FechaBase - 3_600_000L,
        resumen = null,
        estadoDePartida = EstadoDePartida.VACIA,
    )

    val juegoHack = JuegoRespaldado(
        id = 3,
        sistema = "gba",
        nombre = "Pokemon Lets Go Eevee",
        rutaDePartida = "/roms/gba/Pokemon Lets Go Eevee.srm",
        esPokemon = true,
        editable = false,
        ultimoRespaldo = FechaBase - 7_200_000L,
        resumen = null,
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )

    val juegoOtro = JuegoRespaldado(
        id = 4,
        sistema = "gba",
        nombre = "007 - Agente Bajo Fuego",
        rutaDePartida = "/roms/gba/007 - Agente Bajo Fuego.srm",
        esPokemon = false,
        editable = false,
        ultimoRespaldo = FechaBase - 10_800_000L,
        resumen = null,
        estadoDePartida = EstadoDePartida.NO_APLICA,
    )

    /** Un juego de Pokémon que nunca se ha respaldado (sin resumen, sin fecha): para probar la lista sin ese dato. */
    val juegoSinRespaldar = JuegoRespaldado(
        id = 5,
        sistema = "gba",
        nombre = "Pokemon - Version Rouge Feu (F)",
        rutaDePartida = "/roms/gba/Pokemon - Version Rouge Feu (F).srm",
        esPokemon = true,
        editable = false,
        ultimoRespaldo = null,
        resumen = null,
        estadoDePartida = EstadoDePartida.NO_APLICA,
    )

    val juegos = listOf(juegoRojoFuego, juegoVerdeHoja, juegoHack, juegoOtro, juegoSinRespaldar)

    /**
     * El historial del Rojo Fuego, de la más reciente a la más vieja (ver el encargo, punto 4):
     * un respaldo normal, y luego el usuario restaura una versión más vieja (de ahí que el más
     * reciente tenga MENOS medallas y dinero que el que le sigue — "qué cambió" también se
     * muestra en negativo cuando se restaura hacia atrás).
     */
    val historialDeRojoFuego: List<VersionRespaldada> = listOf(
        version(
            id = 13,
            origen = OrigenDeVersion.ESCRITA_POR_LA_APP,
            respaldadaEn = FechaBase + 600_000L,
            resumen = resumenRojoFuego.copy(medallas = 4, atrapados = 70, dinero = 46_750),
        ),
        version(
            id = 12,
            origen = OrigenDeVersion.ANTES_DE_ESCRIBIR,
            respaldadaEn = FechaBase + 500_000L,
            resumen = resumenRojoFuego,
        ),
        version(
            id = 11,
            origen = OrigenDeVersion.CONSOLA,
            respaldadaEn = FechaBase,
            resumen = resumenRojoFuego,
        ),
        version(
            id = 10,
            origen = OrigenDeVersion.CONSOLA,
            respaldadaEn = FechaBase - 2 * 3_600_000L - 12 * 60_000L,
            resumen = resumenRojoFuego.copy(
                horas = 32,
                minutos = 0,
                medallas = 4,
                atrapados = 70,
                dinero = 46_750,
                equipo = listOf(
                    PokemonCorto("Kadabra", Kadabra, 50, mote = "Kadabra"),
                    PokemonCorto("Dragonair", Dragonair, 50),
                    PokemonCorto("Nidoking", Nidoking, 52),
                    PokemonCorto("Charizard", Charizard, 58, mote = "Draco"),
                    PokemonCorto("Machoke", Machoke, 45),
                    PokemonCorto("Horsea", Horsea, 40),
                ),
            ),
        ),
        version(
            id = 9,
            origen = OrigenDeVersion.CONSOLA,
            respaldadaEn = FechaBase - 14 * 3_600_000L,
            resumen = resumenRojoFuego.copy(
                horas = 20,
                minutos = 0,
                medallas = 3,
                atrapados = 55,
                dinero = 5_000,
                equipo = listOf(
                    PokemonCorto("Kadabra", Kadabra, 40, mote = "Kadabra"),
                    PokemonCorto("Dragonair", Dragonair, 40),
                    PokemonCorto("Nidoking", Nidoking, 45),
                    PokemonCorto("Charizard", Charizard, 50, mote = "Draco"),
                    PokemonCorto("Machoke", Machoke, 38),
                ),
            ),
        ),
    )

    /** Los estados de Rojo Fuego: aparte, sin "qué cambió" (ver el encargo, punto 4). */
    val estadosDeRojoFuego: List<VersionRespaldada> = listOf(
        VersionRespaldada(
            id = 20,
            juegoId = 1,
            tipo = TipoDeArchivo.ESTADO_AUTOMATICO,
            rutaEnConsola = "/roms/gba/Pokemon Edicion Rojo Fuego.state.auto",
            sha256 = "autohash",
            tamano = 206_264,
            modificadoEnConsola = (FechaBase / 1000) - 60,
            respaldadaEn = FechaBase,
            origen = OrigenDeVersion.CONSOLA,
            estadoDePartida = EstadoDePartida.NO_APLICA,
            resumen = null,
        ),
        VersionRespaldada(
            id = 19,
            juegoId = 1,
            tipo = TipoDeArchivo.ESTADO,
            rutaEnConsola = "/roms/gba/Pokemon Edicion Rojo Fuego.state1",
            sha256 = "estadohash",
            tamano = 206_264,
            modificadoEnConsola = (FechaBase / 1000) - 7_200,
            respaldadaEn = FechaBase - 7_200_000L,
            origen = OrigenDeVersion.CONSOLA,
            estadoDePartida = EstadoDePartida.NO_APLICA,
            resumen = null,
        ),
    )

    /** Verde Hoja: vacía, con un estado automático que sí tiene el avance (ver docs/plan.md). */
    val historialDeVerdeHoja: List<VersionRespaldada> = listOf(
        version(id = 30, origen = OrigenDeVersion.CONSOLA, respaldadaEn = FechaBase - 3_600_000L, resumen = null, estadoDePartida = EstadoDePartida.VACIA),
    )
    val estadosDeVerdeHoja: List<VersionRespaldada> = listOf(
        VersionRespaldada(
            id = 31,
            juegoId = 2,
            tipo = TipoDeArchivo.ESTADO_AUTOMATICO,
            rutaEnConsola = "/roms/gba/Pokemon Edicion Verde Hoja.state.auto",
            sha256 = "verdehojaauto",
            tamano = 206_264,
            modificadoEnConsola = (FechaBase / 1000) - 120,
            respaldadaEn = FechaBase - 3_600_000L,
            origen = OrigenDeVersion.CONSOLA,
            estadoDePartida = EstadoDePartida.NO_APLICA,
            resumen = null,
        ),
    )

    val historialDelHack: List<VersionRespaldada> = listOf(
        version(id = 40, origen = OrigenDeVersion.CONSOLA, respaldadaEn = FechaBase - 7_200_000L, resumen = null, estadoDePartida = EstadoDePartida.CON_DATOS),
    )

    private fun version(
        id: Long,
        origen: OrigenDeVersion,
        respaldadaEn: Long,
        resumen: ResumenGuardado?,
        estadoDePartida: EstadoDePartida = EstadoDePartida.CON_DATOS,
    ) = VersionRespaldada(
        id = id,
        juegoId = 1,
        tipo = TipoDeArchivo.PARTIDA,
        rutaEnConsola = "/roms/gba/Pokemon Edicion Rojo Fuego.srm",
        sha256 = "hash$id",
        tamano = 131_072,
        modificadoEnConsola = respaldadaEn / 1000,
        respaldadaEn = respaldadaEn,
        origen = origen,
        estadoDePartida = estadoDePartida,
        resumen = resumen,
    )

    /** 16 de octubre de 2026, 16:00 UTC: fecha fija para que las capturas no dependan del reloj real. */
    private const val FechaBase = 1_792_166_400_000L
}
