package com.baxner.r36scompanion.data.pokedex

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.gen3.PartidaSintetica
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.LectorDeResumenFalso
import com.baxner.r36scompanion.data.respaldos.RelojFalso
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldosImpl
import com.baxner.r36scompanion.data.respaldos.ResumenGuardado
import com.baxner.r36scompanion.data.respaldos.RespaldosDatabase
import com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [PokedexGlobalConRespaldos] con [RepositorioDeRespaldosImpl] de verdad (Room en memoria,
 * Robolectric) y [MotorDePokedexDePrueba]. Especies elegidas a propósito para que no se puedan
 * confundir: 1 (Bulbasaur) registrada pero ya no tenida, 25 (Pikachu) registrada y en una caja, 7
 * (Squirtle) sin registrar y solo en el banco.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PokedexGlobalConRespaldosTest {
    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    private lateinit var db: RespaldosDatabase
    private lateinit var repositorio: RepositorioDeRespaldosImpl
    private lateinit var sesion: SesionDeConsolaFalsa
    private lateinit var lector: LectorDeResumenFalso

    @Before
    fun preparar() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RespaldosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val almacen = AlmacenDeBlobs(carpetaTemporal.newFolder("blobs"))
        lector = LectorDeResumenFalso()
        repositorio = RepositorioDeRespaldosImpl(db, almacen, lector, RelojFalso())
        sesion = SesionDeConsolaFalsa()
    }

    @After
    fun cerrar() {
        db.close()
    }

    /** Siembra un juego "editable" con [bytes] como su partida, y devuelve su id. */
    private suspend fun sembrarJuego(nombre: String, bytes: ByteArray): Long {
        sesion.ponerArchivo("/roms/gba/$nombre.srm", bytes, modificado = 1000)
        lector.definir("$nombre.srm", ResumenGuardado(version = nombre, entrenador = "Rojo", idDeEntrenador = "1/0"))
        repositorio.respaldarTodo(sesion)
        return repositorio.juegos().first().single { it.nombre == nombre }.id
    }

    @Test
    fun `una especie registrada pero ya no tenida, y otra solo en el banco`() = runTest {
        // Bulbasaur (1) registrado (atrapado y visto) pero sin ningún Pokémon de esa especie en
        // la partida; Pikachu (25) registrado Y en la caja 0; Squirtle (7) no está en la
        // Pokédex del juego, solo en el banco.
        val bytes = PartidaSintetica(contadores = 1L to 0L, rotacion = 0)
            .conPokedex(ranura = 0, atrapados = setOf(1, 25), vistos = setOf(1, 25))
            .conPokemonDeCaja(ranura = 0, caja = 0, casilla = 0, especie = 25, nivel = 12)
            .bytes()
        val juegoId = sembrarJuego("Pokemon Edicion Rojo Fuego", bytes)

        val banco = BancoFalso(
            listOf(pokemonDeBanco(id = 1, especie = 7, estado = EstadoEnBanco.EN_BANCO)),
        )
        val pokedex = PokedexGlobalConRespaldos(repositorio, banco, MotorPartidas(MotorDePokedexDePrueba()))

        val entradas = pokedex.entradas().associateBy { it.especie }

        val bulbasaur = entradas.getValue(1)
        assertTrue("Bulbasaur está registrado", bulbasaur.registrada)
        assertTrue(juegoId in bulbasaur.atrapadaEn)
        assertFalse("pero ya no lo tiene: no hay ningún Bulbasaur en la partida ni en el banco", bulbasaur.laTiene)

        val pikachu = entradas.getValue(25)
        assertTrue(pikachu.registrada)
        assertTrue("lo tiene en la caja 0, casilla 0 de la partida", pikachu.laTiene)
        val ubicacion = pikachu.ubicaciones.single() as Ubicacion.EnPartida
        assertEquals(juegoId, ubicacion.juegoId)
        assertEquals(Casilla(OrigenDeCasilla.CAJA, 0, 0), ubicacion.casilla)
        assertEquals(12, ubicacion.nivel)

        val squirtle = entradas.getValue(7)
        assertFalse("Squirtle no está en la Pokédex de ningún juego", squirtle.registrada)
        assertTrue("pero sí lo tiene: está en el banco", squirtle.laTiene)
        assertTrue(squirtle.ubicaciones.single() is Ubicacion.EnBanco)
    }

    @Test
    fun `un pendiente del banco no se cuenta como ubicacion (es transitorio)`() = runTest {
        val bytes = PartidaSintetica(1L to 0L, 0).bytes()
        sembrarJuego("Pokemon Edicion Esmeralda", bytes)
        val banco = BancoFalso(listOf(pokemonDeBanco(id = 2, especie = 50, estado = EstadoEnBanco.PENDIENTE_DE_RETIRO)))
        val pokedex = PokedexGlobalConRespaldos(repositorio, banco, MotorPartidas(MotorDePokedexDePrueba()))

        val diglett = pokedex.entradas().single { it.especie == 50 }

        assertFalse(diglett.laTiene)
    }

    @Test
    fun `resumen cuenta registradas, tenidas y vistas`() = runTest {
        val bytes = PartidaSintetica(1L to 0L, 0)
            .conPokedex(ranura = 0, atrapados = setOf(1), vistos = setOf(1, 4))
            .bytes()
        sembrarJuego("Pokemon Edicion Rojo Fuego", bytes)
        val pokedex = PokedexGlobalConRespaldos(repositorio, BancoFalso(emptyList()), MotorPartidas(MotorDePokedexDePrueba()))

        val resumen = pokedex.resumen()

        assertEquals(386, resumen.total)
        assertEquals(1, resumen.registradas) // solo la 1, atrapada
        assertEquals(0, resumen.tenidas) // registrada pero no está en ninguna caja ni en el banco
        assertEquals(2, resumen.vistas) // la 1 y la 4
    }

    @Test
    fun `dondeAtrapar junta lo que diga encounters-search de cada juego editable`() = runTest {
        val bytes = PartidaSintetica(1L to 0L, 0).bytes()
        val juegoId = sembrarJuego("Pokemon Edicion Rojo Fuego", bytes)
        val motor = MotorDePokedexDePrueba(versionId = 10).apply {
            encuentros = mapOf(10 to 25 to listOf(EncounterRowDto(speciesId = 25, location = "Viridian Forest", levelRange = "3-5", isEgg = false)))
        }
        val pokedex = PokedexGlobalConRespaldos(repositorio, BancoFalso(emptyList()), MotorPartidas(motor))

        val lugares = pokedex.dondeAtrapar(25)

        val lugar = lugares.single()
        assertEquals(juegoId, lugar.juegoId)
        assertEquals(10, lugar.versionId)
        assertEquals("Viridian Forest", lugar.lugar)
        assertEquals("3-5", lugar.niveles)
        assertFalse(lugar.esHuevo)
    }

    @Test
    fun `dondeAtrapar da vacio si no sale por ningun juego`() = runTest {
        val bytes = PartidaSintetica(1L to 0L, 0).bytes()
        sembrarJuego("Pokemon Edicion Rojo Fuego", bytes)
        val pokedex = PokedexGlobalConRespaldos(repositorio, BancoFalso(emptyList()), MotorPartidas(MotorDePokedexDePrueba()))

        assertTrue(pokedex.dondeAtrapar(150).isEmpty()) // Mewtwo: solo por evento, no por encuentro normal
    }

    private fun pokemonDeBanco(id: Long, especie: Int, estado: EstadoEnBanco): PokemonEnBanco = PokemonEnBanco(
        id = id,
        bytes = ByteArray(0),
        especie = especie,
        nivel = 15,
        mote = "",
        esShiny = false,
        esHuevo = false,
        entrenadorOriginal = "ROJO",
        idDeEntrenador = 12345,
        versionDeOrigen = 10,
        legalidad = null,
        estado = estado,
        juegoDeOrigen = null,
        casillaDeOrigen = null,
        depositadoEn = 0,
        caja = 0,
        posicion = 0,
    )
}
