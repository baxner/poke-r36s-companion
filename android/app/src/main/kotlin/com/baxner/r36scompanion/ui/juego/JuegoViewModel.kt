package com.baxner.r36scompanion.ui.juego

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * El detalle de un juego (ver el encargo, punto 4): el juego en sí (cabecera y equipo), su
 * historial de partida y de estados, y restaurar/exportar. Expone varios [StateFlow] sueltos (en
 * vez de un único estado combinado) porque mezclar seis flujos de tipos distintos en un solo
 * `combine` no sale limpio; [ui.juego.JuegoScreen] arma su `JuegoUiState` juntándolos.
 */
class JuegoViewModel(private val modulos: Modulos, private val juegoId: Long) : ViewModel() {

    val juego: StateFlow<JuegoRespaldado?> = modulos.repositorioDeRespaldos.juegos()
        .map { lista -> lista.firstOrNull { it.id == juegoId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val historialYEstados: StateFlow<Pair<List<ItemDeHistorial>, List<VersionRespaldada>>> =
        modulos.repositorioDeRespaldos.versiones(juegoId)
            .map { lista ->
                val (partida, estados) = lista.partition { it.tipo == TipoDeArchivo.PARTIDA }
                historialDePartida(partida, modulos.textos) to estados.sortedByDescending { it.respaldadaEn }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ItemDeHistorial>() to emptyList())

    val historialDePartida: StateFlow<List<ItemDeHistorial>> =
        historialYEstados.map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val versionesDeEstados: StateFlow<List<VersionRespaldada>> =
        historialYEstados.map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conectada: StateFlow<Boolean> = modulos.conexion.estado
        .map { it is EstadoDeConexion.Conectada }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _restaurando = MutableStateFlow(false)
    val restaurando: StateFlow<Boolean> = _restaurando.asStateFlow()

    private val _resultadoDeRestaurar = MutableStateFlow<ResultadoDeEscritura?>(null)
    val resultadoDeRestaurar: StateFlow<ResultadoDeEscritura?> = _resultadoDeRestaurar.asStateFlow()

    private val _errorDeRestaurar = MutableStateFlow<String?>(null)
    val errorDeRestaurar: StateFlow<String?> = _errorDeRestaurar.asStateFlow()

    private val _errorDeExportar = MutableStateFlow<String?>(null)
    val errorDeExportar: StateFlow<String?> = _errorDeExportar.asStateFlow()

    /** Restaura [versionId] en la consola (ver el encargo, punto 4: diálogo de confirmación aparte, en la pantalla). */
    fun restaurar(versionId: Long) {
        val sesion = modulos.conexion.sesion.value ?: return
        if (_restaurando.value) return
        _restaurando.value = true
        _resultadoDeRestaurar.value = null
        _errorDeRestaurar.value = null
        viewModelScope.launch {
            try {
                _resultadoDeRestaurar.value = modulos.escritorDePartidas.restaurar(sesion, versionId)
            } catch (e: ErrorDeEscritura.JuegoAbierto) {
                _errorDeRestaurar.value = "Cierra \"${e.rom}\" en la consola (sal del juego en RetroArch) e inténtalo de nuevo."
            } catch (e: ErrorDeEscritura.Conflicto) {
                _errorDeRestaurar.value =
                    "La partida de la consola cambió desde que se leyó (¿jugaste mientras tanto?). " +
                        "Se respaldó esa versión de todos modos; vuelve a intentarlo si de verdad quieres sobrescribirla."
            } catch (e: Exception) {
                _errorDeRestaurar.value = e.message ?: "no se pudo restaurar: se perdió la conexión con la consola"
                modulos.conexion.reportarFalloDeRed()
            } finally {
                _restaurando.value = false
            }
        }
    }

    fun descartarResultadoDeRestaurar() {
        _resultadoDeRestaurar.value = null
    }

    fun descartarErrorDeRestaurar() {
        _errorDeRestaurar.value = null
    }

    fun descartarErrorDeExportar() {
        _errorDeExportar.value = null
    }

    /** Un .zip con la última versión de cada archivo de este juego (ver el encargo, punto 4). Cierra [destino] siempre. */
    fun exportar(destino: OutputStream) {
        viewModelScope.launch {
            try {
                destino.use { modulos.repositorioDeRespaldos.exportarZip(it, listOf(juegoId)) }
            } catch (e: Exception) {
                _errorDeExportar.value = e.message ?: "no se pudo exportar"
            }
        }
    }
}
