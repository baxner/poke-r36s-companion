package com.baxner.r36scompanion.ui.banco

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.banco.MovimientoPreparado
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.ui.compartido.juegosDePokemonEditables
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeEscritura
import com.baxner.r36scompanion.ui.compartido.ultimaPartidaDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * En qué va el retiro (ver el encargo, punto 3). A diferencia del depósito, aquí no hay un paso
 * de "vista previa" aparte: [com.baxner.r36scompanion.data.banco.Banco.prepararRetiro] es lo
 * único que sabe la casilla de destino y la legalidad ahí (mete al Pokémon en la primera casilla
 * libre y marca el pendiente de una vez, ver el KDoc de `Banco.kt`), así que "elegir juego" ya
 * dispara `prepararRetiro` y su resultado ES la vista previa.
 */
enum class EtapaDeRetiro { ELEGIR_JUEGO, VISTA_PREVIA, EXITO }

data class BancoRetiroUiState(
    val etapa: EtapaDeRetiro = EtapaDeRetiro.ELEGIR_JUEGO,
    val cargando: Boolean = true,
    val pokemon: PokemonEnBanco? = null,
    val juegosEditables: List<JuegoRespaldado> = emptyList(),
    val juegoElegido: JuegoRespaldado? = null,
    val movimiento: MovimientoPreparado? = null,
    val procesando: Boolean = false,
    val error: String? = null,
)

class BancoRetiroViewModel(private val modulos: Modulos, private val pokemonId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow(BancoRetiroUiState())
    val uiState: StateFlow<BancoRetiroUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val pokemon = modulos.banco.pokemon().first().firstOrNull { it.id == pokemonId }
            val juegos = modulos.repositorioDeRespaldos.juegosDePokemonEditables()
            _uiState.value = _uiState.value.copy(cargando = false, pokemon = pokemon, juegosEditables = juegos)
        }
    }

    /** Dispara `prepararRetiro`: ya reserva la casilla y marca el pendiente (ver el KDoc de la clase). */
    fun elegirJuego(juego: JuegoRespaldado) {
        if (_uiState.value.cargando) return
        _uiState.value = _uiState.value.copy(cargando = true, juegoElegido = juego, error = null)
        viewModelScope.launch {
            try {
                val bytes = modulos.repositorioDeRespaldos.ultimaPartidaDe(juego.id)
                    ?: throw IllegalStateException("no hay una partida respaldada de este juego")
                val movimiento = modulos.banco.prepararRetiro(pokemonId, juego.id, bytes, juego.nombre)
                _uiState.value = _uiState.value.copy(cargando = false, movimiento = movimiento, etapa = EtapaDeRetiro.VISTA_PREVIA)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, juegoElegido = null, error = e.message ?: "no se pudo preparar el retiro")
            }
        }
    }

    /** El usuario no quiere retirarlo ahí: deshace el pendiente (vuelve a EN_BANCO) y regresa a elegir juego. */
    fun cancelarVistaPrevia() {
        val movimiento = _uiState.value.movimiento ?: return
        viewModelScope.launch {
            modulos.banco.cancelar(movimiento)
            _uiState.value = _uiState.value.copy(etapa = EtapaDeRetiro.ELEGIR_JUEGO, movimiento = null, juegoElegido = null)
        }
    }

    fun confirmar() {
        val estado = _uiState.value
        val movimiento = estado.movimiento ?: return
        val sesion = modulos.conexion.sesion.value ?: return
        if (estado.procesando) return

        _uiState.value = estado.copy(procesando = true, error = null)
        viewModelScope.launch {
            try {
                modulos.escritorDePartidas.escribir(sesion, movimiento.juegoId, movimiento.partidaNueva, movimiento.base)
                modulos.banco.confirmar(movimiento)
                _uiState.value = _uiState.value.copy(procesando = false, etapa = EtapaDeRetiro.EXITO)
            } catch (e: Exception) {
                modulos.banco.cancelar(movimiento)
                _uiState.value = _uiState.value.copy(
                    procesando = false,
                    etapa = EtapaDeRetiro.ELEGIR_JUEGO,
                    movimiento = null,
                    juegoElegido = null,
                    error = mensajeDeErrorDeEscritura(e),
                )
                modulos.conexion.reportarFalloDeRed()
            }
        }
    }

    fun descartarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
