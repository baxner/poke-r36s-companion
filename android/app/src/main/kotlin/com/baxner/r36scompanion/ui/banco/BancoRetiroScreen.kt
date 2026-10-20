package com.baxner.r36scompanion.ui.banco

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
import com.baxner.r36scompanion.data.banco.MovimientoPreparado
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.pokedex.aTexto
import com.baxner.r36scompanion.ui.pokedex.nombreParaMostrar
import com.baxner.r36scompanion.ui.sprites.IconoDeEquipo
import com.baxner.r36scompanion.ui.theme.CompanionColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BancoRetiroScreen(pokemonId: Long, onTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: BancoRetiroViewModel = viewModel(key = "retiro-$pokemonId") { BancoRetiroViewModel(modulos, pokemonId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Retirar del banco") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.etapa == EtapaDeRetiro.VISTA_PREVIA) viewModel.cancelarVistaPrevia() else onTerminar()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") }
                },
            )
        },
    ) { padding ->
        BancoRetiroScreenContent(
            uiState = uiState,
            textos = modulos.textos,
            onElegirJuego = viewModel::elegirJuego,
            onConfirmar = viewModel::confirmar,
            onCancelarVistaPrevia = viewModel::cancelarVistaPrevia,
            onDescartarError = viewModel::descartarError,
            onTerminar = onTerminar,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun BancoRetiroScreenContent(
    uiState: BancoRetiroUiState,
    textos: Textos,
    onElegirJuego: (JuegoRespaldado) -> Unit,
    onConfirmar: () -> Unit,
    onCancelarVistaPrevia: () -> Unit,
    onDescartarError: () -> Unit,
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.cargando) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val pokemon = uiState.pokemon
    if (pokemon == null) {
        EstadoVacio(titulo = "Ya no está en el banco", modifier = modifier.fillMaxSize())
        return
    }

    when (uiState.etapa) {
        EtapaDeRetiro.ELEGIR_JUEGO -> PasoElegirJuegoRetiro(pokemon, uiState.juegosEditables, uiState.error, textos, onElegirJuego, onDescartarError, modifier)
        EtapaDeRetiro.VISTA_PREVIA -> PasoVistaPreviaRetiro(
            pokemon = pokemon,
            juego = uiState.juegoElegido,
            movimiento = uiState.movimiento,
            procesando = uiState.procesando,
            textos = textos,
            onConfirmar = onConfirmar,
            onCancelar = onCancelarVistaPrevia,
            modifier = modifier,
        )
        EtapaDeRetiro.EXITO -> PasoExitoRetiro(onTerminar, modifier)
    }
}

@Composable
private fun CabeceraDePokemonDelBanco(pokemon: PokemonEnBanco, textos: Textos) {
    val nombre = pokemon.especie?.let(textos::especie) ?: "?"
    TarjetaDeSeccion {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconoDeEquipo(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 48.dp, contentDescription = nombre)
            Column {
                Text(pokemon.mote.ifBlank { nombre }, style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary)
                Text("Nv ${pokemon.nivel} · en el banco", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PasoElegirJuegoRetiro(
    pokemon: PokemonEnBanco,
    juegos: List<JuegoRespaldado>,
    error: String?,
    textos: Textos,
    onElegir: (JuegoRespaldado) -> Unit,
    onDescartarError: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CabeceraDePokemonDelBanco(pokemon, textos) }
        item { Text("¿A qué partida lo retiras?", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        error?.let {
            item {
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onDescartarError)) {
                    FilaDeNivel(NivelVisual.ERROR, "No se pudo preparar el retiro", subtitulo = it)
                }
            }
        }
        if (juegos.isEmpty()) {
            item { EstadoVacio(titulo = "No hay partidas editables donde retirarlo") }
        } else {
            items(juegos, key = { it.id }) { juego ->
                TarjetaDeSeccion(modifier = Modifier.clickable { onElegir(juego) }) {
                    Text(juego.nombreParaMostrar(textos), style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PasoVistaPreviaRetiro(
    pokemon: PokemonEnBanco,
    juego: JuegoRespaldado?,
    movimiento: MovimientoPreparado?,
    procesando: Boolean,
    textos: Textos,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier,
) {
    if (juego == null || movimiento == null) return
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabeceraDePokemonDelBanco(pokemon, textos)

        TarjetaDeSeccion(titulo = "Quedará en ${juego.nombreParaMostrar(textos)}") {
            Text("En ${movimiento.casilla.aTexto()}.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary)
            val legalidad = movimiento.legalidadEnDestino
            if (legalidad != null) {
                if (legalidad.valida) {
                    FilaDeNivel(NivelVisual.OK, "Es legal en ese juego")
                } else {
                    FilaDeNivel(NivelVisual.AVISO, "El juego lo marca como ilegal ahí", subtitulo = legalidad.mensajes.joinToString("; ").ifBlank { null })
                }
            }
        }

        FilaDeNivel(NivelVisual.AVISO, "Antes de continuar", subtitulo = TextoConfirmarRetiro)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancelar, enabled = !procesando) { Text("Cancelar") }
            Button(onClick = onConfirmar, enabled = !procesando) {
                if (procesando) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Sí, retirar")
            }
        }
    }
}

@Composable
private fun PasoExitoRetiro(onTerminar: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.OK, "Listo: ya quedó en tu partida")
        Button(onClick = onTerminar) { Text("Terminar") }
    }
}
