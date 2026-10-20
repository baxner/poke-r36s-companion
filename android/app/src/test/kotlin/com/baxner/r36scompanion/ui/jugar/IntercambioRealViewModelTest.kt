package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.jugar.ErrorDeJuego
import com.baxner.r36scompanion.data.jugar.ErrorDeRom
import com.baxner.r36scompanion.data.jugar.EstadoDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.IntercambioReal
import com.baxner.r36scompanion.data.respaldos.Cambio
import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.jugar.CambiosDeIntercambioReal
import com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.emulador.AbridorDeSalas
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de [IntercambioRealViewModel] con falsos (ver AGENTS.md y el KDoc de
 * [SeguirJugandoViewModelTest] sobre por qué `runCurrent()`/`advanceTimeBy()` a mano, nunca
 * `advanceUntilIdle()`, con el sondeo de 500 ms corriendo).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntercambioRealViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private val juegoA = JuegoRespaldado(
        id = 1L,
        sistema = "gba",
        nombre = "Pokemon Edicion Rojo Fuego",
        rutaDePartida = "/roms/gba/Pokemon Edicion Rojo Fuego.srm",
        esPokemon = true,
        editable = true,
        ultimoRespaldo = 1_000L,
        resumen = null,
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )
    private val juegoB = JuegoRespaldado(
        id = 2L,
        sistema = "gba",
        nombre = "Pokemon Edicion Esmeralda",
        rutaDePartida = "/roms/gba/Pokemon Edicion Esmeralda.srm",
        esPokemon = true,
        editable = true,
        ultimoRespaldo = 1_000L,
        resumen = null,
        estadoDePartida = EstadoDePartida.CON_DATOS,
    )

    private fun pump() = mainDispatcher.scheduler.runCurrent()

    private class Escenario(
        juegos: List<JuegoRespaldado>,
        val sesion: MutableStateFlow<com.baxner.r36scompanion.data.consola.SesionDeConsola?> = MutableStateFlow(SesionDeConsolaFalsa()),
        val roms: RomsFalsas = RomsFalsas(),
        val reales: IntercambiosRealesFalsos = IntercambiosRealesFalsos(),
        val sala: SalaFalsa = SalaFalsa(jugadores = 2),
        val textos: Textos,
    ) {
        val repositorio = RepositorioDeRespaldosFalso(juegos)
        val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val abridor = AbridorDeSalas { _, _ -> sala }

        fun viewModel(juegoAId: Long = 1L, juegoBId: Long = 2L) = IntercambioRealViewModel(
            juegoA = juegoAId,
            juegoB = juegoBId,
            repositorio = repositorio,
            roms = roms,
            reales = reales,
            sesion = sesion,
            abridor = abridor,
            alcanceDeLaApp = alcance,
            textos = textos,
        )
    }

    private fun escenario() = Escenario(listOf(juegoA, juegoB), textos = textos)

    @Test
    fun empiezaUnoNuevoYLlegaAInstrucciones() {
        val f = escenario()
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeIntercambioReal.INSTRUCCIONES, vm.uiState.value.etapa)
        assertEquals("Rojo Fuego", vm.uiState.value.nombreA)
        assertEquals("Esmeralda", vm.uiState.value.nombreB)
        assertFalse(f.sala.arrancada)
    }

    @Test
    fun empezarAJugarAbreLaSalaConCableYSonidoDelJugador0() {
        val f = escenario()
        val vm = f.viewModel()
        pump()

        vm.empezarAJugar()
        pump()

        assertEquals(EtapaDeIntercambioReal.JUGANDO, vm.uiState.value.etapa)
        assertTrue(f.sala.arrancada)
        assertEquals(0, f.sala.sonidoDeJugador)
        assertEquals(0, vm.uiState.value.jugadorControlado)
    }

    @Test
    fun cambiarJugadorControladoCambiaElSonido() {
        val f = escenario()
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()

        vm.controlar(1)
        assertEquals(1, vm.uiState.value.jugadorControlado)
        assertEquals(1, f.sala.sonidoDeJugador)

        vm.controlar(0)
        assertEquals(0, vm.uiState.value.jugadorControlado)
        assertEquals(0, f.sala.sonidoDeJugador)
    }

    @Test
    fun guardaCadaJugadorPorSeparadoYNoRepite() {
        val f = escenario()
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()

        f.sala.simularGuardado(0, byteArrayOf(1, 1))
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertEquals(1, f.reales.historialDeGuardados.size)
        assertEquals(0, f.reales.historialDeGuardados.last().second)

        // Jugador 1 guarda, jugador 0 no cambia otra vez.
        f.sala.simularGuardado(1, byteArrayOf(2, 2))
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertEquals(2, f.reales.historialDeGuardados.size)
        assertEquals(1, f.reales.historialDeGuardados.last().second)

        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertEquals(2, f.reales.historialDeGuardados.size)
    }

    @Test
    fun pausaYReanuda() {
        val f = escenario()
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()

        vm.pausar()
        assertTrue(f.sala.pausada)
        vm.reanudar()
        assertFalse(f.sala.pausada)
    }

    @Test
    fun terminarVaARevisionConLasFrasesDeCambios() {
        val f = escenario()
        f.reales.cambiosARespoonder = CambiosDeIntercambioReal(
            resumenA = null,
            resumenB = null,
            cambiosA = listOf(Cambio.SalioDelEquipo(com.baxner.r36scompanion.data.respaldos.PokemonCorto(especie = "Kadabra", especieId = 64, nivel = 30))),
            cambiosB = listOf(Cambio.EntroAlEquipo(com.baxner.r36scompanion.data.respaldos.PokemonCorto(especie = "Kadabra", especieId = 64, nivel = 30))),
        )
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()

        vm.pedirTerminar()
        assertEquals(EtapaDeIntercambioReal.CONFIRMAR_TERMINAR, vm.uiState.value.etapa)

        vm.confirmarTerminar()
        pump()

        assertEquals(EtapaDeIntercambioReal.REVISION, vm.uiState.value.etapa)
        assertTrue(f.sala.terminada)
        assertEquals(1, vm.uiState.value.frasesDeCambiosA.size)
        assertEquals(1, vm.uiState.value.frasesDeCambiosB.size)
        assertTrue(vm.uiState.value.frasesDeCambiosA.first().contains("Salió"))
        assertTrue(vm.uiState.value.frasesDeCambiosB.first().contains("Entró"))
    }

    @Test
    fun escribirSinConexionQuedaPendiente() {
        val f = escenario()
        f.sesion.value = null
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()
        vm.pedirTerminar()
        vm.confirmarTerminar()
        pump()

        vm.escribir()
        pump()

        assertEquals(EtapaDeIntercambioReal.REVISION, vm.uiState.value.etapa)
        assertTrue(vm.uiState.value.mensajeDeError!!.contains("conexión"))
        assertEquals(0, f.reales.vecesLlamadoEscribir)
    }

    @Test
    fun escribirConExitoTerminaElIntercambio() {
        val f = escenario()
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()
        vm.pedirTerminar()
        vm.confirmarTerminar()
        pump()

        vm.escribir()
        pump()

        assertEquals(EtapaDeIntercambioReal.ESCRITO, vm.uiState.value.etapa)
        assertEquals(1, f.reales.vecesLlamadoEscribir)
    }

    @Test
    fun descartarTerminaElIntercambioSinEscribir() {
        val f = escenario()
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()
        vm.pedirTerminar()
        vm.confirmarTerminar()
        pump()

        vm.pedirDescartar()
        assertEquals(EtapaDeIntercambioReal.CONFIRMAR_DESCARTAR, vm.uiState.value.etapa)
        vm.confirmarDescartar()
        pump()

        assertEquals(EtapaDeIntercambioReal.DESCARTADO, vm.uiState.value.etapa)
        assertEquals(1, f.reales.vecesLlamadoDescartar)
        assertEquals(0, f.reales.vecesLlamadoEscribir)
    }

    @Test
    fun retomaUnPendientePorEscribirVaDirectoARevisionSinAbrirLaSala() {
        val f = escenario()
        f.reales.ponerPendiente(
            IntercambioReal(
                id = 5L,
                juegoA = 1L,
                baseA = null,
                juegoB = 2L,
                baseB = null,
                empezado = 1_000L,
                vecesGuardadaA = 1,
                vecesGuardadaB = 0,
                estado = EstadoDeIntercambioReal.POR_ESCRIBIR,
            ),
            ByteArray(4),
            ByteArray(4),
        )
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeIntercambioReal.REVISION, vm.uiState.value.etapa)
        assertFalse(f.sala.arrancada)
        assertEquals(0, f.roms.vecesLlamadoTraer)
    }

    @Test
    fun siNoHayRomNiConsolaPideConectar() {
        val f = Escenario(listOf(juegoA, juegoB), sesion = MutableStateFlow(null), textos = textos)
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeIntercambioReal.FALTA_CONSOLA, vm.uiState.value.etapa)
    }

    @Test
    fun errorDeRomMuestraElMensaje() {
        val f = escenario()
        f.roms.errorAlTraer = ErrorDeRom.FormatoNoSoportado("/roms/gba/Pokemon Edicion Rojo Fuego.7z", "7z")
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeIntercambioReal.ERROR_DE_ROM, vm.uiState.value.etapa)
        assertTrue(vm.uiState.value.mensajeDeErrorDeRom!!.contains(".7z"))
    }

    @Test
    fun hayOtroIntercambioMuestraElError() {
        val f = escenario()
        f.reales.errorAlEmpezar = ErrorDeJuego.HayOtroIntercambio(99L)
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeIntercambioReal.ERROR_AL_EMPEZAR, vm.uiState.value.etapa)
        assertTrue(vm.uiState.value.mensajeDeError!!.contains("intercambio"))
    }

    @Test
    fun necesitaPokedexNacionalEntreFireRedYEsmeralda() {
        val f = Escenario(
            listOf(
                juegoA.copy(resumen = com.baxner.r36scompanion.data.respaldos.ResumenGuardado(version = "FireRed", entrenador = "Rojo", idDeEntrenador = "1/2")),
                juegoB.copy(resumen = com.baxner.r36scompanion.data.respaldos.ResumenGuardado(version = "Emerald", entrenador = "Rojo", idDeEntrenador = "1/2")),
            ),
            textos = textos,
        )
        val vm = f.viewModel()
        pump()

        assertTrue(vm.uiState.value.necesitaPokedexNacional)
    }

    @Test
    fun alTerminarSinEspacioPideReintentarYNoPierdeLaPartida() {
        val f = escenario()
        val vm = f.viewModel()
        pump()
        vm.empezarAJugar()
        pump()

        f.reales.errorAlGuardar = java.io.IOException("sin espacio")
        f.sala.simularGuardado(1, byteArrayOf(2, 2))
        vm.confirmarTerminar()
        pump()
        assertEquals(EtapaDeIntercambioReal.ERROR_AL_GUARDAR, vm.uiState.value.etapa)
        assertTrue(vm.uiState.value.errorAlGuardarEnElTelefono!!.contains("jugador 2"))

        f.reales.errorAlGuardar = null
        vm.confirmarTerminar()
        pump()
        assertEquals(EtapaDeIntercambioReal.REVISION, vm.uiState.value.etapa)
        assertTrue(f.reales.historialDeGuardados.last().third.contentEquals(byteArrayOf(2, 2)))
    }
}
