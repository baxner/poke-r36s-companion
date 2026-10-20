package com.baxner.r36scompanion.ui.jugar

import android.content.res.Configuration
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.FilaDeNivel
import com.baxner.r36scompanion.ui.componentes.NivelVisual
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.theme.CompanionColors

/**
 * El intercambio real (ver el encargo, punto 4 y 9): sin barra inferior (ver `CompanionNavHost`).
 * Mismo manejo de lifecycle/pantalla encendida que [SeguirJugandoScreen] (ver su KDoc), pero con
 * dos juegos a la vez: en vertical, uno arriba del otro; en horizontal, lado a lado con los
 * controles encima de los dos (la cruceta y los botones de [ControlesEnPantalla] ya caen en los
 * bordes del contenedor que se les dé).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntercambioRealScreen(juegoA: Long, juegoB: Long, onTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val modulos = modulosActuales()
    val viewModel: IntercambioRealViewModel = viewModel(key = "intercambio-real-$juegoA-$juegoB") {
        IntercambioRealViewModel(
            juegoA = juegoA,
            juegoB = juegoB,
            repositorio = modulos.repositorioDeRespaldos,
            roms = modulos.romsDelTelefono,
            reales = modulos.intercambiosReales,
            sesion = modulos.conexion.sesion,
            abridor = modulos.abridorDeSalas,
            alcanceDeLaApp = modulos.alcanceDeProceso,
            textos = modulos.textos,
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

    val jugando = uiState.etapa == EtapaDeIntercambioReal.JUGANDO || uiState.etapa == EtapaDeIntercambioReal.CONFIRMAR_TERMINAR
    val vista = LocalView.current
    DisposableEffect(jugando) {
        vista.keepScreenOn = jugando
        onDispose { vista.keepScreenOn = false }
    }

    BackHandler(enabled = uiState.etapa == EtapaDeIntercambioReal.JUGANDO) { viewModel.pedirTerminar() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Intercambio real") },
                navigationIcon = {
                    IconButton(onClick = { if (uiState.etapa == EtapaDeIntercambioReal.JUGANDO) viewModel.pedirTerminar() else onTerminar() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
            )
        },
    ) { padding ->
        IntercambioRealScreenContent(
            uiState = uiState,
            onEmpezarAJugar = viewModel::empezarAJugar,
            onReintentar = viewModel::reintentar,
            onControlar = viewModel::controlar,
            onTeclas = viewModel::teclas,
            onTenerSuperficie = viewModel::pantalla,
            onCancelarTerminar = viewModel::cancelarTerminar,
            onConfirmarTerminar = viewModel::confirmarTerminar,
            onEscribir = viewModel::escribir,
            onPedirDescartar = viewModel::pedirDescartar,
            onCancelarDescartar = viewModel::cancelarDescartar,
            onConfirmarDescartar = viewModel::confirmarDescartar,
            onDescartarError = viewModel::descartarError,
            onTerminar = onTerminar,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun IntercambioRealScreenContent(
    uiState: IntercambioRealUiState,
    onEmpezarAJugar: () -> Unit,
    onReintentar: () -> Unit,
    onControlar: (Int) -> Unit,
    onTeclas: (Int, Int) -> Unit,
    onTenerSuperficie: (Int, Surface?, Int) -> Unit,
    onCancelarTerminar: () -> Unit,
    onConfirmarTerminar: () -> Unit,
    onEscribir: () -> Unit,
    onPedirDescartar: () -> Unit,
    onCancelarDescartar: () -> Unit,
    onConfirmarDescartar: () -> Unit,
    onDescartarError: () -> Unit,
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState.etapa) {
        EtapaDeIntercambioReal.CARGANDO -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        EtapaDeIntercambioReal.FALTA_CONSOLA -> EstadoVacio(
            titulo = "Conecta la consola para copiar los dos juegos",
            modifier = modifier.fillMaxSize(),
        )
        EtapaDeIntercambioReal.ERROR_DE_ROM -> MensajeConReintentarContent(uiState.mensajeDeErrorDeRom ?: "No se pudo copiar un juego", onReintentar, modifier)
        EtapaDeIntercambioReal.ERROR_AL_EMPEZAR -> MensajeConReintentarContent(uiState.mensajeDeError ?: "No se pudo empezar el intercambio", onReintentar, modifier)
        EtapaDeIntercambioReal.ERROR_DE_RED -> MensajeConReintentarContent(uiState.mensajeDeError ?: "Se perdió la conexión con la consola", onReintentar, modifier)
        EtapaDeIntercambioReal.INSTRUCCIONES -> InstruccionesDelClubDeCableContent(uiState.necesitaPokedexNacional, onEmpezarAJugar, modifier)
        EtapaDeIntercambioReal.JUGANDO, EtapaDeIntercambioReal.CONFIRMAR_TERMINAR ->
            JugandoDobleContent(uiState, onControlar, onTeclas, onTenerSuperficie, modifier)
        EtapaDeIntercambioReal.REVISION -> RevisionDeCambiosContent(uiState, onEscribir, onPedirDescartar, onDescartarError, modifier)
        EtapaDeIntercambioReal.CONFIRMAR_DESCARTAR -> RevisionDeCambiosContent(uiState, onEscribir, onPedirDescartar, onDescartarError, modifier)
        EtapaDeIntercambioReal.ESCRITO -> ExitoContent("Listo: el intercambio ya quedó escrito en las dos partidas", onTerminar, modifier)
        EtapaDeIntercambioReal.DESCARTADO -> ExitoContent("Descartado: las dos partidas del teléfono se guardaron aparte", onTerminar, modifier)
    }

    if (uiState.etapa == EtapaDeIntercambioReal.CONFIRMAR_TERMINAR) {
        AlertDialog(
            onDismissRequest = onCancelarTerminar,
            confirmButton = {},
            text = { ConfirmarTerminarIntercambioContent(onConfirmar = onConfirmarTerminar, onCancelar = onCancelarTerminar) },
        )
    }

    if (uiState.etapa == EtapaDeIntercambioReal.CONFIRMAR_DESCARTAR) {
        AlertDialog(
            onDismissRequest = onCancelarDescartar,
            confirmButton = {},
            text = { ConfirmarDescartarIntercambioContent(onConfirmar = onConfirmarDescartar, onCancelar = onCancelarDescartar) },
        )
    }
}

@Composable
private fun InstruccionesDelClubDeCableContent(necesitaPokedexNacional: Boolean, onEmpezarAJugar: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Antes de empezar", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        item {
            TarjetaDeSeccion {
                Text("En los dos juegos:", style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                Text("1. Entra a un Centro Pokémon.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                Text("2. Sube al primer piso.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                Text("3. Habla con la encargada del Club de Cable y elige intercambiar.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                Text("4. Al terminar, sal del Club de Cable para que los dos juegos guarden.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
            }
        }
        if (necesitaPokedexNacional) {
            item {
                FilaDeNivel(
                    NivelVisual.AVISO,
                    "Hace falta la Pokédex Nacional",
                    subtitulo = "Rojo Fuego/Verde Hoja necesita la Pokédex Nacional para intercambiar con Rubí/Zafiro/Esmeralda.",
                )
            }
        }
        item { Button(onClick = onEmpezarAJugar, modifier = Modifier.fillMaxWidth()) { Text("Empezar a jugar") } }
    }
}

@Composable
private fun JugandoDobleContent(
    uiState: IntercambioRealUiState,
    onControlar: (Int) -> Unit,
    onTeclas: (Int, Int) -> Unit,
    onTenerSuperficie: (Int, Surface?, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrada = remember { EntradaDeJugadores(numeroDeJugadores = 2) }
    LaunchedEffect(uiState.jugadorControlado) { entrada.controlarConTactil(uiState.jugadorControlado) }

    val horizontal = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val controles: @Composable (Modifier) -> Unit = { mod ->
        ControlesEnPantalla(
            alCambiar = { mascara ->
                entrada.tactil(mascara)
                onTeclas(uiState.jugadorControlado, entrada.mascara(uiState.jugadorControlado))
            },
            modifier = mod,
        )
    }

    if (horizontal) {
        Box(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                PantallaConEtiqueta(0, uiState.nombreA, uiState.vecesGuardadaA, uiState.jugadorControlado == 0, entrada, onTenerSuperficie, onTeclas, onControlar, Modifier.weight(1f).fillMaxHeight())
                PantallaConEtiqueta(1, uiState.nombreB, uiState.vecesGuardadaB, uiState.jugadorControlado == 1, entrada, onTenerSuperficie, onTeclas, onControlar, Modifier.weight(1f).fillMaxHeight())
            }
            controles(Modifier.fillMaxSize())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            PantallaConEtiqueta(0, uiState.nombreA, uiState.vecesGuardadaA, uiState.jugadorControlado == 0, entrada, onTenerSuperficie, onTeclas, onControlar, Modifier.weight(1f).fillMaxWidth())
            PantallaConEtiqueta(1, uiState.nombreB, uiState.vecesGuardadaB, uiState.jugadorControlado == 1, entrada, onTenerSuperficie, onTeclas, onControlar, Modifier.weight(1f).fillMaxWidth())
            controles(Modifier.fillMaxWidth().height(260.dp))
        }
    }
}

@Composable
private fun PantallaConEtiqueta(
    jugador: Int,
    nombre: String,
    vecesGuardada: Int,
    controlado: Boolean,
    entrada: EntradaDeJugadores,
    onTenerSuperficie: (Int, Surface?, Int) -> Unit,
    onTeclas: (Int, Int) -> Unit,
    onControlar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable { onControlar(jugador) }
            .then(if (controlado) Modifier.border(BorderStroke(3.dp, CompanionColors.Primary)) else Modifier),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Jugador ${jugador + 1} · $nombre", style = MaterialTheme.typography.labelMedium, color = CompanionColors.TextPrimary)
            Text(
                if (vecesGuardada == 1) "Guardó 1 vez" else "Guardó $vecesGuardada veces",
                style = MaterialTheme.typography.labelSmall,
                color = CompanionColors.TextSecondary,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PantallaDelGba(
                jugador = jugador,
                alTenerSuperficie = { superficie, escala -> onTenerSuperficie(jugador, superficie, escala) },
                entrada = entrada,
                alCambiarEntradaDeMando = { onTeclas(jugador, entrada.mascara(jugador)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RevisionDeCambiosContent(
    uiState: IntercambioRealUiState,
    onEscribir: () -> Unit,
    onPedirDescartar: () -> Unit,
    onDescartarError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Qué cambió", style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary) }
        item {
            TarjetaDeSeccion(titulo = uiState.nombreA) {
                if (uiState.frasesDeCambiosA.isEmpty()) {
                    Text("Sin cambios.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                } else {
                    uiState.frasesDeCambiosA.forEach { frase -> Text(frase, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary) }
                }
            }
        }
        item {
            TarjetaDeSeccion(titulo = uiState.nombreB) {
                if (uiState.frasesDeCambiosB.isEmpty()) {
                    Text("Sin cambios.", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                } else {
                    uiState.frasesDeCambiosB.forEach { frase -> Text(frase, style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextPrimary) }
                }
            }
        }
        uiState.mensajeDeError?.let { mensaje ->
            item {
                TarjetaDeSeccion {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) { FilaDeNivel(NivelVisual.ERROR, "No se pudo escribir", subtitulo = mensaje) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEscribir, enabled = !uiState.procesando) {
                    if (uiState.procesando) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Escribir en la consola")
                }
                OutlinedButton(onClick = onPedirDescartar, enabled = !uiState.procesando) { Text("Descartar") }
            }
        }
    }
}

@Composable
private fun ExitoContent(mensaje: String, onTerminar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilaDeNivel(NivelVisual.OK, mensaje)
        Button(onClick = onTerminar) { Text("Terminar") }
    }
}

/** Aparte del `AlertDialog` (ver AGENTS.md, Roborazzi no asienta diálogos). */
@Composable
fun ConfirmarTerminarIntercambioContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("¿Terminaste el intercambio?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Lo que se guardó dentro de los juegos ya está en el teléfono; lo que no se guardó se pierde.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = onCancelar) { Text("Seguir jugando") }
            Button(onClick = onConfirmar) { Text("Terminé") }
        }
    }
}

@Composable
fun ConfirmarDescartarIntercambioContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("¿Descartar el intercambio?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Las dos partidas del teléfono se guardan aparte, no se borran; nada se escribe en la consola.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
            Button(onClick = onConfirmar) { Text("Descartar") }
        }
    }
}
