package com.baxner.r36scompanion.ui.intercambio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.ResumenDePokemon
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.pokedex.nombreParaMostrar
import com.baxner.r36scompanion.ui.sprites.IconoDeEquipo
import com.baxner.r36scompanion.ui.theme.CompanionColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntercambioScreen(onTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: IntercambioViewModel = viewModel { IntercambioViewModel(modulos) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Intercambio rápido") },
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState.etapa) {
                            EtapaDeIntercambio.ELEGIR_JUEGO_A, EtapaDeIntercambio.EXITO -> onTerminar()
                            EtapaDeIntercambio.ELEGIR_JUEGO_B -> viewModel.volverAElegirJuegoA()
                            EtapaDeIntercambio.ELEGIR_POKEMON -> viewModel.volverAElegirJuegoB()
                            EtapaDeIntercambio.VISTA_PREVIA -> viewModel.volverAElegirPokemon()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") }
                },
            )
        },
    ) { padding ->
        IntercambioScreenContent(
            uiState = uiState,
            textos = modulos.textos,
            onElegirJuegoA = viewModel::elegirJuegoA,
            onElegirJuegoB = viewModel::elegirJuegoB,
            onAlternarSeleccionA = viewModel::alternarSeleccionA,
            onAlternarSeleccionB = viewModel::alternarSeleccionB,
            onAlternarEvolucionar = viewModel::alternarEvolucionar,
            onVerVistaPrevia = viewModel::verVistaPrevia,
            onConfirmar = viewModel::confirmar,
            onDescartarError = viewModel::descartarError,
            onTerminar = onTerminar,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun IntercambioScreenContent(
    uiState: IntercambioUiState,
    textos: Textos,
    onElegirJuegoA: (JuegoRespaldado) -> Unit,
    onElegirJuegoB: (JuegoRespaldado) -> Unit,
    onAlternarSeleccionA: (Casilla) -> Unit,
    onAlternarSeleccionB: (Casilla) -> Unit,
    onAlternarEvolucionar: () -> Unit,
    onVerVistaPrevia: () -> Unit,
    onConfirmar: () -> Unit,
    onDescartarError: () -> Unit,
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.cargando) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    when (uiState.etapa) {
        EtapaDeIntercambio.ELEGIR_JUEGO_A -> PasoElegirJuego(
            titulo = "¿Cuál es la primera partida?",
            juegos = uiState.juegosEditables,
            textos = textos,
            error = uiState.error,
            onElegir = onElegirJuegoA,
            modifier = modifier,
        )
        EtapaDeIntercambio.ELEGIR_JUEGO_B -> PasoElegirJuego(
            titulo = "¿Y la segunda?",
            juegos = uiState.juegosEditables.filter { it.id != uiState.juegoA?.id },
            textos = textos,
            error = uiState.error,
            onElegir = onElegirJuegoB,
            modifier = modifier,
        )
        EtapaDeIntercambio.ELEGIR_POKEMON -> PasoElegirPokemonDeIntercambio(uiState, textos, onAlternarSeleccionA, onAlternarSeleccionB, onAlternarEvolucionar, onVerVistaPrevia, modifier)
        EtapaDeIntercambio.VISTA_PREVIA -> PasoVistaPreviaDeIntercambio(uiState, textos, onConfirmar, onDescartarError, modifier)
        EtapaDeIntercambio.EXITO -> PasoExitoDeIntercambio(onTerminar, modifier)
    }
}

@Composable
private fun PasoElegirJuego(titulo: String, juegos: List<JuegoRespaldado>, textos: Textos, error: String?, onElegir: (JuegoRespaldado) -> Unit, modifier: Modifier) {
    if (juegos.isEmpty()) {
        EstadoVacio(titulo = "No hay suficientes partidas editables", subtitulo = "Hace falta al menos dos.", modifier = modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(titulo, style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        error?.let { item { FilaDeNivel(NivelVisual.ERROR, "No se pudo continuar", subtitulo = it) } }
        items(juegos, key = { it.id }) { juego ->
            TarjetaDeSeccion(modifier = Modifier.clickable { onElegir(juego) }) {
                Text(juego.nombreParaMostrar(textos), style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun PasoElegirPokemonDeIntercambio(
    uiState: IntercambioUiState,
    textos: Textos,
    onAlternarSeleccionA: (Casilla) -> Unit,
    onAlternarSeleccionB: (Casilla) -> Unit,
    onAlternarEvolucionar: () -> Unit,
    onVerVistaPrevia: () -> Unit,
    modifier: Modifier,
) {
    val nombreA = uiState.juegoA?.nombreParaMostrar(textos) ?: "A"
    val nombreB = uiState.juegoB?.nombreParaMostrar(textos) ?: "B"
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Evolucionar como en el juego", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
                Switch(checked = uiState.evolucionar, onCheckedChange = { onAlternarEvolucionar() })
            }
        }
        item { Text("Lo que manda $nombreA", style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary) }
        items(uiState.pokemonDeA, key = { "a-${it.casilla}" }) { pokemon ->
            FilaDeSeleccionDePokemon(pokemon, textos, seleccionado = pokemon.casilla in uiState.seleccionA, onClick = { onAlternarSeleccionA(pokemon.casilla) })
        }
        item { Text("Lo que manda $nombreB", style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary) }
        items(uiState.pokemonDeB, key = { "b-${it.casilla}" }) { pokemon ->
            FilaDeSeleccionDePokemon(pokemon, textos, seleccionado = pokemon.casilla in uiState.seleccionB, onClick = { onAlternarSeleccionB(pokemon.casilla) })
        }
        uiState.error?.let { item { FilaDeNivel(NivelVisual.ERROR, "No se pudo preparar la vista previa", subtitulo = it) } }
        item { Button(onClick = onVerVistaPrevia, enabled = uiState.sePuedeVerVistaPrevia) { Text("Ver vista previa") } }
    }
}

@Composable
private fun FilaDeSeleccionDePokemon(pokemon: ResumenDePokemon, textos: Textos, seleccionado: Boolean, onClick: () -> Unit) {
    val nombre = pokemon.especie?.let(textos::especie) ?: "?"
    TarjetaDeSeccion(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = seleccionado, onCheckedChange = { onClick() })
            IconoDeEquipo(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 32.dp, contentDescription = nombre)
            Column {
                Text(pokemon.mote.ifBlank { nombre }, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                Text("Nv ${pokemon.nivel}", style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PasoVistaPreviaDeIntercambio(
    uiState: IntercambioUiState,
    textos: Textos,
    onConfirmar: () -> Unit,
    onDescartarError: () -> Unit,
    modifier: Modifier,
) {
    val vistaPrevia = uiState.vistaPrevia
    val nombreA = uiState.juegoA?.nombreParaMostrar(textos) ?: "A"
    val nombreB = uiState.juegoB?.nombreParaMostrar(textos) ?: "B"
    if (vistaPrevia == null) {
        EstadoVacio(titulo = "No hay vista previa", modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Vista previa", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }

        if (vistaPrevia.llegadas.isEmpty()) {
            item { Text("Nada que mover todavía.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary) }
        } else {
            items(vistaPrevia.llegadas) { llegada ->
                TarjetaDeSeccion {
                    Text(llegada.direccion.aTexto(nombreA, nombreB), style = MaterialTheme.typography.labelMedium, color = CompanionColors.TextSecondary)
                    Text(llegada.textoDeEspecie(textos), style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                    FilaDeNivel(if (llegada.legalidad.valida) NivelVisual.OK else NivelVisual.AVISO, llegada.textoDeLegalidad())
                }
            }
        }

        if (vistaPrevia.rechazos.isNotEmpty()) {
            item { Text("No se pueden mover", style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary) }
            items(vistaPrevia.rechazos) { rechazo ->
                FilaDeNivel(NivelVisual.ERROR, rechazo.textoDeRechazo())
            }
        }

        uiState.error?.let { item { FilaDeNivel(NivelVisual.ERROR, "No se pudo intercambiar", subtitulo = it) } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { uiState.error?.let { onDescartarError() }; onConfirmar() }, enabled = !uiState.procesando && vistaPrevia.sePuede) {
                    if (uiState.procesando) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Sí, intercambiar")
                }
            }
        }
    }
}

@Composable
private fun PasoExitoDeIntercambio(onTerminar: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.OK, "Listo: el intercambio ya quedó escrito en las dos partidas")
        Button(onClick = onTerminar) { Text("Terminar") }
    }
}
