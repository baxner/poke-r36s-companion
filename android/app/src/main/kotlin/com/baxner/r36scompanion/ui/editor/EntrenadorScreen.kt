package com.baxner.r36scompanion.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.baxner.r36scompanion.data.editor.Entrenador
import com.baxner.r36scompanion.data.editor.Medalla
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.theme.CompanionColors
import kotlinx.coroutines.launch

/** La pestaña Entrenador del editor (ver el encargo de esta fase, punto 5): nombre, dinero y medallas. */
@Composable
fun EntrenadorTab(editorViewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val estado by editorViewModel.estado.collectAsStateWithLifecycle()
    val hayCambios by editorViewModel.hayCambios.collectAsStateWithLifecycle()
    val sesion = (estado as? EditorViewModel.Estado.Listo)?.sesion
    val alcance = rememberCoroutineScope()

    var entrenador by remember { mutableStateOf<Entrenador?>(null) }
    var medallas by remember { mutableStateOf<List<Medalla>?>(null) }
    LaunchedEffect(sesion, hayCambios) {
        entrenador = sesion?.entrenador()
        medallas = sesion?.medallas()
    }

    EntrenadorTabContent(
        entrenador = entrenador,
        medallas = medallas,
        onCambiarNombre = { nombre ->
            alcance.launch {
                sesion?.cambiarNombre(limitarNombre(nombre, entrenador?.maximoDeLetras ?: 0))
                editorViewModel.notificarCambio()
            }
        },
        onCambiarDinero = { dinero ->
            alcance.launch {
                sesion?.cambiarDinero(limitarDinero(dinero))
                editorViewModel.notificarCambio()
            }
        },
        onCambiarMedalla = { indice, ganada ->
            val actuales = medallas?.map { it.ganada }?.toMutableList()
            if (actuales != null) {
                actuales[indice] = ganada
                alcance.launch {
                    sesion?.cambiarMedallas(actuales)
                    editorViewModel.notificarCambio()
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun EntrenadorTabContent(
    entrenador: Entrenador?,
    medallas: List<Medalla>?,
    onCambiarNombre: (String) -> Unit,
    onCambiarDinero: (Long) -> Unit,
    onCambiarMedalla: (indice: Int, ganada: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TarjetaDeSeccion(titulo = "Entrenador") {
                if (entrenador != null) {
                    var nombre by remember(entrenador.nombre) { mutableStateOf(entrenador.nombre) }
                    var dinero by remember(entrenador.dinero) { mutableStateOf((entrenador.dinero ?: 0L).toString()) }
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nuevo ->
                            nombre = limitarNombre(nuevo, entrenador.maximoDeLetras)
                            onCambiarNombre(nombre)
                        },
                        label = { Text("Nombre (máximo ${entrenador.maximoDeLetras} letras)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dinero,
                        onValueChange = { nuevo ->
                            dinero = nuevo
                            nuevo.toLongOrNull()?.let { onCambiarDinero(it) }
                        },
                        label = { Text("Dinero") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("ID ${entrenador.idDeEntrenador}", style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
                } else {
                    Text("Cargando…", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                }
            }
        }
        if (medallas != null && medallas.isNotEmpty()) {
            item {
                TarjetaDeSeccion(titulo = "Medallas") {
                    medallas.forEachIndexed { indice, medalla ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = medalla.ganada, onCheckedChange = { onCambiarMedalla(indice, it) })
                            Text(nombreDeMedalla(medalla.nombreEnIngles), style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
