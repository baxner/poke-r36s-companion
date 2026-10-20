package com.baxner.r36scompanion.ui.banco

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.editor.Caja
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
fun BancoDepositoScreen(onTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: BancoDepositoViewModel = viewModel { BancoDepositoViewModel(modulos) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Depositar en el banco") },
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState.etapa) {
                            EtapaDeDeposito.ELEGIR_JUEGO, EtapaDeDeposito.EXITO -> onTerminar()
                            EtapaDeDeposito.ELEGIR_POKEMON -> viewModel.volverAElegirJuego()
                            EtapaDeDeposito.CONFIRMAR -> viewModel.volverAElegirPokemon()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") }
                },
            )
        },
    ) { padding ->
        BancoDepositoScreenContent(
            uiState = uiState,
            textos = modulos.textos,
            onElegirJuego = viewModel::elegirJuego,
            onElegirPokemon = viewModel::elegirPokemon,
            onConfirmar = viewModel::confirmar,
            onCancelarConfirmacion = viewModel::volverAElegirPokemon,
            onDescartarError = viewModel::descartarError,
            onTerminar = onTerminar,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun BancoDepositoScreenContent(
    uiState: BancoDepositoUiState,
    textos: Textos,
    onElegirJuego: (JuegoRespaldado) -> Unit,
    onElegirPokemon: (Casilla, ResumenDePokemon) -> Unit,
    onConfirmar: () -> Unit,
    onCancelarConfirmacion: () -> Unit,
    onDescartarError: () -> Unit,
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.cargando) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    when (uiState.etapa) {
        EtapaDeDeposito.ELEGIR_JUEGO -> PasoElegirJuegoDeposito(uiState.juegosEditables, textos, onElegirJuego, modifier)
        EtapaDeDeposito.ELEGIR_POKEMON -> PasoElegirPokemonDeposito(uiState.cajas, textos, onElegirPokemon, modifier)
        EtapaDeDeposito.CONFIRMAR -> PasoConfirmarDeposito(
            juego = uiState.juegoElegido,
            pokemon = uiState.pokemonElegido,
            textos = textos,
            procesando = uiState.procesando,
            error = uiState.error,
            onConfirmar = onConfirmar,
            onCancelar = onCancelarConfirmacion,
            onDescartarError = onDescartarError,
            modifier = modifier,
        )
        EtapaDeDeposito.EXITO -> PasoExitoDeposito(onTerminar, modifier)
    }
}

@Composable
private fun PasoElegirJuegoDeposito(juegos: List<JuegoRespaldado>, textos: Textos, onElegir: (JuegoRespaldado) -> Unit, modifier: Modifier) {
    if (juegos.isEmpty()) {
        EstadoVacio(
            titulo = "No hay partidas editables",
            subtitulo = "Respalda primero una partida de Pokémon que el motor reconozca.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("¿De qué partida sale el Pokémon?", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        items(juegos, key = { it.id }) { juego ->
            TarjetaDeSeccion(modifier = Modifier.clickable { onElegir(juego) }) {
                Text(juego.nombreParaMostrar(textos), style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun PasoElegirPokemonDeposito(cajas: List<Caja>, textos: Textos, onElegir: (Casilla, ResumenDePokemon) -> Unit, modifier: Modifier) {
    val cajasConPokemon = cajas.filter { it.pokemon.isNotEmpty() }
    if (cajasConPokemon.isEmpty()) {
        EstadoVacio(titulo = "No hay Pokémon en las cajas de esta partida", modifier = modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("¿Cuál Pokémon depositas? (solo de tus cajas)", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        items(cajasConPokemon, key = { it.numero }) { caja ->
            TarjetaDeSeccion(titulo = caja.nombre.ifBlank { "Caja ${caja.numero + 1}" }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    caja.pokemon.forEach { pokemon ->
                        PokemonDeCajaParaDepositar(pokemon, textos, onClick = { onElegir(pokemon.casilla, pokemon) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PokemonDeCajaParaDepositar(pokemon: ResumenDePokemon, textos: Textos, onClick: () -> Unit) {
    val nombre = pokemon.especie?.let(textos::especie) ?: "?"
    Column(modifier = Modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconoDeEquipo(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 40.dp, contentDescription = nombre)
        Text(pokemon.mote.ifBlank { nombre }, style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextPrimary, maxLines = 1)
        Text("Nv ${pokemon.nivel}", style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary)
    }
}

@Composable
private fun PasoConfirmarDeposito(
    juego: JuegoRespaldado?,
    pokemon: ResumenDePokemon?,
    textos: Textos,
    procesando: Boolean,
    error: String?,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    onDescartarError: () -> Unit,
    modifier: Modifier,
) {
    if (pokemon == null || juego == null) return
    val nombre = pokemon.especie?.let(textos::especie) ?: "?"

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TarjetaDeSeccion {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconoDeEquipo(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 48.dp, contentDescription = nombre)
                Column {
                    Text(pokemon.mote.ifBlank { nombre }, style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary)
                    Text("Nv ${pokemon.nivel} · ${juego.nombreParaMostrar(textos)}", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                }
            }
        }

        FilaDeNivel(NivelVisual.AVISO, "Antes de continuar", subtitulo = TextoConfirmarDeposito)

        error?.let {
            TarjetaDeSeccion {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    FilaDeNivel(NivelVisual.ERROR, "No se pudo depositar", subtitulo = it)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancelar, enabled = !procesando) { Text("Cancelar") }
            Button(onClick = { if (error != null) onDescartarError(); onConfirmar() }, enabled = !procesando) {
                if (procesando) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Sí, depositar")
            }
        }
    }
}

@Composable
private fun PasoExitoDeposito(onTerminar: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.OK, "Listo: ya quedó en el banco del teléfono")
        Button(onClick = onTerminar) { Text("Terminar") }
    }
}
