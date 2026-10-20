package com.baxner.r36scompanion.ui.juego

import com.baxner.r36scompanion.data.respaldos.PokemonCorto
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Contra las tablas reales de texto (ver TextosTest): así se revisan los nombres en español de verdad, no inventados. */
class FrasesDeCambioTest {
    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun resumen(
        medallas: Int = 5,
        horas: Int = 10,
        minutos: Int = 0,
        atrapados: Int = 75,
        dinero: Long = 1_000,
        equipo: List<PokemonCorto> = emptyList(),
    ) = ResumenGuardado(
        version = "FireRed",
        entrenador = "Rojo",
        idDeEntrenador = "12345/0",
        dinero = dinero,
        horas = horas,
        minutos = minutos,
        segundos = 0,
        medallas = medallas,
        medallasTotales = 8,
        vistos = 80,
        atrapados = atrapados,
        equipo = equipo,
    )

    @Test
    fun masUnaMedalla() {
        val antes = resumen(medallas = 4)
        val despues = resumen(medallas = 5)
        assertEquals(listOf("+1 medalla"), frasesDeCambios(antes, despues, textos))
    }

    @Test
    fun masDeUnaMedallaUsaPlural() {
        val antes = resumen(medallas = 2)
        val despues = resumen(medallas = 4)
        assertEquals(listOf("+2 medallas"), frasesDeCambios(antes, despues, textos))
    }

    @Test
    fun tiempoDeJuego() {
        val antes = resumen(horas = 10, minutos = 0)
        val despues = resumen(horas = 12, minutos = 10)
        assertEquals(listOf("+2 h 10 min de juego"), frasesDeCambios(antes, despues, textos))
    }

    @Test
    fun atrapados() {
        val antes = resumen(atrapados = 75)
        val despues = resumen(atrapados = 78)
        assertEquals(listOf("+3 atrapados"), frasesDeCambios(antes, despues, textos))
    }

    @Test
    fun dineroGanadoYPerdido() {
        assertEquals(listOf("+$500"), frasesDeCambios(resumen(dinero = 1_000), resumen(dinero = 1_500), textos))
        assertEquals(listOf("-$200"), frasesDeCambios(resumen(dinero = 1_000), resumen(dinero = 800), textos))
    }

    @Test
    fun subioDeNivelTraduceLaEspecieDesdeElEquipoDeDespues() {
        val antes = resumen(equipo = listOf(PokemonCorto(especie = "Kadabra", especieId = 64, nivel = 53)))
        val despues = resumen(equipo = listOf(PokemonCorto(especie = "Kadabra", especieId = 64, nivel = 54)))
        assertEquals(listOf("Kadabra subió al Nv 54"), frasesDeCambios(antes, despues, textos))
    }

    @Test
    fun evolucionoTraduceAmbasEspecies() {
        val antes = resumen(equipo = listOf(PokemonCorto(especie = "Abra", especieId = 63, nivel = 16)))
        val despues = resumen(equipo = listOf(PokemonCorto(especie = "Kadabra", especieId = 64, nivel = 16)))
        assertEquals(listOf("Abra evolucionó a Kadabra"), frasesDeCambios(antes, despues, textos))
    }

    @Test
    fun entroAlEquipoYSalioDelEquipo() {
        // Un nivel menor y un mote distinto: así `diferencias()` no lo toma como que el mismo
        // Pokémon subió de nivel, sino como que uno salió y otro (recién atrapado) entró (ver
        // `data/respaldos/Diferencias.kt`, `diferenciaDePosicion`).
        val kadabra = PokemonCorto(especie = "Kadabra", especieId = 64, nivel = 54)
        val antes = resumen(equipo = listOf(kadabra))
        val despues = resumen(equipo = listOf(PokemonCorto(especie = "Alakazam", especieId = 65, nivel = 10, mote = "Distinto")))
        val frases = frasesDeCambios(antes, despues, textos)
        assertEquals(listOf("Salió Kadabra del equipo", "Entró Alakazam al equipo"), frases)
    }

    @Test
    fun sinResumenPrevioNoHayFrases() {
        assertEquals(emptyList<String>(), frasesDeCambios(null, resumen(), textos))
    }
}
