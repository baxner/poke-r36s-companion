package com.baxner.r36scompanion.ui.juego

import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NombreDeJuegoTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun juego(nombre: String, resumen: ResumenGuardado? = null) = JuegoRespaldado(
        id = 1,
        sistema = "gba",
        nombre = nombre,
        rutaDePartida = "/roms/gba/$nombre.srm",
        esPokemon = true,
        editable = resumen != null,
        ultimoRespaldo = 1L,
        resumen = resumen,
        estadoDePartida = if (resumen != null) EstadoDePartida.CON_DATOS else EstadoDePartida.VACIA,
    )

    @Test
    fun `con resumen usa el nombre que da el motor`() {
        val resumen = ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/1")
        assertEquals("Rojo Fuego", nombreDeJuego(juego("Pokemon Edicion Rojo Fuego", resumen), textos))
    }

    @Test
    fun `sin resumen lo deduce del nombre de archivo`() {
        assertEquals("Rojo Fuego (francés)", nombreDeJuego(juego("Pokemon - Version Rouge Feu (F)"), textos))
        assertEquals("Verde Hoja", nombreDeJuego(juego("Pokemon Edicion Verde Hoja"), textos))
    }

    @Test
    fun `sin resumen y sin poder deducirlo, el nombre del archivo tal cual`() {
        assertEquals("Pokemon Lets Go Eevee", nombreDeJuego(juego("Pokemon Lets Go Eevee"), textos))
    }
}
