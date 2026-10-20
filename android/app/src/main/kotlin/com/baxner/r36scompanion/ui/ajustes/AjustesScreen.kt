package com.baxner.r36scompanion.ui.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baxner.r36scompanion.ui.componentes.TarjetaDeSeccion
import com.baxner.r36scompanion.ui.modulosActuales
import com.baxner.r36scompanion.ui.theme.CompanionColors

private data class FilaDeNavegacion(val titulo: String, val subtitulo: String, val onClick: () -> Unit)

@Composable
fun AjustesScreen(
    onAbrirIntroduccion: () -> Unit,
    onAbrirDiagnostico: () -> Unit,
    onAbrirLicencias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modulos = modulosActuales()
    val viewModel: AjustesViewModel = viewModel { AjustesViewModel(modulos) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mostrarConfirmarOlvidar by remember { mutableStateOf(false) }

    AjustesScreenContent(
        uiState = uiState,
        mostrarConfirmarOlvidar = mostrarConfirmarOlvidar,
        onUsuarioChange = viewModel::actualizarUsuario,
        onContrasenaChange = viewModel::actualizarContrasena,
        onRespaldoAutomaticoChange = viewModel::cambiarRespaldoAutomatico,
        onPedirOlvidarConsola = { mostrarConfirmarOlvidar = true },
        onConfirmarOlvidarConsola = {
            mostrarConfirmarOlvidar = false
            viewModel.olvidarConsola()
        },
        onCancelarOlvidarConsola = { mostrarConfirmarOlvidar = false },
        onAbrirIntroduccion = onAbrirIntroduccion,
        onAbrirDiagnostico = onAbrirDiagnostico,
        onAbrirLicencias = onAbrirLicencias,
        modifier = modifier,
    )
}

@Composable
fun AjustesScreenContent(
    uiState: AjustesUiState,
    mostrarConfirmarOlvidar: Boolean,
    onUsuarioChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onRespaldoAutomaticoChange: (Boolean) -> Unit,
    onPedirOlvidarConsola: () -> Unit,
    onConfirmarOlvidarConsola: () -> Unit,
    onCancelarOlvidarConsola: () -> Unit,
    onAbrirIntroduccion: () -> Unit,
    onAbrirDiagnostico: () -> Unit,
    onAbrirLicencias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filas = listOf(
        FilaDeNavegacion("Introducción", "Cómo conectar la consola, paso a paso", onAbrirIntroduccion),
        FilaDeNavegacion("Diagnóstico", "¿Por qué no conecta?", onAbrirDiagnostico),
        FilaDeNavegacion("Licencias", "PKHeX, las tablas de nombres y los sprites", onAbrirLicencias),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TarjetaDeSeccion(titulo = "Tu consola") {
                if (uiState.nombreDeConsola != null) {
                    Text(uiState.nombreDeConsola, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                    Text(
                        uiState.huellaFijada.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CompanionColors.TextSecondary,
                    )
                    OutlinedButton(onClick = onPedirOlvidarConsola) { Text("Olvidar esta consola") }
                } else {
                    Text("Ninguna consola fijada todavía", style = MaterialTheme.typography.bodyMedium, color = CompanionColors.TextSecondary)
                }
            }
        }

        item {
            TarjetaDeSeccion(titulo = "Usuario y contraseña") {
                OutlinedTextField(
                    value = uiState.usuario,
                    onValueChange = onUsuarioChange,
                    label = { Text("Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CampoDeContrasena(valor = uiState.contrasena, onValueChange = onContrasenaChange)
            }
        }

        item {
            TarjetaDeSeccion {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Respaldar al conectar", style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                        Text("Respalda todo solo (nada más lee la consola)", style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
                    }
                    Switch(checked = uiState.respaldoAutomatico, onCheckedChange = onRespaldoAutomaticoChange)
                }
            }
        }

        items(filas) { fila -> FilaDeAjuste(fila) }
    }

    if (mostrarConfirmarOlvidar) {
        AlertDialog(
            onDismissRequest = onCancelarOlvidarConsola,
            confirmButton = {},
            text = { ConfirmarOlvidarDesdeAjustesContent(onConfirmar = onConfirmarOlvidarConsola, onCancelar = onCancelarOlvidarConsola) },
        )
    }
}

@Composable
private fun CampoDeContrasena(valor: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = onValueChange,
        label = { Text("Contraseña") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Ocultar" else "Mostrar")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilaDeAjuste(fila: FilaDeNavegacion) {
    TarjetaDeSeccion(modifier = Modifier.clickable(onClick = fila.onClick)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fila.titulo, style = MaterialTheme.typography.bodyLarge, color = CompanionColors.TextPrimary)
                Text(fila.subtitulo, style = MaterialTheme.typography.bodySmall, color = CompanionColors.TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CompanionColors.TextSecondary)
        }
    }
}

/** El contenido del diálogo "¿Olvidar esta consola?" (aparte del `AlertDialog`: ver AGENTS.md, Roborazzi no asienta diálogos). */
@Composable
fun ConfirmarOlvidarDesdeAjustesContent(onConfirmar: () -> Unit, onCancelar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("¿Olvidar esta consola?", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("La próxima consola que conteste por este cable se guardará como si fuera la primera vez.")
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancelar) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.height(18.dp))
                Text("Cancelar")
            }
            Button(onClick = onConfirmar) { Text("Olvidar") }
        }
    }
}
