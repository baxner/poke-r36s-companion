package com.baxner.r36scompanion.ui.pokedex

import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.pokedex.DondeAtrapar
import com.baxner.r36scompanion.data.pokedex.Ubicacion
import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class TextosDePokedexTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private val rojoFuego = JuegoRespaldado(
        id = 1,
        sistema = "gba",
        nombre = "Pokemon Edicion Rojo Fuego",
        rutaDePartida = "/roms/gba/Pokemon Edicion Rojo Fuego.srm",
        esPokemon = true,
        editable = true,
        ultimoRespaldo = 0,
        resumen = ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/0"),
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )

    private val sinRespaldar = JuegoRespaldado(
        id = 2,
        sistema = "gba",
        nombre = "Pokemon Edicion Esmeralda",
        rutaDePartida = "/roms/gba/Pokemon Edicion Esmeralda.srm",
        esPokemon = true,
        editable = false,
        ultimoRespaldo = null,
        resumen = null,
        estadoDePartida = EstadoDePartida.NO_APLICA,
    )

    @Test
    fun `Casilla aTexto distingue equipo de caja, en 1-based`() {
        assertEquals("en el equipo, posición 1", Casilla(OrigenDeCasilla.EQUIPO, posicion = 0).aTexto())
        assertEquals("caja 4, casilla 13", Casilla(OrigenDeCasilla.CAJA, posicion = 12, caja = 3).aTexto())
    }

    @Test
    fun `nombreParaMostrar usa el resumen si ya se leyo, si no el nombre de la ROM`() {
        assertEquals("Rojo Fuego", rojoFuego.nombreParaMostrar(textos))
        assertEquals("Pokemon Edicion Esmeralda", sinRespaldar.nombreParaMostrar(textos))
    }

    @Test
    fun `Ubicacion EnPartida da el nombre del juego y la casilla con nivel`() {
        val ubicacion = Ubicacion.EnPartida(juegoId = 1, casilla = Casilla(OrigenDeCasilla.CAJA, 0, 0), nivel = 54, esShiny = false)

        val texto = ubicacion.aTexto(mapOf(1L to rojoFuego), textos)

        assertEquals("Rojo Fuego", texto.lugar)
        assertEquals("caja 1, casilla 1 — Nv 54", texto.detalle)
    }

    @Test
    fun `Ubicacion EnBanco marca el shiny`() {
        val ubicacion = Ubicacion.EnBanco(pokemonId = 9, nivel = 3, esShiny = true)

        val texto = ubicacion.aTexto(emptyMap(), textos)

        assertEquals("En el banco", texto.lugar)
        assertEquals("Nv 3, shiny", texto.detalle)
    }

    @Test
    fun `DondeAtrapar normal muestra lugar y niveles`() {
        val lugar = DondeAtrapar(juegoId = 1, versionId = 4, lugar = "Viridian Forest", niveles = "3-5", esHuevo = false)

        assertEquals("Rojo Fuego: Viridian Forest (niveles 3-5)", lugar.aTexto(mapOf(1L to rojoFuego), textos))
    }

    @Test
    fun `DondeAtrapar por huevo lo dice`() {
        val lugar = DondeAtrapar(juegoId = 1, versionId = 4, lugar = "Day Care", niveles = "—", esHuevo = true)

        assertEquals("Rojo Fuego: por huevo (Day Care)", lugar.aTexto(mapOf(1L to rojoFuego), textos))
    }
}
