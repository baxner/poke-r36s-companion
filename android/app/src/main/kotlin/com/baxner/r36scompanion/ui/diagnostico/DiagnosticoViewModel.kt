package com.baxner.r36scompanion.ui.diagnostico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.consola.AjustesRetroArch
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.esPartidaVacia
import com.baxner.r36scompanion.data.respaldos.tipoYNombreDeArchivo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** La carpeta de GBA en la consola del usuario (ver AGENTS.md y docs/plan.md). */
private const val CarpetaGba = "/roms/gba"

/** Lo que se alcanzó a leer de la consola en el diagnóstico en vivo (vacío mientras no hay sesión). */
private data class HechosDeLaConsola(
    val carpetaGbaExiste: Boolean? = null,
    val partidasEncontradas: Int? = null,
    val ajustesRetroArch: List<AjustesRetroArch> = emptyList(),
    val partidasVaciasConEstadoAutomatico: List<String> = emptyList(),
)

/**
 * Diagnóstico en vivo (ver el encargo, punto 6, y docs/plan.md): combina el
 * [com.baxner.r36scompanion.data.conexion.EstadoDeConexion] con lo que se puede leer de la
 * consola en cuanto hay sesión, y traduce todo con [pasosDeDiagnostico]. Se vuelve a leer la
 * consola cada vez que cambia la sesión (reconectar, o la primera vez que conecta) y a mano con
 * [actualizar] (botón "Actualizar" de la pantalla).
 */
class DiagnosticoViewModel(private val modulos: Modulos) : ViewModel() {
    private val hechosDeLaConsola = MutableStateFlow(HechosDeLaConsola())

    val pasos: StateFlow<List<PasoDeDiagnostico>> = combine(modulos.conexion.estado, hechosDeLaConsola) { estado, hechos ->
        pasosDeDiagnostico(
            HechosDeDiagnostico(
                estadoDeConexion = estado,
                carpetaGbaExiste = hechos.carpetaGbaExiste,
                partidasEncontradas = hechos.partidasEncontradas,
                ajustesRetroArch = hechos.ajustesRetroArch,
                partidasVaciasConEstadoAutomatico = hechos.partidasVaciasConEstadoAutomatico,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            modulos.conexion.sesion.collect { sesion ->
                if (sesion != null) revisarConsola(sesion) else hechosDeLaConsola.value = HechosDeLaConsola()
            }
        }
    }

    /** Botón "Actualizar": vuelve a leer la consola sin esperar a que cambie la sesión. */
    fun actualizar() {
        val sesion = modulos.conexion.sesion.value ?: return
        viewModelScope.launch { revisarConsola(sesion) }
    }

    private suspend fun revisarConsola(sesion: SesionDeConsola) {
        try {
            if (sesion.info(CarpetaGba) == null) {
                hechosDeLaConsola.value = HechosDeLaConsola(carpetaGbaExiste = false)
                return
            }
            val archivos = sesion.listar(CarpetaGba)
                .filter { !it.esCarpeta }
                .mapNotNull { archivo -> tipoYNombreDeArchivo(archivo.nombre)?.let { (tipo, nombre) -> Triple(archivo, tipo, nombre) } }

            val nombresConEstadoAutomatico = archivos.filter { (_, tipo, _) -> tipo == TipoDeArchivo.ESTADO_AUTOMATICO }.map { (_, _, nombre) -> nombre }.toSet()
            val partidas = archivos.filter { (_, tipo, _) -> tipo == TipoDeArchivo.PARTIDA }
            val vacias = partidas
                .filter { (_, _, nombre) -> nombre in nombresConEstadoAutomatico }
                .filter { (archivo, _, _) -> esPartidaVacia(sesion.leer(archivo.ruta)) }
                .map { (_, _, nombre) -> nombre }

            hechosDeLaConsola.value = HechosDeLaConsola(
                carpetaGbaExiste = true,
                partidasEncontradas = partidas.size,
                ajustesRetroArch = sesion.ajustesRetroArch(),
                partidasVaciasConEstadoAutomatico = vacias,
            )
        } catch (_: Exception) {
            // No se pudo leer la consola a medio diagnóstico (se desconectó justo ahora): se deja
            // como estaba (probablemente "no se pudo comprobar") y se avisa a la conexión, igual
            // que cualquier otra operación de red fallida (ver el encargo, punto 3).
            modulos.conexion.reportarFalloDeRed()
        }
    }
}
