package com.baxner.r36scompanion.data.banco

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.gen3.PartidaGen3
import com.baxner.r36scompanion.data.gen3.PartidaSintetica
import com.baxner.r36scompanion.data.motor.MotorException
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.respaldos.RelojFalso
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BancoConMotor] con Room en memoria (Robolectric). Usa dos motores falsos distintos (ver sus
 * KDoc): [MotorDeCajasReales] para `prepararDeposito` (que llama a [PartidaGen3] directo sobre
 * los mismos bytes del motor) y [MotorDeCajaFalsa] para `prepararRetiro` (que no toca
 * [PartidaGen3] en absoluto, todo lo hace el motor).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BancoConMotorTest {
    private lateinit var db: BancoDatabase
    private lateinit var reloj: RelojFalso

    @Before
    fun preparar() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BancoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reloj = RelojFalso()
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun bancoDeDeposito(): BancoConMotor = BancoConMotor(db, MotorPartidas(MotorDeCajasReales()), reloj)

    private fun bancoDeRetiro(motor: MotorDeCajaFalsa = MotorDeCajaFalsa()): BancoConMotor = BancoConMotor(db, MotorPartidas(motor), reloj)

    /** Captura la excepción que lance [bloque], o null si no lanzó nada. */
    private suspend fun capturar(bloque: suspend () -> Unit): Throwable? = try {
        bloque()
        null
    } catch (e: Throwable) {
        e
    }

    /** Una partida sintética de Gen 3 con un Pikachu (especie 25, nivel 10) en la caja 0, casilla 0. */
    private fun partidaConUnPikachu(): ByteArray =
        PartidaSintetica(contadores = 1L to 0L, rotacion = 0).conPokemonDeCaja(ranura = 0, caja = 0, casilla = 0, especie = 25, nivel = 10).bytes()

    private val casillaDelPikachu = Casilla(origen = OrigenDeCasilla.CAJA, posicion = 0, caja = 0)

    // --- Depósito ---

    @Test
    fun `prepararDeposito saca el Pokemon, vacia la casilla y comprueba con el motor que solo falta ese`() = runTest {
        val banco = bancoDeDeposito()
        val partida = partidaConUnPikachu()

        val movimiento = banco.prepararDeposito(juegoId = 1, partida = partida, nombreDeArchivo = "rojo.srm", casilla = casillaDelPikachu)

        // La partida nueva ya no tiene al Pikachu en esa casilla, y conserva el tamaño.
        val partidaNueva = PartidaGen3.leer(movimiento.partidaNueva)!!
        assertTrue(partidaNueva.casillaVacia(0, 0))
        assertEquals(partida.size, movimiento.partidaNueva.size)

        // El pendiente quedó en el banco con los datos del Pokémon.
        val pokemon = banco.pendientes().single()
        assertEquals(EstadoEnBanco.PENDIENTE_DE_DEPOSITO, pokemon.estado)
        assertEquals(25, pokemon.especie)
        assertEquals(10, pokemon.nivel)
        assertEquals(1L, pokemon.juegoDeOrigen)
        assertEquals(casillaDelPikachu, pokemon.casillaDeOrigen)
        assertEquals(movimiento.pokemonId, pokemon.id)
        assertNull("al depositar no hay legalidad en destino: no está entrando a ningún juego", movimiento.legalidadEnDestino)
    }

    @Test
    fun `prepararDeposito falla si la casilla esta vacia`() = runTest {
        val banco = bancoDeDeposito()
        val casillaVacia = Casilla(origen = OrigenDeCasilla.CAJA, posicion = 1, caja = 0)

        val excepcion = capturar { banco.prepararDeposito(1, partidaConUnPikachu(), "rojo.srm", casillaVacia) }

        assertTrue(excepcion is IllegalStateException)
        assertTrue(banco.pendientes().isEmpty())
    }

    @Test
    fun `depositar y que falle la escritura - cancelar deja todo igual`() = runTest {
        val banco = bancoDeDeposito()
        val movimiento = banco.prepararDeposito(1, partidaConUnPikachu(), "rojo.srm", casillaDelPikachu)
        assertEquals(1, banco.pendientes().size)

        // La app intentó escribir `movimiento.partidaNueva` en la consola con EscritorDePartidas y
        // falló: se cancela.
        banco.cancelar(movimiento)

        assertTrue("el pendiente desaparece: el depósito nunca llegó a pasar", banco.pendientes().isEmpty())
        assertTrue("tampoco queda nada en el banco", banco.pokemon().first().isEmpty())
    }

    @Test
    fun `depositar y confirmar deja el Pokemon EN_BANCO`() = runTest {
        val banco = bancoDeDeposito()
        val movimiento = banco.prepararDeposito(1, partidaConUnPikachu(), "rojo.srm", casillaDelPikachu)

        banco.confirmar(movimiento)

        assertTrue(banco.pendientes().isEmpty())
        val fila = db.bancoDao().porId(movimiento.pokemonId)!!
        assertEquals(EstadoEnBanco.EN_BANCO, fila.estado)
    }

    @Test
    fun `confirmar o cancelar sin nada pendiente falla`() = runTest {
        val banco = bancoDeDeposito()
        val movimiento = banco.prepararDeposito(1, partidaConUnPikachu(), "rojo.srm", casillaDelPikachu)
        banco.confirmar(movimiento)

        assertTrue(capturar { banco.confirmar(movimiento) } is IllegalStateException)
        assertTrue(capturar { banco.cancelar(movimiento) } is IllegalStateException)
    }

    @Test
    fun `conciliar un deposito decide bien en los dos sentidos`() = runTest {
        val banco = bancoDeDeposito()
        val partidaOriginal = partidaConUnPikachu()

        // La app se cerró antes de escribir: la consola sigue con la partida de antes (el
        // Pokémon todavía en su casilla) → se cancela.
        val movimiento1 = banco.prepararDeposito(1, partidaOriginal, "rojo.srm", casillaDelPikachu)
        banco.conciliar(movimiento1.pokemonId, partidaOriginal, "rojo.srm")
        assertTrue("se cancela: el Pokémon nunca salió de la consola", banco.pendientes().isEmpty())
        assertTrue(banco.pokemon().first().none { it.id == movimiento1.pokemonId })

        // Otro depósito: esta vez la consola ya tiene la partida escrita (sin el Pokémon) → se confirma.
        val movimiento2 = banco.prepararDeposito(1, partidaOriginal, "rojo.srm", casillaDelPikachu)
        banco.conciliar(movimiento2.pokemonId, movimiento2.partidaNueva, "rojo.srm")
        assertTrue("se confirma: el Pokémon ya no está en la consola", banco.pendientes().isEmpty())
        assertEquals(EstadoEnBanco.EN_BANCO, db.bancoDao().porId(movimiento2.pokemonId)!!.estado)
    }

    // --- Retiro ---

    @Test
    fun `prepararRetiro mete el Pokemon en la primera casilla libre`() = runTest {
        val banco = bancoDeRetiro()
        val pokemonId = sembrarPokemonEnBanco()
        val partidaDestino = EstadoDeCajaDePrueba(cajas = listOf(CajaDePruebaDeBanco(0, 2)), ocupadas = listOf(CasillaDePrueba(0, 0, 999, 50))).aBytes()

        val movimiento = banco.prepararRetiro(pokemonId, juegoId = 2, partida = partidaDestino, nombreDeArchivo = "verde.srm")

        assertEquals(Casilla(origen = OrigenDeCasilla.CAJA, posicion = 1, caja = 0), movimiento.casilla)
        assertNotNull(movimiento.legalidadEnDestino)
        assertTrue(movimiento.legalidadEnDestino!!.valida)
        assertTrue(movimiento.cambios.isNotEmpty())

        val fila = db.bancoDao().porId(pokemonId)!!
        assertEquals(EstadoEnBanco.PENDIENTE_DE_RETIRO, fila.estado)
        assertEquals(2L, fila.juegoDeOrigen)
    }

    @Test
    fun `prepararRetiro falla sin espacio y no deja nada pendiente`() = runTest {
        val banco = bancoDeRetiro()
        val pokemonId = sembrarPokemonEnBanco()
        val partidaLlena = EstadoDeCajaDePrueba(cajas = listOf(CajaDePruebaDeBanco(0, 1)), ocupadas = listOf(CasillaDePrueba(0, 0, 999, 50))).aBytes()

        val excepcion = capturar { banco.prepararRetiro(pokemonId, 2, partidaLlena, "verde.srm") }

        assertTrue(excepcion is MotorException)
        assertEquals("box-full", (excepcion as MotorException).codigo)
        val fila = db.bancoDao().porId(pokemonId)!!
        assertEquals("sigue EN_BANCO: la falta de espacio no deja nada a medias", EstadoEnBanco.EN_BANCO, fila.estado)
    }

    @Test
    fun `prepararRetiro falla si ningun juego puede tener la especie`() = runTest {
        val banco = bancoDeRetiro(MotorDeCajaFalsa().apply { especiesRechazadas = setOf(25) })
        val pokemonId = sembrarPokemonEnBanco()

        val excepcion = capturar { banco.prepararRetiro(pokemonId, 2, EstadoDeCajaDePrueba().aBytes(), "verde.srm") }

        assertTrue(excepcion is MotorException)
        assertEquals("conversion-failed", (excepcion as MotorException).codigo)
        assertEquals(EstadoEnBanco.EN_BANCO, db.bancoDao().porId(pokemonId)!!.estado)
    }

    @Test
    fun `prepararRetiro no se puede repetir mientras haya un pendiente`() = runTest {
        val banco = bancoDeRetiro()
        val pokemonId = sembrarPokemonEnBanco()
        banco.prepararRetiro(pokemonId, 2, EstadoDeCajaDePrueba().aBytes(), "verde.srm")

        val excepcion = capturar { banco.prepararRetiro(pokemonId, 2, EstadoDeCajaDePrueba().aBytes(), "verde.srm") }

        assertTrue(excepcion is IllegalStateException)
    }

    @Test
    fun `conciliar un retiro decide bien en los dos sentidos`() = runTest {
        val banco = bancoDeRetiro()
        val pokemonId = sembrarPokemonEnBanco()

        // La app se cerró antes de escribir: la consola de destino no tiene al Pokémon → se cancela.
        banco.prepararRetiro(pokemonId, 2, EstadoDeCajaDePrueba().aBytes(), "verde.srm")
        val consolaSinElPokemon = PartidaSintetica(1L to 0L, 0).bytes()
        banco.conciliar(pokemonId, consolaSinElPokemon, "verde.srm")
        assertEquals(EstadoEnBanco.EN_BANCO, db.bancoDao().porId(pokemonId)!!.estado)

        // Segundo retiro: ahora la consola de destino sí tiene al Pokémon en esa casilla → se confirma y sale del banco.
        val movimiento2 = banco.prepararRetiro(pokemonId, 2, EstadoDeCajaDePrueba().aBytes(), "verde.srm")
        val consolaConElPokemon = PartidaSintetica(1L to 0L, 0)
            .conPokemonDeCaja(0, movimiento2.casilla.caja!!, movimiento2.casilla.posicion, 25, 10)
            .bytes()
        banco.conciliar(pokemonId, consolaConElPokemon, "verde.srm")
        assertNull("se confirma: el movimiento queda firme y sale del banco", db.bancoDao().porId(pokemonId))
    }

    @Test
    fun `mover cambia la casilla del banco`() = runTest {
        val banco = bancoDeDeposito()
        val movimiento = banco.prepararDeposito(1, partidaConUnPikachu(), "rojo.srm", casillaDelPikachu)
        banco.confirmar(movimiento)

        banco.mover(movimiento.pokemonId, caja = 2, posicion = 5)

        val fila = db.bancoDao().porId(movimiento.pokemonId)!!
        assertEquals(2, fila.caja)
        assertEquals(5, fila.posicion)
    }

    @Test
    fun `mover no deja chocar con una casilla del banco ya ocupada`() = runTest {
        val banco = bancoDeDeposito()
        val m1 = banco.prepararDeposito(1, partidaConUnPikachu(), "rojo.srm", casillaDelPikachu)
        banco.confirmar(m1)
        val otraPartida = PartidaSintetica(1L to 0L, 0).conPokemonDeCaja(0, 0, 0, 1, 5).bytes()
        val m2 = banco.prepararDeposito(1, otraPartida, "rojo.srm", casillaDelPikachu)
        banco.confirmar(m2)

        val excepcion = capturar { banco.mover(m2.pokemonId, caja = db.bancoDao().porId(m1.pokemonId)!!.caja, posicion = db.bancoDao().porId(m1.pokemonId)!!.posicion) }

        assertTrue(excepcion is IllegalStateException)
    }

    private suspend fun sembrarPokemonEnBanco(especie: Int = 25, nivel: Int = 10): Long = db.bancoDao().insertar(
        PokemonEnBancoEntity(
            bytes = PokemonFileDePrueba(especie = especie, nivel = nivel).aBytes(),
            especie = especie,
            nivel = nivel,
            mote = "PIKACHU",
            esShiny = false,
            esHuevo = false,
            entrenadorOriginal = "ROJO",
            idDeEntrenador = 12345,
            versionDeOrigen = 10,
            legalidadJson = null,
            estado = EstadoEnBanco.EN_BANCO,
            juegoDeOrigen = 1,
            casillaDeOrigenJson = Casilla(OrigenDeCasilla.CAJA, 0, 0).aJsonDeBanco(),
            depositadoEn = reloj.ahoraMs(),
            caja = 0,
            posicion = 0,
        ),
    )
}
