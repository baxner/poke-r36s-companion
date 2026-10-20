package com.baxner.r36scompanion.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.data.intercambio.ErrorDeIntercambio
import com.baxner.r36scompanion.data.jugar.IntercambioReal
import com.baxner.r36scompanion.data.jugar.IntercambiosReales
import com.baxner.r36scompanion.data.jugar.PartidaPrestada
import com.baxner.r36scompanion.data.jugar.Prestamos
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ProgresoDeRespaldo
import com.baxner.r36scompanion.data.respaldos.ResultadoDeRespaldo
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeEscritura
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeIntercambio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que pinta la pantalla de Inicio: la tarjeta de conexión, la lista de juegos y el respaldo en curso. */
data class InicioUiState(
    val estadoDeConexion: EstadoDeConexion = EstadoDeConexion.SinCable,
    val juegos: JuegosOrganizados = JuegosOrganizados(emptyList(), emptyList()),
    val progreso: ProgresoDeRespaldo? = null,
    val resultado: ResultadoDeRespaldo? = null,
    val error: String? = null,
    /** Fase 5 (ver su encargo, punto 5): partidas prestadas e intercambio real pendiente, con sus nombres. */
    val prestadas: List<PartidaPrestada> = emptyList(),
    val intercambioRealPendiente: IntercambioReal? = null,
    val nombresDeJuego: Map<Long, JuegoRespaldado> = emptyMap(),
) {
    val conectada: Boolean get() = estadoDeConexion is EstadoDeConexion.Conectada
}

/**
 * Inicio: la tarjeta de conexión (delegando en [Modulos.conexion]), la lista de juegos
 * ([Modulos.repositorioDeRespaldos]), "Respaldar todo", a mano o solo al conectar si
 * [com.baxner.r36scompanion.data.preferencias.PreferenciasDeLaApp.respaldoAutomatico] está
 * encendido (ver el encargo, punto 3), y las tarjetas de "seguir jugando"/intercambio real
 * pendiente (fase 5, ver su encargo, punto 5). [prestamos]/[intercambiosReales] tienen un valor
 * por defecto sacado de [Modulos] para no cambiar el constructor que ya usa `CompanionNavHost`.
 */
class InicioViewModel(
    private val modulos: Modulos,
    private val prestamos: Prestamos = modulos.prestamos,
    private val intercambiosReales: IntercambiosReales = modulos.intercambiosReales,
) : ViewModel() {
    private val _progreso = MutableStateFlow<ProgresoDeRespaldo?>(null)
    private val _resultado = MutableStateFlow<ResultadoDeRespaldo?>(null)
    private val _error = MutableStateFlow<String?>(null)

    private val juegosOrganizados = modulos.repositorioDeRespaldos.juegos().map(::organizarJuegos)
    private val juegosPorId = modulos.repositorioDeRespaldos.juegos().map { lista -> lista.associateBy { it.id } }

    private data class EstadoBase(
        val estado: EstadoDeConexion,
        val juegos: JuegosOrganizados,
        val progreso: ProgresoDeRespaldo?,
        val resultado: ResultadoDeRespaldo?,
        val error: String?,
    )

    private val base = combine(modulos.conexion.estado, juegosOrganizados, _progreso, _resultado, _error, ::EstadoBase)

    val uiState: StateFlow<InicioUiState> = combine(
        base,
        prestamos.prestadas(),
        intercambiosReales.pendiente(),
        juegosPorId,
    ) { base, prestadas, pendiente, nombres ->
        InicioUiState(base.estado, base.juegos, base.progreso, base.resultado, base.error, prestadas, pendiente, nombres)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUiState())

    /** Si ya se disparó el respaldo automático desde que se conectó por última vez (no en cada recomposición). */
    private var yaSeRespaldoEnEstaConexion = false
    private var respaldando = false

    init {
        viewModelScope.launch {
            modulos.conexion.estado.collect { estado ->
                if (estado is EstadoDeConexion.Conectada) {
                    if (!yaSeRespaldoEnEstaConexion && modulos.preferenciasDeLaApp.respaldoAutomatico) {
                        yaSeRespaldoEnEstaConexion = true
                        respaldarTodo()
                    }
                } else {
                    yaSeRespaldoEnEstaConexion = false
                }
            }
        }
    }

    fun respaldarTodo() {
        val sesion = modulos.conexion.sesion.value ?: return
        if (respaldando) return
        respaldando = true
        _error.value = null
        _resultado.value = null
        viewModelScope.launch {
            try {
                _resultado.value = modulos.repositorioDeRespaldos.respaldarTodo(sesion) { progreso -> _progreso.value = progreso }
            } catch (e: Exception) {
                _error.value = e.message ?: "no se pudo respaldar: se perdió la conexión con la consola"
                modulos.conexion.reportarFalloDeRed()
            } finally {
                _progreso.value = null
                respaldando = false
            }
        }
    }

    fun reintentar() = modulos.conexion.reintentar()

    fun abrirAjustesDeAnclaje(): Boolean = modulos.conexion.abrirAjustesDeAnclaje()

    fun olvidarConsolaYReconectar() = modulos.conexion.olvidarConsolaYReconectar()

    fun descartarResultado() {
        _resultado.value = null
    }

    fun descartarError() {
        _error.value = null
    }

    /** "Escribir" en la tarjeta de intercambio real pendiente (ver el encargo de la fase 5, punto 5). */
    fun escribirIntercambioReal(id: Long) {
        val sesion = modulos.conexion.sesion.value
        if (sesion == null) {
            _error.value = "No hay conexión con la consola: conéctala y vuelve a intentar."
            return
        }
        viewModelScope.launch {
            try {
                intercambiosReales.escribir(sesion, id)
            } catch (e: ErrorDeIntercambio) {
                _error.value = mensajeDeErrorDeIntercambio(e)
                modulos.conexion.reportarFalloDeRed()
            } catch (e: ErrorDeEscritura) {
                _error.value = mensajeDeErrorDeEscritura(e)
            } catch (e: Exception) {
                _error.value = e.message ?: "no se pudo escribir el intercambio"
                modulos.conexion.reportarFalloDeRed()
            }
        }
    }

    /** "Descartar" en la tarjeta de intercambio real pendiente. */
    fun descartarIntercambioReal(id: Long) {
        viewModelScope.launch { intercambiosReales.descartar(id) }
    }
}

