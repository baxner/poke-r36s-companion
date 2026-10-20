package com.baxner.r36scompanion.ui.banco

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.banco.EstadoEnBanco
import com.baxner.r36scompanion.data.banco.PokemonEnBanco
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.sprites.IconoDeEquipo
import com.baxner.r36scompanion.ui.theme.CompanionColors

@Composable
fun BancoScreen(onDepositar: () -> Unit, onRetirar: (Long) -> Unit, onIntercambiar: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: BancoViewModel = viewModel { BancoViewModel(modulos) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BancoScreenContent(
        uiState = uiState,
        textos = modulos.textos,
        onDepositar = onDepositar,
        onRetirar = onRetirar,
        onIntercambiar = onIntercambiar,
        onElegirCaja = viewModel::elegirCaja,
        onConciliar = viewModel::conciliar,
        onDescartarErrorDeConciliar = viewModel::descartarErrorDeConciliar,
        onDescartarResultadoDeConciliar = viewModel::descartarResultadoDeConciliar,
        modifier = modifier,
    )
}

@Composable
fun BancoScreenContent(
    uiState: BancoUiState,
    textos: Textos,
    onDepositar: () -> Unit,
    onRetirar: (Long) -> Unit,
    onIntercambiar: () -> Unit,
    onElegirCaja: (Int) -> Unit,
    onConciliar: (PokemonEnBanco) -> Unit,
    onDescartarErrorDeConciliar: () -> Unit,
    onDescartarResultadoDeConciliar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Banco", style = MaterialTheme.typography.headlineSmall, color = CompanionColors.TextPrimary)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDepositar, enabled = uiState.conectada) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Depositar")
                }
                OutlinedButton(onClick = onIntercambiar, enabled = uiState.conectada) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Intercambiar")
                }
            }
        }

        if (!uiState.conectada) {
            item { FilaDeNivel(NivelVisual.INFO, TextoSinConsolaParaMover) }
        }

        uiState.resultadoDeConciliar?.let { mensaje ->
            item { TarjetaDescartableBanco(onDescartarResultadoDeConciliar) { FilaDeNivel(NivelVisual.OK, mensaje) } }
        }
        uiState.errorDeConciliar?.let { mensaje ->
            item { TarjetaDescartableBanco(onDescartarErrorDeConciliar) { FilaDeNivel(NivelVisual.ERROR, "No se pudo conciliar", subtitulo = mensaje) } }
        }

        if (uiState.pendientes.isNotEmpty()) {
            item { TituloDeSeccionBanco("Pendientes de la última vez") }
            items(uiState.pendientes, key = { it.id }) { pendiente ->
                FilaDePendiente(
                    pendiente = pendiente,
                    textos = textos,
                    conciliando = pendiente.id in uiState.conciliando,
                    puedeConciliar = uiState.conectada,
                    onConciliar = { onConciliar(pendiente) },
                )
            }
        }

        item { TituloDeSeccionBanco("Cajas") }
        item {
            SelectorDeCaja(cajaActual = uiState.cajaActual, cajasDisponibles = uiState.cajasDisponibles, onElegirCaja = onElegirCaja)
        }

        if (uiState.casillas.all { it == null } && uiState.pendientes.isEmpty()) {
            item { EstadoVacio(titulo = "El banco está vacío", subtitulo = "Deposita un Pokémon desde una de tus partidas.") }
        } else {
            item {
                CuadriculaDeCaja(casillas = uiState.casillas, textos = textos, onRetirar = { if (uiState.conectada) onRetirar(it) })
            }
        }
    }
}

@Composable
private fun TituloDeSeccionBanco(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun TarjetaDescartableBanco(onDescartar: () -> Unit, content: @Composable () -> Unit) {
    TarjetaDeSeccion {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) { content() }
            IconButton(onClick = onDescartar) { Icon(Icons.Default.Close, contentDescription = "Descartar") }
        }
    }
}

@Composable
private fun FilaDePendiente(
    pendiente: PokemonEnBanco,
    textos: Textos,
    conciliando: Boolean,
    puedeConciliar: Boolean,
    onConciliar: () -> Unit,
) {
    val nombre = pendiente.especie?.let(textos::especie) ?: "?"
    TarjetaDeSeccion {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconoDeEquipo(especieId = pendiente.especie, shiny = pendiente.esShiny, tamano = 32.dp)
                Column {
                    Text(pendiente.mote.ifBlank { nombre }, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                    Text(
                        if (pendiente.estado == EstadoEnBanco.PENDIENTE_DE_DEPOSITO) "Pendiente de depósito" else "Pendiente de retiro",
                        style = MaterialTheme.typography.bodySmall,
                        color = CompanionColors.Warning,
                    )
                }
            }
            if (conciliando) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                Button(onClick = onConciliar, enabled = puedeConciliar) { Text("Conciliar") }
            }
        }
    }
}

@Composable
private fun SelectorDeCaja(cajaActual: Int, cajasDisponibles: List<Int>, onElegirCaja: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (cajaActual > 0) onElegirCaja(cajaActual - 1) }, enabled = cajaActual > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Caja anterior")
        }
        Text("Caja ${cajaActual + 1} de ${cajasDisponibles.size}", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary)
        IconButton(
            onClick = { if (cajaActual < cajasDisponibles.lastIndex) onElegirCaja(cajaActual + 1) },
            enabled = cajaActual < cajasDisponibles.lastIndex,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Caja siguiente")
        }
    }
}

@Composable
private fun CuadriculaDeCaja(casillas: List<PokemonEnBanco?>, textos: Textos, onRetirar: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(ColumnasDelBanco),
        modifier = Modifier.fillMaxWidth().aspectRatio(ColumnasDelBanco.toFloat() / FilasDelBanco.toFloat()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(casillas) { pokemon -> CasillaDelBanco(pokemon, textos, onRetirar) }
    }
}

@Composable
private fun CasillaDelBanco(pokemon: PokemonEnBanco?, textos: Textos, onRetirar: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(CompanionColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .let { if (pokemon != null) it.clickable { onRetirar(pokemon.id) } else it },
        contentAlignment = Alignment.Center,
    ) {
        if (pokemon != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconoDeEquipo(especieId = pokemon.especie, shiny = pokemon.esShiny, tamano = 28.dp, contentDescription = pokemon.especie?.let(textos::especie))
                Text("Nv ${pokemon.nivel}", style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextSecondary, textAlign = TextAlign.Center)
                if (pokemon.legalidad?.valida == false) {
                    Text("!", style = MaterialTheme.typography.labelSmall, color = CompanionColors.Primary)
                }
            }
        }
    }
}
