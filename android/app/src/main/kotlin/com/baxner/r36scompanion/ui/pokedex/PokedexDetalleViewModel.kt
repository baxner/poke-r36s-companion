package com.baxner.r36scompanion.ui.pokedex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.pokedex.DondeAtrapar
import com.baxner.r36scompanion.data.pokedex.EntradaDePokedex
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** El detalle de una especie de la Pokédex (ver el encargo, punto 2). */
data class PokedexDetalleUiState(
    val especie: Int,
    val cargando: Boolean = true,
    val entrada: EntradaDePokedex? = null,
    val juegos: Map<Long, JuegoRespaldado> = emptyMap(),
    val dondeAtrapar: List<DondeAtrapar> = emptyList(),
    val error: String? = null,
)

/**
 * No hay un `PokedexGlobal.entrada(especie)` en el contrato (ver Pokedex.kt): se filtra
 * [com.baxner.r36scompanion.data.pokedex.PokedexGlobal.entradas] completo, como ya hace
 * `PokedexViewModel`. Son 386 entradas en memoria; no vale la pena cambiar el contrato por esto.
 */
class PokedexDetalleViewModel(private val modulos: Modulos, private val especie: Int) : ViewModel() {
    private val _uiState = MutableStateFlow(PokedexDetalleUiState(especie = especie))
    val uiState: StateFlow<PokedexDetalleUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            try {
                val entrada = modulos.pokedexGlobal.entradas().firstOrNull { it.especie == especie }
                val juegos = modulos.repositorioDeRespaldos.juegos().first().associateBy { it.id }
                val dondeAtrapar = modulos.pokedexGlobal.dondeAtrapar(especie)
                _uiState.value = PokedexDetalleUiState(
                    especie = especie,
                    cargando = false,
                    entrada = entrada,
                    juegos = juegos,
                    dondeAtrapar = dondeAtrapar,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message ?: "no se pudo cargar")
            }
        }
    }
}
