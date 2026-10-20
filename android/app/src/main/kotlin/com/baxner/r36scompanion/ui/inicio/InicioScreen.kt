package com.baxner.r36scompanion.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.respaldos.ProgresoDeRespaldo
import com.baxner.r36scompanion.data.respaldos.ResultadoDeRespaldo
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.theme.CompanionColors

@Composable
fun InicioScreen(
    onAbrirJuego: (Long) -> Unit,
    onIrAAjustes: () -> Unit,
    onVerDiagnostico: () -> Unit,
    onSeguirJugando: (Long) -> Unit,
    onRegresarDeInmediato: (Long) -> Unit,
    onContinuarIntercambioReal: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modulos = modulosActuales()
    val viewModel: InicioViewModel = viewModel { InicioViewModel(modulos) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mostrarConfirmarOlvidar by remember { mutableStateOf(false) }

    InicioScreenContent(
        uiState = uiState,
        textos = modulos.textos,
        mostrarConfirmarOlvidar = mostrarConfirmarOlvidar,
        onReintentar = viewModel::reintentar,
        onActivarAnclaje = { viewModel.abrirAjustesDeAnclaje() },
        onPedirConfirmarOlvidar = { mostrarConfirmarOlvidar = true },
        onConfirmarOlvidar = {
            mostrarConfirmarOlvidar = false
            viewModel.olvidarConsolaYReconectar()
        },
        onCancelarOlvidar = { mostrarConfirmarOlvidar = false },
        onIrAAjustes = onIrAAjustes,
        onVerDiagnostico = onVerDiagnostico,
        onRespaldarTodo = viewModel::respaldarTodo,
        onDescartarResultado = viewModel::descartarResultado,
        onDescartarError = viewModel::descartarError,
        onAbrirJuego = onAbrirJuego,
        onSeguirJugando = onSeguirJugando,
        onRegresarDeInmediato = onRegresarDeInmediato,
        onContinuarIntercambioReal = onContinuarIntercambioReal,
        onEscribirIntercambioReal = viewModel::escribirIntercambioReal,
        onDescartarIntercambioReal = viewModel::descartarIntercambioReal,
        modifier = modifier,
    )
}

@Composable
fun InicioScreenContent(
    uiState: InicioUiState,
    textos: Textos,
    mostrarConfirmarOlvidar: Boolean,
    onReintentar: () -> Unit,
    onActivarAnclaje: () -> Unit,
    onPedirConfirmarOlvidar: () -> Unit,
    onConfirmarOlvidar: () -> Unit,
    onCancelarOlvidar: () -> Unit,
    onIrAAjustes: () -> Unit,
    onVerDiagnostico: () -> Unit,
    onRespaldarTodo: () -> Unit,
    onDescartarResultado: () -> Unit,
    onDescartarError: () -> Unit,
    onAbrirJuego: (Long) -> Unit,
    onSeguirJugando: (Long) -> Unit,
    onRegresarDeInmediato: (Long) -> Unit,
    onContinuarIntercambioReal: (Long, Long) -> Unit,
    onEscribirIntercambioReal: (Long) -> Unit,
    onDescartarIntercambioReal: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TarjetaDeConexion(
                estado = uiState.estadoDeConexion,
                onReintentar = onReintentar,
                onActivarAnclaje = onActivarAnclaje,
                onPedirConfirmarOlvidar = onPedirConfirmarOlvidar,
                onIrAAjustes = onIrAAjustes,
                onVerDiagnostico = onVerDiagnostico,
            )
        }

        if (uiState.conectada) {
            item {
                PanelDeRespaldo(
                    progreso = uiState.progreso,
                    resultado = uiState.resultado,
                    error = uiState.error,
                    onRespaldarTodo = onRespaldarTodo,
                    onDescartarResultado = onDescartarResultado,
                    onDescartarError = onDescartarError,
                )
            }
        }

        // Fase 5 (ver su encargo, punto 5): una tarjeta por cada partida prestada y, si hay, el intercambio real pendiente.
        items(uiState.prestadas, key = { "prestamo-${it.juegoId}" }) { prestada ->
            TarjetaDePrestamo(prestada, uiState.nombresDeJuego[prestada.juegoId], textos, onSeguirJugando, onRegresarDeInmediato)
        }
        uiState.intercambioRealPendiente?.let { intercambio ->
            item(key = "intercambio-real-pendiente") {
                TarjetaDeIntercambioRealPendiente(
                    intercambio = intercambio,
                    juegoA = uiState.nombresDeJuego[intercambio.juegoA],
                    juegoB = uiState.nombresDeJuego[intercambio.juegoB],
                    textos = textos,
                    onContinuar = onContinuarIntercambioReal,
                    onEscribir = onEscribirIntercambioReal,
                    onDescartar = onDescartarIntercambioReal,
                )
            }
        }

        if (uiState.juegos.pokemon.isEmpty() && uiState.juegos.otros.isEmpty()) {
            item {
                EstadoVacio(
                    titulo = "Todavía no hay nada respaldado",
                    subtitulo = "Conecta la consola y toca “Respaldar todo”.",
                )
            }
        }

        if (uiState.juegos.pokemon.isNotEmpty()) {
            item { TituloDeSeccion("Tus juegos de Pokémon") }
            items(uiState.juegos.pokemon, key = { it.id }) { juego ->
                FilaDeJuegoPokemon(juego = juego, textos = textos, onClick = { onAbrirJuego(juego.id) })
            }
        }

        if (uiState.juegos.otros.isNotEmpty()) {
            item { TituloDeSeccion("Otros juegos") }
            items(uiState.juegos.otros, key = { it.id }) { juego ->
                FilaDeJuegoOtro(juego = juego, onClick = { onAbrirJuego(juego.id) })
            }
        }
    }

    if (mostrarConfirmarOlvidar) {
        AlertDialog(
            onDismissRequest = onCancelarOlvidar,
            confirmButton = {},
            text = { ConfirmarOlvidarConsolaContent(onConfirmar = onConfirmarOlvidar, onCancelar = onCancelarOlvidar) },
        )
    }
}

@Composable
private fun TituloDeSeccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
}

/** El botón "Respaldar todo" con su progreso y el resumen al terminar (ver el encargo, punto 3). */
@Composable
private fun PanelDeRespaldo(
    progreso: ProgresoDeRespaldo?,
    resultado: ResultadoDeRespaldo?,
    error: String?,
    onRespaldarTodo: () -> Unit,
    onDescartarResultado: () -> Unit,
    onDescartarError: () -> Unit,
) {
    TarjetaDeSeccion {
        if (progreso != null) {
            val fraccion = if (progreso.total > 0) progreso.hechos / progreso.total.toFloat() else 0f
            Text("Respaldando… ${progreso.archivoActual ?: ""}", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
            LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.fillMaxWidth())
        } else {
            Button(onClick = onRespaldarTodo) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Respaldar todo")
            }
        }

        resultado?.let {
            FilaDescartable(onDescartarResultado) {
                Text(
                    "Listo: ${it.nuevas.size} ${if (it.nuevas.size == 1) "nueva" else "nuevas"}, ${it.sinCambios} sin cambios" +
                        if (it.fallidos.isNotEmpty()) ", ${it.fallidos.size} ${if (it.fallidos.size == 1) "fallida" else "fallidas"}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.fallidos.isNotEmpty()) CompanionColors.Warning else CompanionColors.Ok,
                )
            }
        }

        error?.let { mensaje ->
            FilaDescartable(onDescartarError) {
                FilaDeNivel(nivel = NivelVisual.ERROR, titulo = "No se pudo respaldar", subtitulo = mensaje)
            }
        }
    }
}

@Composable
private fun FilaDescartable(onDescartar: () -> Unit, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) { content() }
        IconButton(onClick = onDescartar) { Icon(Icons.Default.Close, contentDescription = "Descartar") }
    }
}
