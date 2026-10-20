package com.baxner.r36scompanion.ui.banco

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que pinta la pestaña Banco (ver el encargo, punto 3). */
data class BancoUiState(
    val pokemon: List<PokemonEnBanco> = emptyList(),
    val cajaActual: Int = 0,
    val conectada: Boolean = false,
    val conciliando: Set<Long> = emptySet(),
    val errorDeConciliar: String? = null,
    val resultadoDeConciliar: String? = null,
) {
    val pendientes: List<PokemonEnBanco> get() = pokemon.filter { it.estado != EstadoEnBanco.EN_BANCO }
    val cajasDisponibles: List<Int> get() = cajasConContenido(pokemon)
    val casillas: List<PokemonEnBanco?> get() = casillasDeLaCaja(pokemon, cajaActual)
}

/**
 * El banco en vivo ([Modulos.banco]) más la caja que se está mirando (solo en memoria: no hace
 * falta recordarla entre sesiones) y la conciliación de pendientes (ver el encargo, punto 3:
 * "si al abrir el banco con la consola conectada hay pendientes, ofrecer `conciliar`").
 */
class BancoViewModel(private val modulos: Modulos) : ViewModel() {
    private val _cajaActual = MutableStateFlow(0)
    private val _conciliando = MutableStateFlow<Set<Long>>(emptySet())
    private val _errorDeConciliar = MutableStateFlow<String?>(null)
    private val _resultadoDeConciliar = MutableStateFlow<String?>(null)

    // combine() solo tiene sobrecargas hasta 5 flujos: se anida en dos para los 6 campos del estado.
    private val base = combine(
        modulos.banco.pokemon(),
        _cajaActual,
        modulos.conexion.estado.map { it is EstadoDeConexion.Conectada },
        _conciliando,
        _errorDeConciliar,
    ) { pokemon, caja, conectada, conciliando, error -> BancoUiState(pokemon, caja, conectada, conciliando, error) }

    val uiState: StateFlow<BancoUiState> = combine(base, _resultadoDeConciliar) { estado, resultado ->
        estado.copy(resultadoDeConciliar = resultado)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BancoUiState())

    fun elegirCaja(caja: Int) {
        _cajaActual.value = caja
    }

    /** Decide un pendiente mirando la partida real de su juego en la consola (ver el encargo, punto 3). */
    fun conciliar(pendiente: PokemonEnBanco) {
        val sesion = modulos.conexion.sesion.value ?: return
        val juegoId = pendiente.juegoDeOrigen ?: return
        if (pendiente.id in _conciliando.value) return
        viewModelScope.launch {
            _conciliando.value = _conciliando.value + pendiente.id
            _errorDeConciliar.value = null
            try {
                val juego = modulos.repositorioDeRespaldos.juegos().first().firstOrNull { it.id == juegoId }
                    ?: throw IllegalStateException("ya no está ese juego entre los respaldados")
                val bytesEnConsola = sesion.leer(juego.rutaDePartida)
                modulos.banco.conciliar(pendiente.id, bytesEnConsola, juego.nombre)
                val siguePresente = modulos.banco.pokemon().first().any { it.id == pendiente.id }
                _resultadoDeConciliar.value = explicarConciliacion(pendiente.estado, siguePresente)
            } catch (e: Exception) {
                _errorDeConciliar.value = e.message ?: "no se pudo conciliar: se perdió la conexión con la consola"
                modulos.conexion.reportarFalloDeRed()
            } finally {
                _conciliando.value = _conciliando.value - pendiente.id
            }
        }
    }

    fun descartarErrorDeConciliar() {
        _errorDeConciliar.value = null
    }

    fun descartarResultadoDeConciliar() {
        _resultadoDeConciliar.value = null
    }
}
