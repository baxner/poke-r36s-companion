package com.baxner.r36scompanion.ui.juego

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.theme.CompanionColors

/** Lo que pinta el detalle de un juego, juntando los [kotlinx.coroutines.flow.StateFlow] sueltos de [JuegoViewModel]. */
data class JuegoUiState(
    val juego: JuegoRespaldado? = null,
    val historialDePartida: List<ItemDeHistorial> = emptyList(),
    val versionesDeEstados: List<VersionRespaldada> = emptyList(),
    val conectada: Boolean = false,
    val restaurando: Boolean = false,
    val resultadoDeRestaurar: ResultadoDeEscritura? = null,
    val errorDeRestaurar: String? = null,
    val errorDeExportar: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuegoScreen(juegoId: Long, onBack: () -> Unit, onEditar: (Long) -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: JuegoViewModel = viewModel(key = "juego-$juegoId") { JuegoViewModel(modulos, juegoId) }
    val context = LocalContext.current

    val juego by viewModel.juego.collectAsStateWithLifecycle()
    val historialDePartida by viewModel.historialDePartida.collectAsStateWithLifecycle()
    val versionesDeEstados by viewModel.versionesDeEstados.collectAsStateWithLifecycle()
    val conectada by viewModel.conectada.collectAsStateWithLifecycle()
    val restaurando by viewModel.restaurando.collectAsStateWithLifecycle()
    val resultadoDeRestaurar by viewModel.resultadoDeRestaurar.collectAsStateWithLifecycle()
    val errorDeRestaurar by viewModel.errorDeRestaurar.collectAsStateWithLifecycle()
    val errorDeExportar by viewModel.errorDeExportar.collectAsStateWithLifecycle()

    var versionAConfirmar by remember { mutableStateOf<Long?>(null) }

    val lanzadorExportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.let(viewModel::exportar) }
    }

    val uiState = JuegoUiState(
        juego = juego,
        historialDePartida = historialDePartida,
        versionesDeEstados = versionesDeEstados,
        conectada = conectada,
        restaurando = restaurando,
        resultadoDeRestaurar = resultadoDeRestaurar,
        errorDeRestaurar = errorDeRestaurar,
        errorDeExportar = errorDeExportar,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(juego?.let { nombreDeJuego(it, modulos.textos) } ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
                actions = {
                    IconButton(onClick = { lanzadorExportar.launch("${juego?.nombre ?: "partida"}.zip") }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exportar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorDeJuego(juego?.resumen?.version).aColorCompose(),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        JuegoScreenContent(
            uiState = uiState,
            textos = modulos.textos,
            versionAConfirmar = versionAConfirmar,
            onEditar = { onEditar(juegoId) },
            onPedirRestaurar = { versionAConfirmar = it },
            onConfirmarRestaurar = {
                versionAConfirmar?.let(viewModel::restaurar)
                versionAConfirmar = null
            },
            onCancelarRestaurar = { versionAConfirmar = null },
            onDescartarResultado = viewModel::descartarResultadoDeRestaurar,
            onDescartarErrorDeRestaurar = viewModel::descartarErrorDeRestaurar,
            onDescartarErrorDeExportar = viewModel::descartarErrorDeExportar,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun JuegoScreenContent(
    uiState: JuegoUiState,
    textos: Textos,
    versionAConfirmar: Long?,
    onEditar: () -> Unit,
    onPedirRestaurar: (Long) -> Unit,
    onConfirmarRestaurar: () -> Unit,
    onCancelarRestaurar: () -> Unit,
    onDescartarResultado: () -> Unit,
    onDescartarErrorDeRestaurar: () -> Unit,
    onDescartarErrorDeExportar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val juego = uiState.juego
    val resumen = juego?.resumen
    val color = colorDeJuego(resumen?.version).aColorCompose()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (resumen != null) {
            item {
                Column(modifier = Modifier.fillMaxWidth().background(color, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(16.dp)) {
                    CabeceraDeJuego(resumen)
                }
            }
            item { EquipoDeJuego(resumen.equipo, textos) }
            if (juego?.editable == true) {
                item {
                    Button(onClick = onEditar, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Editar")
                    }
                }
            }
        }

        uiState.resultadoDeRestaurar?.let { resultado ->
            item { TarjetaDeResultadoDeRestaurar(resultado, onDescartarResultado) }
        }
        uiState.errorDeRestaurar?.let { mensaje ->
            item { TarjetaDescartable(onDescartarErrorDeRestaurar) { FilaDeNivel(nivel = NivelVisual.ERROR, titulo = "No se pudo restaurar", subtitulo = mensaje) } }
        }
        uiState.errorDeExportar?.let { mensaje ->
            item { TarjetaDescartable(onDescartarErrorDeExportar) { FilaDeNivel(nivel = NivelVisual.ERROR, titulo = "No se pudo exportar", subtitulo = mensaje) } }
        }

        if (uiState.historialDePartida.isNotEmpty()) {
            item { TituloDeSeccionJuego("Historial de la partida") }
            items(uiState.historialDePartida, key = { it.version.id }) { item ->
                FilaDeHistorialDePartida(
                    item = item,
                    esLaMasVieja = item == uiState.historialDePartida.last(),
                    puedeRestaurar = uiState.conectada && !uiState.restaurando,
                    onRestaurar = { onPedirRestaurar(item.version.id) },
                )
            }
        }

        if (uiState.versionesDeEstados.isNotEmpty()) {
            item { TituloDeSeccionJuego("Historial de estados") }
            items(uiState.versionesDeEstados, key = { it.id }) { version -> FilaDeEstado(version) }
        }
    }

    if (versionAConfirmar != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onCancelarRestaurar,
            confirmButton = {},
            text = { ConfirmarRestaurarContent(onConfirmar = onConfirmarRestaurar, onCancelar = onCancelarRestaurar) },
        )
    }
}

@Composable
private fun TituloDeSeccionJuego(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, color = CompanionColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun TarjetaDeResultadoDeRestaurar(resultado: ResultadoDeEscritura, onDescartar: () -> Unit) {
    TarjetaDescartable(onDescartar) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilaDeNivel(nivel = NivelVisual.OK, titulo = "Listo: se restauró esa versión en la consola")
            if (resultado.estadosApartados.isNotEmpty()) {
                Text(
                    "Se apartó el estado automático: ${resultado.estadosApartados.joinToString { it.substringAfterLast('/') }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CompanionColors.TextSecondary,
                )
            }
            if (resultado.estadosManuales.isNotEmpty()) {
                FilaDeNivel(
                    nivel = NivelVisual.AVISO,
                    titulo = "Todavía hay estados guardados a mano",
                    subtitulo = "Si cargas uno de estos en la consola perderás lo restaurado: " +
                        resultado.estadosManuales.joinToString { it.substringAfterLast('/') },
                )
            }
        }
    }
}

@Composable
private fun TarjetaDescartable(onDescartar: () -> Unit, content: @Composable () -> Unit) {
    TarjetaDeSeccion {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) { content() }
            IconButton(onClick = onDescartar) { Icon(Icons.Default.Close, contentDescription = "Descartar") }
        }
    }
}
