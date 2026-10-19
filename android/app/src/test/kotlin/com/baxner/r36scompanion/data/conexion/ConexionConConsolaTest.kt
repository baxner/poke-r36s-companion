package com.baxner.r36scompanion.data.conexion

import com.baxner.r36scompanion.data.consola.AjustesRetroArch
import com.baxner.r36scompanion.data.consola.Anclaje
import com.baxner.r36scompanion.data.consola.ArchivoRemoto
import com.baxner.r36scompanion.data.consola.BuscadorDeConsola
import com.baxner.r36scompanion.data.consola.Candidata
import com.baxner.r36scompanion.data.consola.ConectorDeConsola
import com.baxner.r36scompanion.data.consola.Consola
import com.baxner.r36scompanion.data.consola.Credenciales
import com.baxner.r36scompanion.data.consola.ErrorDeConsola
import com.baxner.r36scompanion.data.consola.EstadoDelAnclaje
import com.baxner.r36scompanion.data.consola.InterfazDeAnclaje
import com.baxner.r36scompanion.data.consola.JuegoAbierto
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ojo: el controlador corre en `backgroundScope`, y `advanceUntilIdle()` no corre trabajo de fondo
 * cuando no queda nada en primer plano (un observador de fondo nunca termina). Por eso `runCurrent()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConexionConConsolaTest {
    private val huellaDeLaConsola = "SHA256:consolaReal"
    private val interfaz = InterfazDeAnclaje("rndis0", "10.101.33.64", 24)
    private val laConsola = Candidata("10.101.33.156", 22, "rndis0")

    @Test
    fun sinCableYSinAnclaje() = runTest {
        val f = Escenario(this, EstadoDelAnclaje(cableConectado = false, anclajeActivo = false, interfaces = emptyList()))
        assertEquals(EstadoDeConexion.SinCable, f.conexion.estado.value)
        f.anclaje.estado.value = EstadoDelAnclaje(cableConectado = true, anclajeActivo = false, interfaces = emptyList())
        f.conexion.iniciar()
        runCurrent()
        assertEquals(EstadoDeConexion.SinAnclaje, f.conexion.estado.value)
        f.conexion.detener()
    }

    @Test
    fun laPrimeraVezConectaYFijaLaHuella() = runTest {
        val f = Escenario(this, conAnclaje())
        f.buscador.candidatas = listOf(laConsola)
        f.conexion.iniciar()
        runCurrent()
        val estado = f.conexion.estado.value as EstadoDeConexion.Conectada
        assertTrue(estado.esNueva)
        assertEquals(huellaDeLaConsola, f.preferencias.huellaFijada)
        assertEquals("rg351mp", f.preferencias.nombreDeEquipo)
        assertNull("la primera vez no hay huella que exigir", f.conector.huellasPedidas.single())
        assertNotNull(f.conexion.sesion.value)
        f.conexion.detener()
    }

    @Test
    fun despuesExigeLaHuellaFijada() = runTest {
        val f = Escenario(this, conAnclaje())
        f.preferencias.huellaFijada = huellaDeLaConsola
        f.buscador.candidatas = listOf(laConsola)
        f.conexion.iniciar()
        runCurrent()
        assertEquals(false, (f.conexion.estado.value as EstadoDeConexion.Conectada).esNueva)
        assertEquals(huellaDeLaConsola, f.conector.huellasPedidas.single())
        f.conexion.detener()
    }

    @Test
    fun otraLlaveNoSeFijaHastaQueElUsuarioDiga() = runTest {
        val f = Escenario(this, conAnclaje())
        f.preferencias.huellaFijada = "SHA256:otraConsola"
        f.buscador.candidatas = listOf(laConsola)
        f.conexion.iniciar()
        runCurrent()
        val estado = f.conexion.estado.value as EstadoDeConexion.HuellaDistinta
        assertEquals("SHA256:otraConsola", estado.esperada)
        assertEquals(huellaDeLaConsola, estado.recibida)
        assertEquals("SHA256:otraConsola", f.preferencias.huellaFijada)
        assertNull(f.conexion.sesion.value)

        f.conexion.olvidarConsolaYReconectar()
        runCurrent()
        assertTrue(f.conexion.estado.value is EstadoDeConexion.Conectada)
        assertEquals(huellaDeLaConsola, f.preferencias.huellaFijada)
        f.conexion.detener()
    }

    @Test
    fun nadieContestaYLuegoSi() = runTest {
        val f = Escenario(this, conAnclaje())
        f.buscador.candidatas = emptyList()
        f.conexion.iniciar()
        runCurrent()
        assertEquals(EstadoDeConexion.NoEncontrada, f.conexion.estado.value)
        f.buscador.candidatas = listOf(laConsola)
        f.conexion.reintentar()
        runCurrent()
        assertTrue(f.conexion.estado.value is EstadoDeConexion.Conectada)
        f.conexion.detener()
    }

    @Test
    fun siUnaCandidataNoContestaPruebaLaSiguiente() = runTest {
        val f = Escenario(this, conAnclaje())
        val otra = Candidata("10.101.33.20", 22, "rndis0")
        f.buscador.candidatas = listOf(otra, laConsola)
        f.conector.fallaCon[otra.host] = ErrorDeConsola.SinConexion("no contesta")
        f.conexion.iniciar()
        runCurrent()
        assertEquals("10.101.33.156", (f.conexion.estado.value as EstadoDeConexion.Conectada).consola.host)
        f.conexion.detener()
    }

    @Test
    fun contrasenaMala() = runTest {
        val f = Escenario(this, conAnclaje())
        f.buscador.candidatas = listOf(laConsola)
        f.conector.fallaCon[laConsola.host] = ErrorDeConsola.CredencialesIncorrectas()
        f.conexion.iniciar()
        runCurrent()
        assertEquals(EstadoDeConexion.CredencialesIncorrectas, f.conexion.estado.value)
        f.conexion.detener()
    }

    @Test
    fun alApagarElAnclajeCierraLaSesion() = runTest {
        val f = Escenario(this, conAnclaje())
        f.buscador.candidatas = listOf(laConsola)
        f.conexion.iniciar()
        runCurrent()
        val sesion = f.conexion.sesion.value as SesionFalsa
        f.anclaje.estado.value = EstadoDelAnclaje(cableConectado = true, anclajeActivo = false, interfaces = emptyList())
        runCurrent()
        assertTrue(sesion.cerrada)
        assertNull(f.conexion.sesion.value)
        assertEquals(EstadoDeConexion.SinAnclaje, f.conexion.estado.value)
        f.conexion.detener()
    }

    private fun conAnclaje() = EstadoDelAnclaje(cableConectado = true, anclajeActivo = true, interfaces = listOf(interfaz))

    private inner class Escenario(alcance: TestScope, inicial: EstadoDelAnclaje) {
        val anclaje = AnclajeFalso(inicial)
        val buscador = BuscadorFalso()
        val conector = ConectorFalso(huellaDeLaConsola)
        val preferencias = PreferenciasFalsas()
        val conexion = ConexionConConsola(anclaje, buscador, conector, preferencias, alcance.backgroundScope)
    }
}

private class AnclajeFalso(inicial: EstadoDelAnclaje) : Anclaje {
    val estado = MutableStateFlow(inicial)
    override fun observar(): Flow<EstadoDelAnclaje> = estado
    override fun estadoActual() = estado.value
    override fun abrirAjustesDeAnclaje() = true
}

private class BuscadorFalso : BuscadorDeConsola {
    var candidatas: List<Candidata> = emptyList()
    override fun interfacesDeAnclaje() = emptyList<InterfazDeAnclaje>()
    override suspend fun buscar() = candidatas
}

private class ConectorFalso(private val huellaReal: String) : ConectorDeConsola {
    val huellasPedidas = mutableListOf<String?>()
    val fallaCon = mutableMapOf<String, ErrorDeConsola>()

    override suspend fun conectar(candidata: Candidata, credenciales: Credenciales, huellaFijada: String?): SesionDeConsola {
        huellasPedidas += huellaFijada
        fallaCon[candidata.host]?.let { throw it }
        if (huellaFijada != null && huellaFijada != huellaReal) throw ErrorDeConsola.HuellaDistinta(huellaFijada, huellaReal)
        return SesionFalsa(Consola(candidata.host, candidata.puerto, huellaReal, "ssh-ed25519", "rg351mp"))
    }
}

private class PreferenciasFalsas : PreferenciasDeConsola {
    override var huellaFijada: String? = null
    override var nombreDeEquipo: String? = null
    override var credenciales = Credenciales()
}

private class SesionFalsa(override val consola: Consola) : SesionDeConsola {
    var cerrada = false
    override suspend fun listar(carpeta: String): List<ArchivoRemoto> = TODO()
    override suspend fun info(ruta: String): ArchivoRemoto? = TODO()
    override suspend fun leer(ruta: String, maximo: Long): ByteArray = TODO()
    override suspend fun escribirSeguro(ruta: String, bytes: ByteArray) = TODO()
    override suspend fun mover(desde: String, hacia: String) = TODO()
    override suspend fun crearCarpetas(carpeta: String) = TODO()
    override suspend fun juegosAbiertos(): List<JuegoAbierto> = TODO()
    override suspend fun ajustesRetroArch(): List<AjustesRetroArch> = TODO()
    override fun close() { cerrada = true }
}
