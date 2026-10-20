package com.baxner.r36scompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.baxner.r36scompanion.CompanionApplication
import com.baxner.r36scompanion.Modulos

/**
 * El [Modulos] del proceso, para que cada pantalla arme su ViewModel con
 * `viewModel { MiViewModel(modulos) }` (el constructor con lambda de
 * `androidx.lifecycle.viewmodel.compose.viewModel`, sin librerías de DI: ver el encargo de esta
 * fase). Evita repetir el *cast* a [CompanionApplication] en cada pantalla.
 */
@Composable
fun modulosActuales(): Modulos = (LocalContext.current.applicationContext as CompanionApplication).modulos
