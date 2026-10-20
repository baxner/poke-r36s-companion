package com.baxner.r36scompanion.ui.pokedex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.pokedex.EntradaDePokedex
import com.baxner.r36scompanion.data.pokedex.ResumenDePokedex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo que pinta la pestaña Pokédex (ver el encargo, punto 2). */
data class PokedexUiState(
    val cargando: Boolean = true,
    val resumen: ResumenDePokedex? = null,
    val entradas: List<EntradaDePokedex> = emptyList(),
    val filtro: FiltroDePokedex = FiltroDePokedex.TODAS,
    val error: String? = null,
) {
    val entradasFiltradas: List<EntradaDePokedex> get() = filtro.aplicar(entradas)
}

/**
 * Carga las 386 entradas de [Modulos.pokedexGlobal] una sola vez (junta todas las partidas
 * respaldadas y el banco: puede tardar un poco con varias partidas, por eso [cargando]) y aplica
 * el filtro elegido en memoria, sin volver a tocar el motor (ver [resumenDe]).
 */
class PokedexViewModel(private val modulos: Modulos) : ViewModel() {
    private val _uiState = MutableStateFlow(PokedexUiState())
    val uiState: StateFlow<PokedexUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.value = _uiState.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            try {
                val entradas = modulos.pokedexGlobal.entradas()
                _uiState.value = _uiState.value.copy(cargando = false, entradas = entradas, resumen = resumenDe(entradas))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message ?: "no se pudo cargar la Pokédex")
            }
        }
    }

    fun elegirFiltro(filtro: FiltroDePokedex) {
        _uiState.value = _uiState.value.copy(filtro = filtro)
    }
}
