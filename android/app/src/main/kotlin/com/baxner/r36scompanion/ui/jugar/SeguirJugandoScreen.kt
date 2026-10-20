package com.baxner.r36scompanion.ui.jugar

import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.data.jugar.AvisoAntesDeJugar
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.theme.CompanionColors

/**
 * "Seguir jugando en el teléfono" (ver el encargo, punto 3 y 5): sin barra inferior (ver
 * `CompanionNavHost`). Pausa el emulador cuando la pantalla pasa a segundo plano (`ON_STOP`) y lo
 * reanuda al volver (`ON_START`), mantiene la pantalla encendida mientras se juega, y pide
 * confirmar antes de salir (atrás o la flecha de la barra) para no perder lo que no se guardó
 * dentro del juego.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeguirJugandoScreen(juegoId: Long, onBack: () -> Unit, modifier: Modifier = Modifier, regresarAlAbrir: Boolean = false) {
    val modulos = modulosActuales()
    val viewModel: SeguirJugandoViewModel = viewModel(key = "jugar-$juegoId-$regresarAlAbrir") {
        SeguirJugandoViewModel(
            juegoId = juegoId,
            repositorio = modulos.repositorioDeRespaldos,
            roms = modulos.romsDelTelefono,
            prestamos = modulos.prestamos,
            sesion = modulos.conexion.sesion,
            abridor = modulos.abridorDeSalas,
            alcanceDeLaApp = modulos.alcanceDeProceso,
            regresarAlAbrir = regresarAlAbrir,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> viewModel.pausar()
                Lifecycle.Event.ON_START -> viewModel.reanudar()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observador)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observador) }
    }

    val jugando = uiState.etapa == EtapaDeSeguirJugando.JUGANDO || uiState.etapa == EtapaDeSeguirJugando.CONFIRMAR_SALIR
    val vista = LocalView.current
    DisposableEffect(jugando) {
        vista.keepScreenOn = jugando
        onDispose { vista.keepScreenOn = false }
    }

    BackHandler(enabled = uiState.etapa == EtapaDeSeguirJugando.JUGANDO) { viewModel.pedirConfirmarSalir() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.nombreDeJuego.ifBlank { "Seguir jugando" }, style = MaterialTheme.typography.titleMedium)
                        if (uiState.etapa == EtapaDeSeguirJugando.JUGANDO || uiState.etapa == EtapaDeSeguirJugando.CONFIRMAR_SALIR) {
                            Text(
                                textoDeUltimoGuardadoEnElTelefono(System.currentTimeMillis(), uiState.ultimoGuardadoEnElTelefono),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (uiState.etapa == EtapaDeSeguirJugando.JUGANDO) viewModel.pedirConfirmarSalir() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
            )
        },
    ) { padding ->
        SeguirJugandoScreenContent(
            uiState = uiState,
            onContinuarApesarDeAvisos = viewModel::continuarApesarDeAvisos,
            onCancelar = onBack,
            onCancelarSalir = viewModel::cancelarSalir,
            onConfirmarSalir = viewModel::confirmarSalir,
            onDespues = onBack,
            onRegresar = viewModel::regresar,
            onRegresarAunqueCambio = viewModel::regresarAunqueCambio,
            onDescartarYQuedarseConLaDeLaConsola = viewModel::descartarYQuedarseConLaDeLaConsola,
            onReintentar = viewModel::reintentarRegresar,
            onDescartarError = viewModel::descartarError,
            onTenerSuperficie = viewModel::pantalla,
            onTeclas = viewModel::teclas,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun SeguirJugandoScreenContent(
    uiState: SeguirJugandoUiState,
    onContinuarApesarDeAvisos: () -> Unit,
    onCancelar: () -> Unit,
    onCancelarSalir: () -> Unit,
    onConfirmarSalir: () -> Unit,
    onDespues: () -> Unit,
    onRegresar: () -> Unit,
    onRegresarAunqueCambio: () -> Unit,
    onDescartarYQuedarseConLaDeLaConsola: () -> Unit,
    onReintentar: () -> Unit,
    onDescartarError: () -> Unit,
    onTenerSuperficie: (Surface?, Int) -> Unit,
    onTeclas: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState.etapa) {
        EtapaDeSeguirJugando.CARGANDO -> CargandoContent(modifier)
        EtapaDeSeguirJugando.SIN_CONSOLA_NI_ROM -> EstadoVacio(
            titulo = "Conecta la consola para copiar el juego",
            subtitulo = "No hay una copia de esta ROM en el teléfono todavía.",
            modifier = modifier.fillMaxSize(),
        )
        EtapaDeSeguirJugando.ERROR_DE_ROM -> EstadoVacio(
            titulo = "No se pudo copiar el juego",
            subtitulo = uiState.mensajeDeErrorDeRom,
            modifier = modifier.fillMaxSize(),
        )
        EtapaDeSeguirJugando.AVISOS -> AvisosAntesDeJugarContent(uiState.avisos, onContinuarApesarDeAvisos, onCancelar, modifier)
        EtapaDeSeguirJugando.JUGANDO, EtapaDeSeguirJugando.CONFIRMAR_SALIR ->
            JugandoContent(uiState, onTenerSuperficie, onTeclas, modifier)
        EtapaDeSeguirJugando.PARTIDA_EN_TELEFONO -> PartidaEnElTelefonoContent(uiState.puedeRegresar, uiState.procesando, onRegresar, onDespues, modifier)
        EtapaDeSeguirJugando.CONFLICTO -> ConflictoAlRegresarContent(onRegresarAunqueCambio, onDescartarYQuedarseConLaDeLaConsola, modifier)
        EtapaDeSeguirJugando.JUEGO_ABIERTO -> MensajeConReintentarContent("Cierra el juego en la consola (sal del juego en RetroArch) y vuelve a intentar.", onReintentar, modifier)
        EtapaDeSeguirJugando.ERROR_DE_RED -> MensajeConReintentarContent(
            uiState.mensajeDeError ?: "Se perdió la conexión con la consola.",
            onReintentar,
            modifier,
        )
        EtapaDeSeguirJugando.REGRESADO -> EstadoVacio(titulo = "Listo: la partida ya está en la consola", modifier = modifier.fillMaxSize())
        EtapaDeSeguirJugando.DESCARTADO -> EstadoVacio(
            titulo = "Te quedaste con la partida de la consola",
            subtitulo = "La del teléfono se guardó aparte: no se perdió.",
            modifier = modifier.fillMaxSize(),
        )
    }

    uiState.mensajeDeError?.takeIf { uiState.etapa == EtapaDeSeguirJugando.JUGANDO }?.let { mensaje ->
        AlertDialog(
            onDismissRequest = onDescartarError,
            confirmButton = { Button(onClick = onDescartarError) { Text("Entendido") } },
            title = { Text("No se pudo llevar la partida") },
            text = { Text(mensaje) },
        )
    }

    if (uiState.etapa == EtapaDeSeguirJugando.CONFIRMAR_SALIR) {
        AlertDialog(
            onDismissRequest = onCancelarSalir,
            confirmButton = {},
            text = { ConfirmarSalirContent(onConfirmar = onConfirmarSalir, onCancelar = onCancelarSalir) },
        )
    }
}

@Composable
private fun CargandoContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun AvisosAntesDeJugarContent(avisos: List<AvisoAntesDeJugar>, onContinuar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Antes de seguir jugando", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        items(avisos) { aviso -> FilaDeNivel(NivelVisual.AVISO, mensajeDeAvisoAntesDeJugar(aviso)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
                Button(onClick = onContinuar) { Text("Jugar de todos modos") }
            }
        }
    }
}

@Composable
private fun JugandoContent(
    uiState: SeguirJugandoUiState,
    onTenerSuperficie: (Surface?, Int) -> Unit,
    onTeclas: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrada = remember { EntradaDeJugadores(numeroDeJugadores = 1) }
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PantallaDelGba(
                jugador = 0,
                alTenerSuperficie = onTenerSuperficie,
                entrada = entrada,
                alCambiarEntradaDeMando = { onTeclas(entrada.mascara(0)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        ControlesEnPantalla(
            alCambiar = { mascara ->
                entrada.tactil(mascara)
                onTeclas(entrada.mascara(0))
            },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
    }
}

@Composable
private fun PartidaEnElTelefonoContent(puedeRegresar: Boolean, procesando: Boolean, onRegresar: () -> Unit, onDespues: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.OK, "La partida está en el teléfono")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (puedeRegresar) {
                Button(onClick = onRegresar, enabled = !procesando) {
                    if (procesando) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Regresar a la consola")
                }
            }
            OutlinedButton(onClick = onDespues) { Text("Después") }
        }
    }
}

@Composable
private fun ConflictoAlRegresarContent(onRegresarAunqueCambio: () -> Unit, onDescartar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.AVISO, "En la consola se jugó mientras tanto")
        TarjetaDeSeccion {
            Text("¿Con cuál partida te quedas?", style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
            Button(onClick = onRegresarAunqueCambio, modifier = Modifier.fillMaxWidth()) { Text("Quedarme con la del teléfono") }
            OutlinedButton(onClick = onDescartar, modifier = Modifier.fillMaxWidth()) { Text("Quedarme con la de la consola") }
            Text(
                "La partida del teléfono se guarda aparte si eliges la de la consola: no se pierde.",
                style = MaterialTheme.typography.bodySmall,
                color = CompanionColors.TextSecondary,
            )
        }
    }
}

/** Compartido con [IntercambioRealScreen] (mismos estados de error antes de empezar a jugar). */
@Composable
fun MensajeConReintentarContent(mensaje: String, onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.ERROR, "No se pudo regresar la partida", subtitulo = mensaje)
        Button(onClick = onReintentar) { Text("Reintentar") }
    }
}

/** El contenido del diálogo "¿Salir?" (aparte del `AlertDialog`: ver AGENTS.md, Roborazzi no asienta diálogos). */
@Composable
fun ConfirmarSalirContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("¿Salir del juego?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Lo que guardaste dentro del juego ya está en el teléfono; lo que no guardaste se pierde.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = onCancelar) { Text("Seguir jugando") }
            Button(onClick = onConfirmar) { Text("Salir") }
        }
    }
}
