package com.baxner.r36scompanion.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baxner.r36scompanion.ui.ajustes.AjustesScreen
import com.baxner.r36scompanion.ui.ajustes.LicenciasScreen
import com.baxner.r36scompanion.ui.diagnostico.DiagnosticoScreen
import com.baxner.r36scompanion.ui.inicio.InicioScreen
import com.baxner.r36scompanion.ui.introduccion.IntroduccionScreen
import com.baxner.r36scompanion.ui.juego.JuegoScreen
import com.baxner.r36scompanion.ui.modulosActuales

private sealed class Destino(val ruta: String, val etiqueta: String, val icono: ImageVector) {
    data object Inicio : Destino("inicio", "Inicio", Icons.Default.Home)
    data object Ajustes : Destino("ajustes", "Ajustes", Icons.Default.Settings)
}

private val destinosDePrimerNivel = listOf(Destino.Inicio, Destino.Ajustes)

private const val RutaIntroduccion = "introduccion"
private const val RutaDiagnostico = "diagnostico"
private const val RutaLicencias = "ajustes/licencias"
private const val RutaJuego = "juego/{juegoId}"
private fun rutaDeJuego(id: Long) = "juego/$id"

/**
 * Raíz de la app (ver el encargo, punto 2): barra inferior de Inicio/Ajustes (Pokédex y Banco
 * llegan en la fase 4) + el detalle de un juego, Introducción, Diagnóstico y Licencias, sin barra.
 * La Introducción se abre sola la primera vez (ver
 * [com.baxner.r36scompanion.data.preferencias.PreferenciasDeLaApp.introduccionVista]).
 */
@Composable
fun CompanionApp() {
    val modulos = modulosActuales()
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        if (!modulos.preferenciasDeLaApp.introduccionVista) {
            navController.navigate(RutaIntroduccion)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val rutaActual = backStackEntry?.destination?.route
    val mostrarBarraInferior = destinosDePrimerNivel.any { it.ruta == rutaActual }

    Scaffold(
        bottomBar = {
            if (mostrarBarraInferior) {
                NavigationBar {
                    destinosDePrimerNivel.forEach { destino ->
                        NavigationBarItem(
                            selected = rutaActual == destino.ruta,
                            onClick = {
                                if (rutaActual != destino.ruta) {
                                    navController.navigate(destino.ruta) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(Destino.Inicio.ruta) { saveState = true }
                                    }
                                }
                            },
                            icon = { Icon(destino.icono, contentDescription = null) },
                            label = { Text(destino.etiqueta) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = Destino.Inicio.ruta, modifier = Modifier.padding(padding)) {
            composable(Destino.Inicio.ruta) {
                InicioScreen(
                    onAbrirJuego = { id -> navController.navigate(rutaDeJuego(id)) },
                    onIrAAjustes = {
                        navController.navigate(Destino.Ajustes.ruta) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Destino.Inicio.ruta) { saveState = true }
                        }
                    },
                    onVerDiagnostico = { navController.navigate(RutaDiagnostico) },
                )
            }
            composable(Destino.Ajustes.ruta) {
                AjustesScreen(
                    onAbrirIntroduccion = { navController.navigate(RutaIntroduccion) },
                    onAbrirDiagnostico = { navController.navigate(RutaDiagnostico) },
                    onAbrirLicencias = { navController.navigate(RutaLicencias) },
                )
            }
            composable(
                route = RutaJuego,
                arguments = listOf(navArgument("juegoId") { type = NavType.LongType }),
            ) { entry ->
                val juegoId = entry.arguments?.getLong("juegoId") ?: -1L
                JuegoScreen(juegoId = juegoId, onBack = { navController.popBackStack() })
            }
            composable(RutaIntroduccion) {
                IntroduccionScreen(onTerminar = { navController.popBackStack() })
            }
            composable(RutaDiagnostico) {
                DiagnosticoScreen(onBack = { navController.popBackStack() })
            }
            composable(RutaLicencias) {
                LicenciasScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
