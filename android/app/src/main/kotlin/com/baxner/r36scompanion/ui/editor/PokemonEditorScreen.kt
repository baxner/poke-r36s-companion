package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Estadisticas
import com.baxner.r36scompanion.data.editor.OpcionesDePokemon
import com.baxner.r36scompanion.data.editor.PokemonEditable
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.CampoDesplegable
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.sprites.SpriteGrande
import com.baxner.r36scompanion.ui.theme.CompanionColors
import kotlinx.coroutines.launch

/**
 * El editor de un Pokémon (ver el encargo de esta fase, punto 5): cada cambio se prueba al
 * instante con `probar` (sin tocar la partida) para mostrar estadísticas y legalidad antes de
 * aplicarlo. [casilla] identifica cuál Pokémon (equipo o caja); viene de [EquipoTab]/[CajasTab].
 */
@Composable
fun PokemonEditorScreen(editorViewModel: EditorViewModel, casilla: Casilla, textos: Textos, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val estado by editorViewModel.estado.collectAsStateWithLifecycle()
    val sesion = (estado as? EditorViewModel.Estado.Listo)?.sesion
    val alcance = rememberCoroutineScope()

    var opciones by remember(casilla) { mutableStateOf<OpcionesDePokemon?>(null) }
    var borrador by remember(casilla) { mutableStateOf<BorradorDePokemon?>(null) }
    var probado by remember(casilla) { mutableStateOf<PokemonEditable?>(null) }
    var aplicando by remember(casilla) { mutableStateOf(false) }

    // Carga inicial: el detalle de verdad (con su legalidad tal como está guardado) y las opciones del juego para ese Pokémon.
    LaunchedEffect(sesion, casilla) {
        val s = sesion ?: return@LaunchedEffect
        val detalle = s.detalle(casilla)
        opciones = s.opciones(casilla)
        probado = detalle
        borrador = detalle.aBorrador()
    }

    // Cada cambio se prueba de inmediato (sin aplicarlo): así la insignia de legalidad y las estadísticas siempre reflejan el borrador actual.
    LaunchedEffect(sesion, casilla, borrador) {
        val s = sesion ?: return@LaunchedEffect
        val b = borrador ?: return@LaunchedEffect
        probado = s.probar(casilla, b.aCambio())
    }

    PokemonEditorScreenContent(
        borrador = borrador,
        opciones = opciones,
        probado = probado,
        textos = textos,
        aplicando = aplicando,
        onCambiar = { borrador = it },
        onAplicar = {
            val s = sesion
            val b = borrador
            if (s != null && b != null) {
                aplicando = true
                alcance.launch {
                    s.aplicar(casilla, b.aCambio())
                    editorViewModel.notificarCambio()
                    aplicando = false
                    onBack()
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonEditorScreenContent(
    borrador: BorradorDePokemon?,
    opciones: OpcionesDePokemon?,
    probado: PokemonEditable?,
    textos: Textos,
    aplicando: Boolean,
    onCambiar: (BorradorDePokemon) -> Unit,
    onAplicar: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(borrador?.mote?.takeIf { it.isNotBlank() } ?: nombreDeEspecieEditable(borrador?.especie, textos)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar") } },
            )
        },
    ) { padding ->
        if (borrador == null || opciones == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando…", color = CompanionColors.TextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    SpriteGrande(
                        especieId = borrador.especie,
                        shiny = borrador.esShiny,
                        tamano = 96.dp,
                        contentDescription = nombreDeEspecieEditable(borrador.especie, textos),
                    )
                    InsigniaDeLegalidad(probado?.legalidad)
                }
            }

            item {
                TarjetaDeSeccion(titulo = "General") {
                    CampoDesplegable(
                        etiqueta = "Especie",
                        opciones = opciones.especies,
                        seleccionado = borrador.especie ?: opciones.especies.firstOrNull() ?: 0,
                        etiquetaDe = { nombreDeEspecieEditable(it, textos) },
                        onSeleccionar = { onCambiar(borrador.copy(especie = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = borrador.mote,
                        onValueChange = { onCambiar(borrador.copy(mote = it)) },
                        label = { Text("Mote") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CampoNumerico(etiqueta = "Nivel", valor = borrador.nivel, onCambiar = { onCambiar(borrador.copy(nivel = it.coerceIn(1, 100))) })
                    CampoDesplegable(
                        etiqueta = "Naturaleza",
                        opciones = NaturalezasGen3,
                        seleccionado = borrador.naturaleza,
                        etiquetaDe = { nombreDeNaturalezaEditable(it, textos) },
                        onSeleccionar = { onCambiar(borrador.copy(naturaleza = it)) },
                        habilitado = "nature" !in opciones.bloqueados,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Shiny", modifier = Modifier.weight(1f), color = CompanionColors.TextPrimary)
                        Switch(checked = borrador.esShiny, onCheckedChange = { onCambiar(borrador.copy(esShiny = it)) })
                    }
                    CampoDesplegable(
                        etiqueta = "Objeto equipado",
                        opciones = (listOf(0) + opciones.objetos).distinct(),
                        seleccionado = borrador.objeto,
                        etiquetaDe = { nombreDeObjetoEquipable(it, textos) },
                        onSeleccionar = { onCambiar(borrador.copy(objeto = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CampoDesplegable(
                        etiqueta = "Poké Ball",
                        opciones = IdsDePokeBallsGen3,
                        seleccionado = borrador.pokeBall,
                        etiquetaDe = { nombreDePokeBall(it, textos) },
                        onSeleccionar = { onCambiar(borrador.copy(pokeBall = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CampoNumerico(etiqueta = "Amistad", valor = borrador.amistad, onCambiar = { onCambiar(borrador.copy(amistad = it.coerceIn(0, 255))) })
                }
            }

            item {
                TarjetaDeSeccion(titulo = "Movimientos") {
                    val opcionesDeMovimiento = (listOf(0) + opciones.movimientos).distinct()
                    borrador.movimientos.forEachIndexed { indice, movimiento ->
                        CampoDesplegable(
                            etiqueta = "Movimiento ${indice + 1}",
                            opciones = opcionesDeMovimiento,
                            seleccionado = movimiento,
                            etiquetaDe = { nombreDeMovimientoEditable(it, textos) },
                            onSeleccionar = { nuevo ->
                                val nuevos = borrador.movimientos.toMutableList().also { it[indice] = nuevo }
                                onCambiar(borrador.copy(movimientos = nuevos))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                TarjetaDeSeccion(titulo = "IVs") {
                    FilaDeEstadisticas(valores = borrador.ivs, onCambiar = { campo, valor -> onCambiar(borrador.copy(ivs = borrador.ivs.con(campo, limitarIv(valor)))) })
                }
            }

            item {
                TarjetaDeSeccion(titulo = "EVs (total ${borrador.evs.total()}/$EvTotalMaximo)") {
                    FilaDeEstadisticas(
                        valores = borrador.evs,
                        onCambiar = { campo, valor -> onCambiar(borrador.copy(evs = borrador.evs.con(campo, limitarEv(borrador.evs, campo, valor)))) },
                    )
                }
            }

            item {
                Button(onClick = onAplicar, enabled = !aplicando, modifier = Modifier.fillMaxWidth()) {
                    Text(if (aplicando) "Aplicando…" else "Aplicar")
                }
            }
        }
    }
}

private val NaturalezasGen3 = (0..24).toList()

@Composable
private fun FilaDeEstadisticas(valores: Estadisticas, onCambiar: (CampoDeEstadistica, Int) -> Unit) {
    val campos = listOf(
        CampoDeEstadistica.PS to "PS",
        CampoDeEstadistica.ATAQUE to "Ataque",
        CampoDeEstadistica.DEFENSA to "Defensa",
        CampoDeEstadistica.ATAQUE_ESPECIAL to "At. Esp.",
        CampoDeEstadistica.DEFENSA_ESPECIAL to "Def. Esp.",
        CampoDeEstadistica.VELOCIDAD to "Velocidad",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        campos.chunked(3).forEach { fila ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { (campo, etiqueta) ->
                    CampoNumerico(
                        etiqueta = etiqueta,
                        valor = valores.valor(campo),
                        onCambiar = { onCambiar(campo, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CampoNumerico(etiqueta: String, valor: Int, onCambiar: (Int) -> Unit, modifier: Modifier = Modifier) {
    var texto by remember(valor) { mutableStateOf(valor.toString()) }
    OutlinedTextField(
        value = texto,
        onValueChange = { nuevo ->
            texto = nuevo
            nuevo.toIntOrNull()?.let(onCambiar)
        },
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
