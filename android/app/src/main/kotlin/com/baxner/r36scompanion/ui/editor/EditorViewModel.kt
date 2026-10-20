package com.baxner.r36scompanion.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.RevisionDeGuardado
import com.baxner.r36scompanion.data.editor.SesionDeEdicion
import com.baxner.r36scompanion.data.respaldos.ErrorDeEscritura
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * El editor de una partida (ver el encargo de esta fase, punto 5): abre la sesión de edición
 * sobre la última versión respaldada de [juegoId], y la comparten las cuatro pestañas (Equipo,
 * Cajas, Mochila, Entrenador), el editor de un Pokémon y la pantalla de guardar. Cada pantalla
 * lee y cambia la [SesionDeEdicion] directamente (sus funciones son todas `suspend` y rápidas: ver
 * el KDoc de `Editor.kt`); lo que centraliza este ViewModel es lo que varias pantallas necesitan
 * ver reactivo: si hay cambios (para el botón de guardar), la legalidad de todo el equipo y las
 * cajas (para las insignias) y el guardado en sí.
 */
class EditorViewModel(private val modulos: Modulos, private val juegoId: Long) : ViewModel() {

    sealed interface Estado {
        data object Cargando : Estado
        data class Error(val mensaje: String) : Estado
        data class Listo(val sesion: SesionDeEdicion) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Cargando)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val _hayCambios = MutableStateFlow(false)
    val hayCambios: StateFlow<Boolean> = _hayCambios.asStateFlow()

    /**
     * Las casillas ilegales, con su motivo (ver [legalidadDe]: `null` mientras no se ha podido
     * revisar, por ejemplo justo al abrir o si la partida editada ya no abre).
     */
    private val _legalidad = MutableStateFlow<Map<Casilla, Legalidad>?>(null)
    val legalidad: StateFlow<Map<Casilla, Legalidad>?> = _legalidad.asStateFlow()

    val conectada: StateFlow<Boolean> = modulos.conexion.estado
        .map { it is EstadoDeConexion.Conectada }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    sealed interface EstadoDeGuardado {
        data object Inactivo : EstadoDeGuardado
        data object Revisando : EstadoDeGuardado
        data class ListoParaConfirmar(val revision: RevisionDeGuardado) : EstadoDeGuardado
        data object Guardando : EstadoDeGuardado
        data class Exito(val resultado: ResultadoDeEscritura) : EstadoDeGuardado
        data class Fallo(val mensaje: String) : EstadoDeGuardado
    }

    private val _estadoDeGuardado = MutableStateFlow<EstadoDeGuardado>(EstadoDeGuardado.Inactivo)
    val estadoDeGuardado: StateFlow<EstadoDeGuardado> = _estadoDeGuardado.asStateFlow()

    init {
        cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            _estado.value = Estado.Cargando
            try {
                val juego = modulos.repositorioDeRespaldos.juegos().first().firstOrNull { it.id == juegoId }
                    ?: throw IllegalStateException("no se encontró ese juego")
                val versionDePartida = modulos.repositorioDeRespaldos.versiones(juegoId).first().firstOrNull { it.tipo == TipoDeArchivo.PARTIDA }
                    ?: throw IllegalStateException("este juego todavía no tiene una partida respaldada")
                val bytes = modulos.repositorioDeRespaldos.bytesDe(versionDePartida.id)
                val nombreDeArchivo = juego.rutaDePartida.substringAfterLast('/')
                val sesion = modulos.editorDePartidas.abrir(bytes, nombreDeArchivo)
                _estado.value = Estado.Listo(sesion)
                actualizarDerivados(sesion)
            } catch (e: Exception) {
                _estado.value = Estado.Error(e.message ?: "no se pudo abrir la partida para editar")
            }
        }
    }

    fun sesionListo(): SesionDeEdicion? = (estado.value as? Estado.Listo)?.sesion

    /**
     * Las pantallas llaman esto después de cualquier cambio en la sesión (aplicar un Pokémon,
     * cambiar dinero, medallas, nombre o un objeto de la mochila): vuelve a calcular `hayCambios`
     * y la legalidad de todos, para que el botón de guardar y las insignias se actualicen solos.
     */
    fun notificarCambio() {
        val sesion = sesionListo() ?: return
        viewModelScope.launch { actualizarDerivados(sesion) }
    }

    private suspend fun actualizarDerivados(sesion: SesionDeEdicion) {
        _hayCambios.value = sesion.hayCambios
        val revision = sesion.revisarAntesDeGuardar()
        _legalidad.value = if (revision.abre) revision.pokemonIlegales.toMap() else null
    }

    fun deshacerTodo() {
        val sesion = sesionListo() ?: return
        sesion.deshacerTodo()
        notificarCambio()
    }

    /** Abre la pantalla de guardar: pide la revisión de inmediato (ver el encargo, punto 5: "si no abre, no se guarda; si hay ilegales, se avisa"). */
    fun pedirRevisionParaGuardar() {
        val sesion = sesionListo() ?: return
        _estadoDeGuardado.value = EstadoDeGuardado.Revisando
        viewModelScope.launch {
            val revision = sesion.revisarAntesDeGuardar()
            _estadoDeGuardado.value = EstadoDeGuardado.ListoParaConfirmar(revision)
        }
    }

    fun confirmarGuardado() {
        val sesion = sesionListo() ?: return
        val sesionDeConsola = modulos.conexion.sesion.value
        if (sesionDeConsola == null) {
            _estadoDeGuardado.value = EstadoDeGuardado.Fallo("No hay consola conectada: conéctate y vuelve a intentarlo.")
            return
        }
        _estadoDeGuardado.value = EstadoDeGuardado.Guardando
        viewModelScope.launch {
            try {
                val resultado = modulos.escritorDePartidas.escribir(sesionDeConsola, juegoId, sesion.bytesActuales, sesion.base)
                _estadoDeGuardado.value = EstadoDeGuardado.Exito(resultado)
            } catch (e: ErrorDeEscritura.JuegoAbierto) {
                _estadoDeGuardado.value =
                    EstadoDeGuardado.Fallo("Cierra \"${e.rom}\" en la consola (sal del juego en RetroArch) e inténtalo de nuevo.")
            } catch (e: ErrorDeEscritura.Conflicto) {
                _estadoDeGuardado.value =
                    EstadoDeGuardado.Fallo("Jugaste en la consola después de abrir el editor: vuelve a respaldar y edita otra vez.")
            } catch (e: Exception) {
                _estadoDeGuardado.value = EstadoDeGuardado.Fallo(e.message ?: "no se pudo guardar en la consola")
                modulos.conexion.reportarFalloDeRed()
            }
        }
    }

    fun descartarEstadoDeGuardado() {
        _estadoDeGuardado.value = EstadoDeGuardado.Inactivo
    }
}
