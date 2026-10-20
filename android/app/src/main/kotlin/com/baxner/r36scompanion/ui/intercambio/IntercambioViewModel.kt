package com.baxner.r36scompanion.ui.intercambio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baxner.r36scompanion.Modulos
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.intercambio.ErrorDeIntercambio
import com.baxner.r36scompanion.data.intercambio.OfertaDeIntercambio
import com.baxner.r36scompanion.data.intercambio.VistaPreviaDeIntercambio
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.ui.compartido.juegosDePokemonEditables
import com.baxner.r36scompanion.ui.compartido.mensajeDeErrorDeIntercambio
import com.baxner.r36scompanion.ui.compartido.ultimaPartidaDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * En qué va el intercambio (ver el encargo, punto 4, y el de la fase 5, punto 5): primero se
 * elige entre rápido y real ([TipoDeIntercambio]); el real reusa [ELEGIR_JUEGO_A]/[ELEGIR_JUEGO_B]
 * (la misma elección de los dos juegos) y, al tener los dos, [LISTO_PARA_REAL] le dice a la
 * pantalla que navegue a [com.baxner.r36scompanion.ui.jugar.IntercambioRealScreen] (este
 * ViewModel no conoce la navegación).
 */
enum class EtapaDeIntercambio { ELEGIR_TIPO, ELEGIR_JUEGO_A, ELEGIR_JUEGO_B, LISTO_PARA_REAL, ELEGIR_POKEMON, VISTA_PREVIA, EXITO }

enum class TipoDeIntercambio { RAPIDO, REAL }

data class IntercambioUiState(
    val etapa: EtapaDeIntercambio = EtapaDeIntercambio.ELEGIR_TIPO,
    val tipo: TipoDeIntercambio = TipoDeIntercambio.RAPIDO,
    val cargando: Boolean = true,
    val juegosEditables: List<JuegoRespaldado> = emptyList(),
    val juegoA: JuegoRespaldado? = null,
    val juegoB: JuegoRespaldado? = null,
    val pokemonDeA: List<ResumenDePokemon> = emptyList(),
    val pokemonDeB: List<ResumenDePokemon> = emptyList(),
    val seleccionA: Set<Casilla> = emptySet(),
    val seleccionB: Set<Casilla> = emptySet(),
    val evolucionar: Boolean = true,
    val vistaPrevia: VistaPreviaDeIntercambio? = null,
    val procesando: Boolean = false,
    val error: String? = null,
) {
    /** Hay algo que mandar de los dos lados: lo mínimo para pedir una vista previa con sentido. */
    val sePuedeVerVistaPrevia: Boolean get() = seleccionA.isNotEmpty() || seleccionB.isNotEmpty()
}

/**
 * El intercambio entre dos partidas del usuario: rápido (ver el KDoc de `Intercambio.kt`) o real,
 * jugando dentro de los dos juegos (ver el encargo de la fase 5, punto 4). Elegir A y B →
 * rápido: elegir qué manda cada lado → `vistaPrevia` → `intercambiar` →
 * `EjecutorDeIntercambio.escribirEnLaConsola`; real: [EtapaDeIntercambio.LISTO_PARA_REAL] y la
 * pantalla navega a la pantalla nueva con los dos juegos elegidos.
 */
class IntercambioViewModel(private val modulos: Modulos) : ViewModel() {
    private val _uiState = MutableStateFlow(IntercambioUiState())
    val uiState: StateFlow<IntercambioUiState> = _uiState.asStateFlow()

    private var bytesA: ByteArray? = null
    private var bytesB: ByteArray? = null

    init {
        viewModelScope.launch {
            val juegos = modulos.repositorioDeRespaldos.juegosDePokemonEditables()
            _uiState.value = _uiState.value.copy(cargando = false, juegosEditables = juegos)
        }
    }

    fun elegirTipo(tipo: TipoDeIntercambio) {
        _uiState.value = _uiState.value.copy(tipo = tipo, etapa = EtapaDeIntercambio.ELEGIR_JUEGO_A)
    }

    fun volverAElegirTipo() {
        bytesA = null
        bytesB = null
        _uiState.value = IntercambioUiState(cargando = false, juegosEditables = _uiState.value.juegosEditables)
    }

    fun elegirJuegoA(juego: JuegoRespaldado) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, juegoA = juego, error = null)
            try {
                bytesA = modulos.repositorioDeRespaldos.ultimaPartidaDe(juego.id) ?: throw IllegalStateException("no hay una partida respaldada de este juego")
                _uiState.value = _uiState.value.copy(cargando = false, etapa = EtapaDeIntercambio.ELEGIR_JUEGO_B)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, juegoA = null, error = e.message ?: "no se pudo leer esa partida")
            }
        }
    }

    fun elegirJuegoB(juego: JuegoRespaldado) {
        val bytesDeA = bytesA ?: return
        val juegoA = _uiState.value.juegoA ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, juegoB = juego, error = null)
            try {
                bytesB = modulos.repositorioDeRespaldos.ultimaPartidaDe(juego.id) ?: throw IllegalStateException("no hay una partida respaldada de este juego")
                if (_uiState.value.tipo == TipoDeIntercambio.REAL) {
                    _uiState.value = _uiState.value.copy(cargando = false, etapa = EtapaDeIntercambio.LISTO_PARA_REAL)
                    return@launch
                }
                val pokemonDeA = modulos.editorDePartidas.abrir(bytesDeA, juegoA.nombre).let { it.equipo() + it.cajas().flatMap { caja -> caja.pokemon } }
                val pokemonDeB = modulos.editorDePartidas.abrir(bytesB!!, juego.nombre).let { it.equipo() + it.cajas().flatMap { caja -> caja.pokemon } }
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    pokemonDeA = pokemonDeA,
                    pokemonDeB = pokemonDeB,
                    etapa = EtapaDeIntercambio.ELEGIR_POKEMON,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, juegoB = null, error = e.message ?: "no se pudo leer esa partida")
            }
        }
    }

    fun alternarSeleccionA(casilla: Casilla) {
        val actual = _uiState.value.seleccionA
        _uiState.value = _uiState.value.copy(seleccionA = if (casilla in actual) actual - casilla else actual + casilla)
    }

    fun alternarSeleccionB(casilla: Casilla) {
        val actual = _uiState.value.seleccionB
        _uiState.value = _uiState.value.copy(seleccionB = if (casilla in actual) actual - casilla else actual + casilla)
    }

    fun alternarEvolucionar() {
        _uiState.value = _uiState.value.copy(evolucionar = !_uiState.value.evolucionar)
    }

    fun verVistaPrevia() {
        val estado = _uiState.value
        val juegoA = estado.juegoA ?: return
        val juegoB = estado.juegoB ?: return
        val a = bytesA ?: return
        val b = bytesB ?: return
        if (estado.cargando) return

        _uiState.value = estado.copy(cargando = true, error = null)
        viewModelScope.launch {
            try {
                val oferta = OfertaDeIntercambio(envia = estado.seleccionA.toList(), recibe = estado.seleccionB.toList(), evolucionar = estado.evolucionar)
                val vistaPrevia = modulos.intercambios.vistaPrevia(a, juegoA.nombre, b, juegoB.nombre, oferta)
                _uiState.value = _uiState.value.copy(cargando = false, vistaPrevia = vistaPrevia, etapa = EtapaDeIntercambio.VISTA_PREVIA)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = e.message ?: "no se pudo preparar la vista previa")
            }
        }
    }

    fun confirmar() {
        val estado = _uiState.value
        val juegoA = estado.juegoA ?: return
        val juegoB = estado.juegoB ?: return
        val a = bytesA ?: return
        val b = bytesB ?: return
        val sesion = modulos.conexion.sesion.value ?: return
        if (estado.procesando) return

        _uiState.value = estado.copy(procesando = true, error = null)
        viewModelScope.launch {
            try {
                val oferta = OfertaDeIntercambio(envia = estado.seleccionA.toList(), recibe = estado.seleccionB.toList(), evolucionar = estado.evolucionar)
                val partidas = modulos.intercambios.intercambiar(a, juegoA.nombre, b, juegoB.nombre, oferta)
                modulos.ejecutorDeIntercambio.escribirEnLaConsola(
                    sesion = sesion,
                    juegoA = juegoA.id,
                    baseA = AlmacenDeBlobs.sha256Hex(a),
                    juegoB = juegoB.id,
                    baseB = AlmacenDeBlobs.sha256Hex(b),
                    partidas = partidas,
                )
                _uiState.value = _uiState.value.copy(procesando = false, etapa = EtapaDeIntercambio.EXITO)
            } catch (e: ErrorDeIntercambio) {
                _uiState.value = _uiState.value.copy(procesando = false, error = mensajeDeErrorDeIntercambio(e))
                modulos.conexion.reportarFalloDeRed()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(procesando = false, error = e.message ?: "no se pudo intercambiar: se perdió la conexión con la consola")
                modulos.conexion.reportarFalloDeRed()
            }
        }
    }

    fun volverAElegirJuegoA() {
        bytesA = null
        bytesB = null
        _uiState.value = IntercambioUiState(cargando = false, juegosEditables = _uiState.value.juegosEditables, tipo = _uiState.value.tipo, etapa = EtapaDeIntercambio.ELEGIR_JUEGO_A)
    }

    fun volverAElegirJuegoB() {
        bytesB = null
        _uiState.value = _uiState.value.copy(
            etapa = EtapaDeIntercambio.ELEGIR_JUEGO_B,
            juegoB = null,
            pokemonDeA = emptyList(),
            pokemonDeB = emptyList(),
            seleccionA = emptySet(),
            seleccionB = emptySet(),
            error = null,
        )
    }

    fun volverAElegirPokemon() {
        _uiState.value = _uiState.value.copy(etapa = EtapaDeIntercambio.ELEGIR_POKEMON, vistaPrevia = null, error = null)
    }

    fun descartarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
