package com.baxner.r36scompanion.ui.jugar

import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar
import com.baxner.r36scompanion.data.jugar.ErrorDeRom
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.SesionDeConsolaFalsa
import com.baxner.r36scompanion.emulador.AbridorDeSalas
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas de [SeguirJugandoViewModel] con falsos (ver AGENTS.md: nunca contra el emulador ni la
 * consola de verdad). `viewModelScope` usa `Dispatchers.Main`, aquí apuntado a un
 * [StandardTestDispatcher] **propio** (no el de `runTest`): así `runCurrent()`/`advanceTimeBy()`
 * mueven el sondeo de 500 ms a mano, sin que el `advanceUntilIdle()` implícito de `runTest` intente
 * agotar un bucle que nunca termina solo (el mismo motivo que `ConexionConConsolaTest`, ver su
 * comentario sobre `runCurrent()`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeguirJugandoViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val juego = JuegoRespaldado(
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

    private fun pump() = mainDispatcher.scheduler.runCurrent()

    private class Escenario(
        val juegos: List<JuegoRespaldado>,
        val sesion: MutableStateFlow<com.baxner.r36scompanion.data.consola.SesionDeConsola?> = MutableStateFlow(SesionDeConsolaFalsa()),
        val roms: RomsFalsas = RomsFalsas(),
        val prestamos: PrestamosFalsos = PrestamosFalsos(),
        val sala: SalaFalsa = SalaFalsa(jugadores = 1),
    ) {
        val repositorio = RepositorioDeRespaldosFalso(juegos)
        val alcance: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val abridor = AbridorDeSalas { _, _ -> sala }

        fun viewModel(regresarAlAbrir: Boolean = false) = SeguirJugandoViewModel(
            juegoId = 1L,
            repositorio = repositorio,
            roms = roms,
            prestamos = prestamos,
            sesion = sesion,
            abridor = abridor,
            alcanceDeLaApp = alcance,
            regresarAlAbrir = regresarAlAbrir,
        )
    }

    @Test
    fun sinAvisosEmpiezaAJugarDeInmediato() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeSeguirJugando.JUGANDO, vm.uiState.value.etapa)
        assertTrue(f.sala.arrancada)
        assertEquals(0, f.sala.sonidoDeJugador)
    }

    @Test
    fun conAvisosEsperaConfirmar() {
        val f = Escenario(listOf(juego))
        f.prestamos.avisos = listOf(AvisoAntesDeJugar.PartidaVacia)
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeSeguirJugando.AVISOS, vm.uiState.value.etapa)
        assertFalse(f.sala.arrancada)

        vm.continuarApesarDeAvisos()
        pump()
        assertEquals(EtapaDeSeguirJugando.JUGANDO, vm.uiState.value.etapa)
        assertTrue(f.sala.arrancada)
    }

    @Test
    fun siNoHayRomNiConsolaPideConectar() {
        val f = Escenario(listOf(juego), sesion = MutableStateFlow(null))
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeSeguirJugando.SIN_CONSOLA_NI_ROM, vm.uiState.value.etapa)
    }

    @Test
    fun errorDeRomMuestraElMensaje() {
        val f = Escenario(listOf(juego))
        f.roms.errorAlTraer = ErrorDeRom.FormatoNoSoportado("/roms/gba/Pokemon Edicion Rojo Fuego.7z", "7z")
        val vm = f.viewModel()
        pump()

        assertEquals(EtapaDeSeguirJugando.ERROR_DE_ROM, vm.uiState.value.etapa)
        assertTrue(vm.uiState.value.mensajeDeErrorDeRom!!.contains(".7z"))
    }

    @Test
    fun guardaEnElTelefonoCuandoElJuegoGuardaYNoSiNoCambio() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel()
        pump()

        // Medio segundo sin que el juego guarde: no debe haber guardados nuevos.
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertEquals(0, f.prestamos.historialDeGuardados.size)

        f.sala.simularGuardado(0, byteArrayOf(1, 2, 3))
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertEquals(1, f.prestamos.historialDeGuardados.size)
        assertTrue(f.prestamos.historialDeGuardados.last().second.contentEquals(byteArrayOf(1, 2, 3)))

        // Sin otro guardado del juego, el siguiente sondeo no repite el guardado.
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertEquals(1, f.prestamos.historialDeGuardados.size)
    }

    @Test
    fun pausaYReanuda() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel()
        pump()

        vm.pausar()
        assertTrue(f.sala.pausada)
        vm.reanudar()
        assertFalse(f.sala.pausada)
    }

    @Test
    fun salirGuardaLoUltimoAunqueNoHayaGuardadoAunElSondeo() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel()
        pump()

        f.sala.simularGuardado(0, byteArrayOf(9, 9, 9))
        vm.confirmarSalir()
        pump()

        assertTrue(f.sala.terminada)
        assertEquals(EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO, vm.uiState.value.etapa)
        assertTrue(f.prestamos.historialDeGuardados.last().second.contentEquals(byteArrayOf(9, 9, 9)))
    }

    @Test
    fun regresarConConflictoOfreceElegir() {
        val f = Escenario(listOf(juego))
        f.prestamos.errorAlRegresar = conflictoDePrueba()
        val vm = f.viewModel()
        pump()
        vm.confirmarSalir()
        pump()

        vm.regresar()
        pump()
        assertEquals(EtapaDeSeguirJugando.CONFLICTO, vm.uiState.value.etapa)

        f.prestamos.errorAlRegresar = null
        vm.regresarAunqueCambio()
        pump()
        assertEquals(EtapaDeSeguirJugando.REGRESADO, vm.uiState.value.etapa)
        assertEquals(1, f.prestamos.vecesLlamadoRegresarAunqueCambio)
    }

    @Test
    fun regresarConJuegoAbiertoAvisaYReintenta() {
        val f = Escenario(listOf(juego))
        f.prestamos.errorAlRegresar = juegoAbiertoDePrueba()
        val vm = f.viewModel()
        pump()
        vm.confirmarSalir()
        pump()

        vm.regresar()
        pump()
        assertEquals(EtapaDeSeguirJugando.JUEGO_ABIERTO, vm.uiState.value.etapa)

        f.prestamos.errorAlRegresar = null
        vm.reintentarRegresar()
        pump()
        assertEquals(EtapaDeSeguirJugando.REGRESADO, vm.uiState.value.etapa)
    }

    @Test
    fun regresarAlAbrirVaDirectoSinPasarPorJugar() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel(regresarAlAbrir = true)
        pump()

        assertEquals(EtapaDeSeguirJugando.REGRESADO, vm.uiState.value.etapa)
        assertFalse(f.sala.arrancada)
        assertEquals(1, f.prestamos.vecesLlamadoRegresar)
    }

    @Test
    fun siNoSePuedeGuardarEnElTelefonoLoAvisaYLoReintenta() {
        val f = Escenario(listOf(juego))
        f.prestamos.errorAlGuardar = java.io.IOException("sin espacio")
        val vm = f.viewModel()
        pump()

        f.sala.simularGuardado(0, byteArrayOf(4, 5, 6))
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertTrue(vm.uiState.value.errorAlGuardarEnElTelefono!!.contains("sin espacio"))
        assertEquals(0, f.prestamos.historialDeGuardados.size)

        // Se liberó espacio: la siguiente vuelta lo guarda sin que el juego vuelva a guardar.
        f.prestamos.errorAlGuardar = null
        mainDispatcher.scheduler.advanceTimeBy(500)
        pump()
        assertNull(vm.uiState.value.errorAlGuardarEnElTelefono)
        assertTrue(f.prestamos.historialDeGuardados.single().second.contentEquals(byteArrayOf(4, 5, 6)))
    }

    @Test
    fun regresarEsperaAQueLoUltimoEsteGuardadoEnElTelefono() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel()
        pump()

        // Un disco lento: lo último que guardó el juego tarda en quedar en el teléfono.
        val disco = kotlinx.coroutines.CompletableDeferred<Unit>()
        f.prestamos.guardadoDetenido = disco
        f.sala.simularGuardado(0, byteArrayOf(9, 9, 9))
        vm.confirmarSalir()
        pump()
        assertTrue("sigue guardando", vm.uiState.value.procesando)
        assertFalse(vm.uiState.value.etapa == EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO)

        vm.regresar()
        pump()
        assertEquals("no regresa nada mientras guarda", 0, f.prestamos.vecesLlamadoRegresar)

        disco.complete(Unit)
        pump()
        assertEquals(EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO, vm.uiState.value.etapa)
        vm.regresar()
        pump()
        assertEquals(listOf("guardar", "regresar"), f.prestamos.llamadas)
    }

    @Test
    fun siLoUltimoNoSeGuardoNoRegresaUnaVersionVieja() {
        val f = Escenario(listOf(juego))
        val vm = f.viewModel()
        pump()

        f.prestamos.errorAlGuardar = java.io.IOException("sin espacio")
        f.sala.simularGuardado(0, byteArrayOf(7, 7, 7))
        vm.confirmarSalir()
        pump()
        assertEquals(EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO, vm.uiState.value.etapa)
        assertTrue(vm.uiState.value.errorAlGuardarEnElTelefono != null)

        vm.regresar()
        pump()
        assertEquals(0, f.prestamos.vecesLlamadoRegresar)

        f.prestamos.errorAlGuardar = null
        vm.regresar()
        pump()
        assertEquals(listOf("guardar", "regresar"), f.prestamos.llamadas)
        assertTrue(f.prestamos.historialDeGuardados.single().second.contentEquals(byteArrayOf(7, 7, 7)))
    }
}
