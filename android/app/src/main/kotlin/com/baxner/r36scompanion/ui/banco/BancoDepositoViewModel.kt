package com.baxner.r36scompanion.ui.banco

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.editor.Caja
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.ui.compartido.juegosDePokemonEditables
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeEscritura
import com.baxner.r36scompanion.ui.compartido.ultimaPartidaDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** En qué va la depositada (ver el encargo, punto 3: elegir juego → sus cajas → un Pokémon → confirmación). */
enum class EtapaDeDeposito { ELEGIR_JUEGO, ELEGIR_POKEMON, CONFIRMAR, EXITO }

data class BancoDepositoUiState(
    val etapa: EtapaDeDeposito = EtapaDeDeposito.ELEGIR_JUEGO,
    val cargando: Boolean = true,
    val juegosEditables: List<JuegoRespaldado> = emptyList(),
    val juegoElegido: JuegoRespaldado? = null,
    val cajas: List<Caja> = emptyList(),
    val casillaElegida: Casilla? = null,
    val pokemonElegido: ResumenDePokemon? = null,
    val procesando: Boolean = false,
    val error: String? = null,
)

/**
 * El depósito de un Pokémon en el banco (ver el encargo, punto 3): lee las cajas del juego
 * elegido con [com.baxner.r36scompanion.data.editor.EditorDePartidas] (nunca el equipo: la v1 de
 * [com.baxner.r36scompanion.data.banco.Banco] solo deposita desde cajas) y, al confirmar,
 * `prepararDeposito` → `EscritorDePartidas.escribir` → `confirmar`, o `cancelar` si la escritura
 * falla (ver el KDoc de `Banco.kt`).
 */
class BancoDepositoViewModel(private val modulos: Modulos) : ViewModel() {
    private val _uiState = MutableStateFlow(BancoDepositoUiState())
    val uiState: StateFlow<BancoDepositoUiState> = _uiState.asStateFlow()

    private var bytesDeLaPartida: ByteArray? = null

    init {
        viewModelScope.launch {
            val juegos = modulos.repositorioDeRespaldos.juegosDePokemonEditables()
            _uiState.value = _uiState.value.copy(cargando = false, juegosEditables = juegos)
        }
    }

    fun elegirJuego(juego: JuegoRespaldado) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, juegoElegido = juego)
            try {
                val bytes = modulos.repositorioDeRespaldos.ultimaPartidaDe(juego.id)
                    ?: throw IllegalStateException("no hay una partida respaldada de este juego")
                bytesDeLaPartida = bytes
                val cajas = modulos.editorDePartidas.abrir(bytes, juego.nombre).cajas()
                _uiState.value = _uiState.value.copy(cargando = false, cajas = cajas, etapa = EtapaDeDeposito.ELEGIR_POKEMON)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message ?: "no se pudieron leer sus cajas")
            }
        }
    }

    fun elegirPokemon(casilla: Casilla, pokemon: ResumenDePokemon) {
        _uiState.value = _uiState.value.copy(etapa = EtapaDeDeposito.CONFIRMAR, casillaElegida = casilla, pokemonElegido = pokemon, error = null)
    }

    fun volverAElegirJuego() {
        _uiState.value = _uiState.value.copy(etapa = EtapaDeDeposito.ELEGIR_JUEGO, juegoElegido = null, cajas = emptyList(), error = null)
        bytesDeLaPartida = null
    }

    fun volverAElegirPokemon() {
        _uiState.value = _uiState.value.copy(etapa = EtapaDeDeposito.ELEGIR_POKEMON, casillaElegida = null, pokemonElegido = null, error = null)
    }

    fun confirmar() {
        val estado = _uiState.value
        val juego = estado.juegoElegido ?: return
        val casilla = estado.casillaElegida ?: return
        val bytes = bytesDeLaPartida ?: return
        val sesion = modulos.conexion.sesion.value ?: return
        if (estado.procesando) return

        _uiState.value = estado.copy(procesando = true, error = null)
        viewModelScope.launch {
            try {
                val movimiento = modulos.banco.prepararDeposito(juego.id, bytes, juego.nombre, casilla)
                try {
                    modulos.escritorDePartidas.escribir(sesion, juego.id, movimiento.partidaNueva, movimiento.base)
                    modulos.banco.confirmar(movimiento)
                    _uiState.value = _uiState.value.copy(procesando = false, etapa = EtapaDeDeposito.EXITO)
                } catch (e: Exception) {
                    modulos.banco.cancelar(movimiento)
                    _uiState.value = _uiState.value.copy(procesando = false, error = mensajeDeErrorDeEscritura(e))
                    modulos.conexion.reportarFalloDeRed()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(procesando = false, error = e.message ?: "no se pudo preparar el depósito")
            }
        }
    }

    fun descartarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
