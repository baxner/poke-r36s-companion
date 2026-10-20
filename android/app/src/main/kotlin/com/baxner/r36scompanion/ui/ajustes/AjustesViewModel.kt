package com.baxner.r36scompanion.ui.ajustes

import androidx.lifecycle.ViewModel
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.consola.Credenciales
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Lo que se ve y edita en Ajustes (ver el encargo, punto 7). */
data class AjustesUiState(
    val nombreDeConsola: String? = null,
    val huellaFijada: String? = null,
    val usuario: String = "ark",
    val contrasena: String = "ark",
    val respaldoAutomatico: Boolean = true,
)

/**
 * Ajustes: la consola fijada y sus credenciales ([com.baxner.r36scompanion.data.conexion.PreferenciasDeConsola],
 * cifradas en el teléfono), y el respaldo automático
 * ([com.baxner.r36scompanion.data.preferencias.PreferenciasDeLaApp]). Cada cambio se guarda al
 * toque (sin un botón "Guardar" aparte): son dos preferencias simples, no un formulario largo.
 */
class AjustesViewModel(private val modulos: Modulos) : ViewModel() {
    private val _uiState = MutableStateFlow(estadoDesdePreferencias())
    val uiState: StateFlow<AjustesUiState> = _uiState.asStateFlow()

    private fun estadoDesdePreferencias(): AjustesUiState {
        val credenciales = modulos.preferenciasDeConsola.credenciales
        return AjustesUiState(
            nombreDeConsola = modulos.preferenciasDeConsola.nombreDeEquipo,
            huellaFijada = modulos.preferenciasDeConsola.huellaFijada,
            usuario = credenciales.usuario,
            contrasena = credenciales.contrasena,
            respaldoAutomatico = modulos.preferenciasDeLaApp.respaldoAutomatico,
        )
    }

    fun actualizarUsuario(valor: String) {
        _uiState.update { it.copy(usuario = valor) }
        guardarCredenciales()
    }

    fun actualizarContrasena(valor: String) {
        _uiState.update { it.copy(contrasena = valor) }
        guardarCredenciales()
    }

    private fun guardarCredenciales() {
        modulos.preferenciasDeConsola.credenciales = Credenciales(usuario = _uiState.value.usuario, contrasena = _uiState.value.contrasena)
    }

    fun cambiarRespaldoAutomatico(encendido: Boolean) {
        modulos.preferenciasDeLaApp.respaldoAutomatico = encendido
        _uiState.update { it.copy(respaldoAutomatico = encendido) }
    }

    /** "Olvidar esta consola" (con confirmación en la pantalla): la próxima que conteste se fija como nueva. */
    fun olvidarConsola() {
        modulos.conexion.olvidarConsolaYReconectar()
        _uiState.update { it.copy(nombreDeConsola = null, huellaFijada = null) }
    }
}
