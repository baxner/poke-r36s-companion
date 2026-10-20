package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.baxner.r36scompanion.data.editor.Bolsillo
import com.baxner.r36scompanion.data.editor.ObjetoEnMochila
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.componentes.EstadoVacio
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.theme.CompanionColors
import kotlinx.coroutines.launch

/** La pestaña Mochila del editor (ver el encargo de esta fase, punto 5): un bolsillo por `TarjetaDeSeccion`, cantidad editable y "Agregar objeto". */
@Composable
fun MochilaTab(editorViewModel: EditorViewModel, textos: Textos, modifier: Modifier = Modifier) {
    val estado by editorViewModel.estado.collectAsStateWithLifecycle()
    val hayCambios by editorViewModel.hayCambios.collectAsStateWithLifecycle()
    val sesion = (estado as? EditorViewModel.Estado.Listo)?.sesion
    val alcance = rememberCoroutineScope()

    var mochila by remember { mutableStateOf<List<Bolsillo>?>(null) }
    LaunchedEffect(sesion, hayCambios) { mochila = sesion?.mochila() }

    MochilaTabContent(
        mochila = mochila,
        textos = textos,
        onCambiarCantidad = { bolsillo, id, cantidad ->
            alcance.launch {
                sesion?.cambiarObjeto(bolsillo, id, cantidad)
                editorViewModel.notificarCambio()
            }
        },
        onAgregar = { bolsillo, id ->
            alcance.launch {
                sesion?.cambiarObjeto(bolsillo, id, 1)
                editorViewModel.notificarCambio()
            }
        },
    )
}

@Composable
fun MochilaTabContent(
    mochila: List<Bolsillo>?,
    textos: Textos,
    onCambiarCantidad: (bolsillo: String, id: Int, cantidad: Int) -> Unit,
    onAgregar: (bolsillo: String, id: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mochila != null && mochila.all { it.objetos.isEmpty() && it.agregables.isEmpty() }) {
        EstadoVacio("Mochila vacía", modifier = modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(mochila.orEmpty(), key = { it.nombre }) { bolsillo ->
            TarjetaDeSeccion(titulo = nombreDeBolsillo(bolsillo.nombre)) {
                if (bolsillo.objetos.isEmpty()) {
                    Text("Sin objetos", style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
                }
                bolsillo.objetos.forEach { objeto ->
                    FilaDeObjetoDeMochila(
                        objeto = objeto,
                        textos = textos,
                        onCambiarCantidad = { nuevaCantidad -> onCambiarCantidad(bolsillo.nombre, objeto.id, nuevaCantidad) },
                    )
                }
                if (bolsillo.agregables.isNotEmpty()) {
                    SelectorDeAgregar(idsAgregables = bolsillo.agregables, textos = textos, onAgregar = { id -> onAgregar(bolsillo.nombre, id) })
                }
            }
        }
    }
}

@Composable
private fun FilaDeObjetoDeMochila(objeto: ObjetoEnMochila, textos: Textos, onCambiarCantidad: (Int) -> Unit) {
    var texto by remember(objeto.id, objeto.cantidad) { mutableStateOf(objeto.cantidad.toString()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            nombreDeObjetoDeMochila(objeto.id, textos),
            style = MaterialTheme.typography.bodyMedium,
            color = CompanionColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = texto,
            onValueChange = { nuevo ->
                texto = nuevo
                nuevo.toIntOrNull()?.let { onCambiarCantidad(it.coerceIn(0, objeto.maximo)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(88.dp),
        )
    }
}

@Composable
private fun SelectorDeAgregar(idsAgregables: List<Int>, textos: Textos, onAgregar: (Int) -> Unit) {
    var seleccionado by remember(idsAgregables) { mutableStateOf(idsAgregables.first()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.baxner.r36scompanion.ui.componentes.CampoDesplegable(
            etiqueta = "Agregar objeto",
            opciones = idsAgregables,
            seleccionado = seleccionado,
            etiquetaDe = { nombreDeObjetoDeMochila(it, textos) },
            onSeleccionar = { seleccionado = it },
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onAgregar(seleccionado) }) { Text("Agregar") }
    }
}
