package com.baxner.r36scompanion.data.respaldos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiferenciasTest {

    private fun resumen(
        medallas: Int? = null,
        atrapados: Int? = null,
        dinero: Long? = null,
        horas: Int? = null,
        minutos: Int? = null,
        segundos: Int? = null,
        equipo: List<PokemonCorto> = emptyList(),
    ) = ResumenGuardado(
        version = "FireRed",
        entrenador = "Rojo",
        idDeEntrenador = "12345/00001",
        medallas = medallas,
        atrapados = atrapados,
        dinero = dinero,
        horas = horas,
        minutos = minutos,
        segundos = segundos,
        equipo = equipo,
    )

    private fun pokemon(especie: String, nivel: Int, mote: String? = null) =
        PokemonCorto(especie = especie, especieId = null, nivel = nivel, mote = mote)

    @Test
    fun `sin antes o sin despues no hay nada que comparar`() {
        val despues = resumen(medallas = 3)

        assertTrue(diferencias(null, despues).isEmpty())
        assertTrue(diferencias(despues, null).isEmpty())
        assertTrue(diferencias(null, null).isEmpty())
    }

    @Test
    fun `dos resumenes iguales no dan cambios`() {
        val r = resumen(medallas = 2, atrapados = 10, dinero = 500, horas = 3, minutos = 20, equipo = listOf(pokemon("Charmander", 12)))

        assertTrue(diferencias(r, r).isEmpty())
    }

    @Test
    fun `medallas nuevas`() {
        val antes = resumen(medallas = 2)
        val despues = resumen(medallas = 3)

        assertEquals(listOf(Cambio.Medallas(2, 3)), diferencias(antes, despues))
    }

    @Test
    fun `tiempo de juego avanzado, en minutos`() {
        val antes = resumen(horas = 3, minutos = 50, segundos = 0)
        val despues = resumen(horas = 4, minutos = 5, segundos = 30)

        // 4h05 - 3h50 = 15 min, más 30s que no llegan a otro minuto.
        assertEquals(listOf(Cambio.TiempoDeJuego(15)), diferencias(antes, despues))
    }

    @Test
    fun `tiempo de juego sin horas o minutos no se compara`() {
        val antes = resumen(horas = null, minutos = 50)
        val despues = resumen(horas = 4, minutos = 5)

        assertTrue(diferencias(antes, despues).isEmpty())
    }

    @Test
    fun `atrapados nuevos`() {
        val antes = resumen(atrapados = 10)
        val despues = resumen(atrapados = 15)

        assertEquals(listOf(Cambio.Atrapados(10, 15)), diferencias(antes, despues))
    }

    @Test
    fun `dinero distinto`() {
        val antes = resumen(dinero = 1000)
        val despues = resumen(dinero = 250)

        assertEquals(listOf(Cambio.Dinero(1000, 250)), diferencias(antes, despues))
    }

    @Test
    fun `un pokemon nuevo entra al equipo en una posicion vacia`() {
        val antes = resumen(equipo = listOf(pokemon("Charmander", 12)))
        val despues = resumen(equipo = listOf(pokemon("Charmander", 12), pokemon("Pidgey", 5)))

        assertEquals(listOf(Cambio.EntroAlEquipo(pokemon("Pidgey", 5))), diferencias(antes, despues))
    }

    @Test
    fun `un pokemon sale del equipo cuando esa posicion desaparece`() {
        val antes = resumen(equipo = listOf(pokemon("Charmander", 12), pokemon("Pidgey", 5)))
        val despues = resumen(equipo = listOf(pokemon("Charmander", 12)))

        assertEquals(listOf(Cambio.SalioDelEquipo(pokemon("Pidgey", 5))), diferencias(antes, despues))
    }

    @Test
    fun `sube de nivel, misma especie y mismo mote`() {
        val antes = resumen(equipo = listOf(pokemon("Charmander", 12, mote = "Brasa")))
        val despues = resumen(equipo = listOf(pokemon("Charmander", 14, mote = "Brasa")))

        assertEquals(listOf(Cambio.SubioDeNivel("Charmander", 12, 14)), diferencias(antes, despues))
    }

    @Test
    fun `evoluciona, mismo mote y cambia de especie`() {
        val antes = resumen(equipo = listOf(pokemon("Charmander", 16, mote = "Brasa")))
        val despues = resumen(equipo = listOf(pokemon("Charmeleon", 16, mote = "Brasa")))

        assertEquals(listOf(Cambio.Evoluciono("Charmander", "Charmeleon")), diferencias(antes, despues))
    }

    @Test
    fun `evoluciona sin mote porque el nivel no bajo`() {
        val antes = resumen(equipo = listOf(pokemon("Kadabra", 25)))
        val despues = resumen(equipo = listOf(pokemon("Alakazam", 25)))

        assertEquals(listOf(Cambio.Evoluciono("Kadabra", "Alakazam")), diferencias(antes, despues))
    }

    @Test
    fun `distinto mote y nivel mas bajo en la misma posicion es un cambio de equipo, no una evolucion`() {
        val antes = resumen(equipo = listOf(pokemon("Charmander", 30, mote = "Brasa")))
        val despues = resumen(equipo = listOf(pokemon("Geodude", 8, mote = "Roca")))

        assertEquals(
            listOf(Cambio.SalioDelEquipo(pokemon("Charmander", 30, mote = "Brasa")), Cambio.EntroAlEquipo(pokemon("Geodude", 8, mote = "Roca"))),
            diferencias(antes, despues),
        )
    }

    @Test
    fun `mismo nivel y especie no es un cambio`() {
        val antes = resumen(equipo = listOf(pokemon("Charmander", 12, mote = "Brasa")))
        val despues = resumen(equipo = listOf(pokemon("Charmander", 12, mote = "Brasa")))

        assertTrue(diferencias(antes, despues).isEmpty())
    }

    @Test
    fun `varios cambios juntos, en orden- medallas, tiempo, atrapados, equipo, dinero`() {
        val antes = resumen(medallas = 2, atrapados = 10, dinero = 1000, horas = 3, minutos = 0, equipo = listOf(pokemon("Charmander", 12)))
        val despues = resumen(medallas = 3, atrapados = 11, dinero = 900, horas = 3, minutos = 30, equipo = listOf(pokemon("Charmander", 14)))

        assertEquals(
            listOf(
                Cambio.Medallas(2, 3),
                Cambio.TiempoDeJuego(30),
                Cambio.Atrapados(10, 11),
                Cambio.SubioDeNivel("Charmander", 12, 14),
                Cambio.Dinero(1000, 900),
            ),
            diferencias(antes, despues),
        )
    }
}
