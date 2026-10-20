package com.baxner.r36scompanion.data.editor

import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [EditorDePartidasConMotor] con [MotorFalso] en vez del motor de verdad (que solo corre en el
 * WebView: ver AGENTS.md). Cubre lo que de verdad tiene lógica propia: el ciclo
 * carga-opera-cierra de cada operación, `probar` contra `aplicar`, deshacer, y la traducción de
 * ida y vuelta para entrenador/medallas/mochila/Pokémon (ya ejercitada también, con JSON literal,
 * en `TraduccionesDelMotorTest`).
 */
class EditorDePartidasConMotorTest {
    private lateinit var motorFalso: MotorFalso
    private lateinit var editor: EditorDePartidasConMotor
    private lateinit var bytesIniciales: ByteArray

    private val casillaBulbasaur = Casilla(OrigenDeCasilla.EQUIPO, 0, null)
    private val casillaKadabra = Casilla(OrigenDeCasilla.EQUIPO, 1, null)
    private val casillaCaterpie = Casilla(OrigenDeCasilla.CAJA, 0, 0)
    private val casillaIlegal = Casilla(OrigenDeCasilla.CAJA, 1, 0)

    @Before
    fun preparar() {
        motorFalso = MotorFalso()
        bytesIniciales = motorFalso.bytesDeAhora()
        editor = EditorDePartidasConMotor(MotorPartidas(motorFalso))
    }

    private suspend fun abrir(): SesionDeEdicion = editor.abrir(bytesIniciales, "Pokemon Edicion Rojo Fuego.srm")

    @Test
    fun `abrir valida que el motor reconozca la partida`() = runTest {
        val sesion = abrir()

        assertEquals(1, motorFalso.cargas)
        assertEquals(1, motorFalso.cierres)
        assertFalse(sesion.hayCambios)
        assertArrayEquals(bytesIniciales, sesion.bytesActuales)
    }

    @Test
    fun `abrir con bytes que el motor no reconoce sube MotorException`() = runTest {
        val error = try {
            editor.abrir("esto no es una partida".toByteArray(), "rara.srm")
            null
        } catch (e: MotorException) {
            e
        }

        assertNotNull(error)
        assertEquals("invalid-save", error!!.codigo)
    }

    @Test
    fun `base es el sha-256 de los bytes iniciales, como lo guardan los respaldos`() = runTest {
        val sesion = abrir()
        assertEquals(AlmacenDeBlobs.sha256Hex(bytesIniciales), sesion.base)
    }

    @Test
    fun `lee el equipo`() = runTest {
        val equipo = abrir().equipo()

        assertEquals(2, equipo.size)
        assertEquals(casillaBulbasaur, equipo[0].casilla)
        assertEquals("BULBASAUR", equipo[0].mote)
        assertEquals(casillaKadabra, equipo[1].casilla)
        assertEquals(28, equipo[1].nivel)
    }

    @Test
    fun `lee las cajas, con la caja vacia incluida`() = runTest {
        val cajas = abrir().cajas()

        assertEquals(2, cajas.size)
        assertEquals("CAJA 1", cajas[0].nombre)
        assertEquals(30, cajas[0].casillas)
        assertEquals(2, cajas[0].pokemon.size)
        assertEquals(casillaCaterpie, cajas[0].pokemon[0].casilla)
        assertEquals("CAJA 2", cajas[1].nombre)
        assertTrue("la caja 2 no tiene Pokémon en esta partida de prueba", cajas[1].pokemon.isEmpty())
    }

    @Test
    fun `cada operacion carga y cierra por su cuenta, sin dejar nada a medias`() = runTest {
        val sesion = abrir() // abrir() ya hizo una carga de validación.
        sesion.equipo()
        sesion.cajas()

        assertEquals(3, motorFalso.cargas)
        assertEquals(3, motorFalso.cierres)
        assertEquals(0, motorFalso.exportaciones)
    }

    @Test
    fun `probar no cambia los bytes ni se queda pegado, aplicar si`() = runTest {
        val sesion = abrir()
        val bytesAntes = sesion.bytesActuales
        val cambio = CambioDePokemon(mote = "ALAKAZAM", nivel = 36)

        val probado = sesion.probar(casillaKadabra, cambio)
        assertEquals("ALAKAZAM", probado.mote)
        assertEquals(36, probado.nivel)
        assertArrayEquals(bytesAntes, sesion.bytesActuales)
        assertFalse(sesion.hayCambios)
        assertEquals(0, motorFalso.exportaciones)
        // No se quedó: una lectura aparte, fresca, todavía ve al Kadabra original.
        assertEquals("Kadabra", sesion.detalle(casillaKadabra).mote)

        val aplicado = sesion.aplicar(casillaKadabra, cambio)
        assertEquals("ALAKAZAM", aplicado.mote)
        assertFalse("aplicar sí tiene que cambiar los bytes", bytesAntes.contentEquals(sesion.bytesActuales))
        assertTrue(sesion.hayCambios)
        assertEquals(1, motorFalso.exportaciones)
        // Y ahora sí se quedó.
        assertEquals("ALAKAZAM", sesion.detalle(casillaKadabra).mote)
        assertEquals(36, sesion.detalle(casillaKadabra).nivel)
    }

    @Test
    fun `deshacerTodo vuelve a los bytes iniciales sin hablarle al motor`() = runTest {
        val sesion = abrir()
        sesion.aplicar(casillaKadabra, CambioDePokemon(mote = "ALAKAZAM"))
        assertTrue(sesion.hayCambios)
        val cargasAntesDeDeshacer = motorFalso.cargas

        sesion.deshacerTodo()

        assertFalse(sesion.hayCambios)
        assertArrayEquals(bytesIniciales, sesion.bytesActuales)
        assertEquals("deshacer es solo en memoria, no carga nada en el motor", cargasAntesDeDeshacer, motorFalso.cargas)
        assertEquals("Kadabra", sesion.detalle(casillaKadabra).mote)
    }

    @Test
    fun `un pokemon_update rechazado por el motor sube como MotorException y no deja cambios`() = runTest {
        val sesion = abrir()
        motorFalso.fallarLaProximaVezEn("pokemon.update", MotorException("invalid-patch", "nivel fuera de rango"))

        val error = try {
            sesion.aplicar(casillaKadabra, CambioDePokemon(nivel = 999))
            null
        } catch (e: MotorException) {
            e
        }

        assertNotNull(error)
        assertEquals("invalid-patch", error!!.codigo)
        assertFalse(sesion.hayCambios)
    }

    @Test
    fun `cambia el dinero del entrenador`() = runTest {
        val sesion = abrir()
        assertEquals(3000L, sesion.entrenador().dinero)

        sesion.cambiarDinero(5000)

        assertEquals(5000L, sesion.entrenador().dinero)
        assertTrue(sesion.hayCambios)
    }

    @Test
    fun `cambia el nombre del entrenador`() = runTest {
        val sesion = abrir()

        sesion.cambiarNombre("ROJO")

        assertEquals("ROJO", sesion.entrenador().nombre)
        assertTrue(sesion.hayCambios)
    }

    @Test
    fun `lee y cambia medallas`() = runTest {
        val sesion = abrir()
        val medallas = sesion.medallas()
        assertEquals(8, medallas.size)
        assertTrue(medallas.none { it.ganada })

        sesion.cambiarMedallas(List(8) { it < 3 })

        val actualizadas = sesion.medallas()
        assertEquals(listOf(true, true, true, false, false, false, false, false), actualizadas.map { it.ganada })
        assertTrue(sesion.hayCambios)
    }

    @Test
    fun `un motor sin medallas -hack o fuera de la 1a a 3a generacion- da lista vacia`() = runTest {
        motorFalso.sinMedallas = true

        assertEquals(emptyList<Medalla>(), abrir().medallas())
    }

    @Test
    fun `lee y cambia la mochila`() = runTest {
        val sesion = abrir()
        val items = sesion.mochila().single { it.nombre == "Items" }
        assertEquals(listOf(ObjetoEnMochila(id = 13, cantidad = 3, maximo = 99)), items.objetos)

        sesion.cambiarObjeto("Items", id = 13, cantidad = 10)
        assertEquals(10, sesion.mochila().single { it.nombre == "Items" }.objetos.single { it.id == 13 }.cantidad)

        sesion.cambiarObjeto("Items", id = 13, cantidad = 0)
        assertTrue(sesion.mochila().single { it.nombre == "Items" }.objetos.none { it.id == 13 })
    }

    @Test
    fun `revisarAntesDeGuardar encuentra el pokemon ilegal de la caja`() = runTest {
        val revision = abrir().revisarAntesDeGuardar()

        assertTrue(revision.abre)
        assertEquals(listOf(casillaIlegal), revision.pokemonIlegales.map { it.first })
        assertFalse(revision.pokemonIlegales.single().second.valida)
        assertEquals(listOf("Nivel fuera de rango para su especie y origen."), revision.pokemonIlegales.single().second.mensajes)
    }

    @Test
    fun `revisarAntesDeGuardar cuando la partida ya no abre`() = runTest {
        val sesion = abrir()
        motorFalso.fallarLaProximaVezEn("game.load", MotorException("invalid-save", "partida dañada"))

        val revision = sesion.revisarAntesDeGuardar()

        assertFalse(revision.abre)
        assertTrue(revision.pokemonIlegales.isEmpty())
    }
}
