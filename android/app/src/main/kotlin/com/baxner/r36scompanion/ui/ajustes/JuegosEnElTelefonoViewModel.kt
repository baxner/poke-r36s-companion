package com.baxner.r36scompanion.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.data.jugar.RomEnElTelefono
import com.baxner.r36scompanion.data.jugar.RomsDelTelefono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Juegos en el teléfono", en Ajustes (ver el encargo de la fase 5, punto 5): las ROMs que
 * [RomsDelTelefono.traer] ya copió, con su tamaño, y borrarlas con confirmación (en la pantalla).
 */
class JuegosEnElTelefonoViewModel(private val roms: RomsDelTelefono) : ViewModel() {
    val romsGuardadas: StateFlow<List<RomEnElTelefono>> =
        roms.guardadas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** El `juegoId` que se está borrando ahora mismo, si alguno. */
    private val _borrando = MutableStateFlow<Long?>(null)
    val borrando: StateFlow<Long?> = _borrando.asStateFlow()

    fun borrar(juegoId: Long) {
        viewModelScope.launch {
            _borrando.value = juegoId
            try {
                roms.borrar(juegoId)
            } finally {
                _borrando.value = null
            }
        }
    }
}
