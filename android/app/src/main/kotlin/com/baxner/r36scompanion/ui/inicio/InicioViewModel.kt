package com.baxner.r36scompanion.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.data.respaldos.ProgresoDeRespaldo
import com.baxner.r36scompanion.data.respaldos.ResultadoDeRespaldo
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
) {
    val conectada: Boolean get() = estadoDeConexion is EstadoDeConexion.Conectada
}

/**
 * Inicio: la tarjeta de conexión (delegando en [Modulos.conexion]), la lista de juegos
 * ([Modulos.repositorioDeRespaldos]) y "Respaldar todo", a mano o solo al conectar si
 * [com.baxner.r36scompanion.data.preferencias.PreferenciasDeLaApp.respaldoAutomatico] está
 * encendido (ver el encargo, punto 3).
 */
class InicioViewModel(private val modulos: Modulos) : ViewModel() {
    private val _progreso = MutableStateFlow<ProgresoDeRespaldo?>(null)
    private val _resultado = MutableStateFlow<ResultadoDeRespaldo?>(null)
    private val _error = MutableStateFlow<String?>(null)

    private val juegosOrganizados = modulos.repositorioDeRespaldos.juegos().map(::organizarJuegos)

    val uiState: StateFlow<InicioUiState> = combine(
        modulos.conexion.estado,
        juegosOrganizados,
        _progreso,
        _resultado,
        _error,
    ) { estado, juegos, progreso, resultado, error ->
        InicioUiState(estado, juegos, progreso, resultado, error)
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
}
