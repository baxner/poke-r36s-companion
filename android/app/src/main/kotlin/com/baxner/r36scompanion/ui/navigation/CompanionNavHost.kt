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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.ui.ajustes.AjustesScreen
import com.baxner.r36scompanion.ui.ajustes.LicenciasScreen
import com.baxner.r36scompanion.ui.diagnostico.DiagnosticoScreen
import com.baxner.r36scompanion.ui.editor.EditorScreen
import com.baxner.r36scompanion.ui.editor.EditorViewModel
import com.baxner.r36scompanion.ui.editor.GuardarEnConsolaScreen
import com.baxner.r36scompanion.ui.editor.PokemonEditorScreen
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

// El editor (fase 3): tres rutas que comparten una sola EditorViewModel (ver su KDoc) por el
// backStackEntry de RutaEditor, obtenido con navController.getBackStackEntry.
private const val RutaEditor = "editor/{juegoId}"
private fun rutaDeEditor(id: Long) = "editor/$id"
private const val RutaGuardarEnEditor = "editor/{juegoId}/guardar"
private fun rutaDeGuardarEnEditor(id: Long) = "editor/$id/guardar"
private const val RutaPokemonEnEditor = "editor/{juegoId}/pokemon/{origen}/{posicion}/{caja}"
private fun rutaDePokemonEnEditor(juegoId: Long, casilla: Casilla) =
    "editor/$juegoId/pokemon/${casilla.origen.name}/${casilla.posicion}/${casilla.caja ?: -1}"

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

    // La primera vez se arranca directo en la Introducción (no con un navigate desde un
    // LaunchedEffect: si la Activity se recreaba, se volvía a apilar y había que terminarla dos
    // veces, visto en el emulador). Se decide una sola vez por composición raíz.
    val inicio = remember { if (modulos.preferenciasDeLaApp.introduccionVista) Destino.Inicio.ruta else RutaIntroduccion }

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
        NavHost(navController = navController, startDestination = inicio, modifier = Modifier.padding(padding)) {
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
                JuegoScreen(
                    juegoId = juegoId,
                    onBack = { navController.popBackStack() },
                    onEditar = { id -> navController.navigate(rutaDeEditor(id)) },
                )
            }
            composable(
                route = RutaEditor,
                arguments = listOf(navArgument("juegoId") { type = NavType.LongType }),
            ) { entry ->
                val juegoId = entry.arguments?.getLong("juegoId") ?: -1L
                val modulos = modulosActuales()
                val editorViewModel: EditorViewModel = viewModel(entry, key = "editor-$juegoId") { EditorViewModel(modulos, juegoId) }
                EditorScreen(
                    editorViewModel = editorViewModel,
                    textos = modulos.textos,
                    onBack = { navController.popBackStack() },
                    onAbrirPokemon = { casilla -> navController.navigate(rutaDePokemonEnEditor(juegoId, casilla)) },
                    onGuardar = { navController.navigate(rutaDeGuardarEnEditor(juegoId)) },
                )
            }
            composable(
                route = RutaPokemonEnEditor,
                arguments = listOf(
                    navArgument("juegoId") { type = NavType.LongType },
                    navArgument("origen") { type = NavType.StringType },
                    navArgument("posicion") { type = NavType.IntType },
                    navArgument("caja") { type = NavType.IntType },
                ),
            ) { entry ->
                val juegoId = entry.arguments?.getLong("juegoId") ?: -1L
                val parentEntry = remember(entry) { navController.getBackStackEntry(rutaDeEditor(juegoId)) }
                val modulos = modulosActuales()
                val editorViewModel: EditorViewModel = viewModel(parentEntry, key = "editor-$juegoId") { EditorViewModel(modulos, juegoId) }
                val origen = OrigenDeCasilla.valueOf(entry.arguments?.getString("origen") ?: OrigenDeCasilla.EQUIPO.name)
                val posicion = entry.arguments?.getInt("posicion") ?: 0
                val cajaCruda = entry.arguments?.getInt("caja") ?: -1
                val casilla = Casilla(origen = origen, posicion = posicion, caja = cajaCruda.takeIf { it >= 0 })
                PokemonEditorScreen(
                    editorViewModel = editorViewModel,
                    casilla = casilla,
                    textos = modulos.textos,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = RutaGuardarEnEditor,
                arguments = listOf(navArgument("juegoId") { type = NavType.LongType }),
            ) { entry ->
                val juegoId = entry.arguments?.getLong("juegoId") ?: -1L
                val parentEntry = remember(entry) { navController.getBackStackEntry(rutaDeEditor(juegoId)) }
                val modulos = modulosActuales()
                val editorViewModel: EditorViewModel = viewModel(parentEntry, key = "editor-$juegoId") { EditorViewModel(modulos, juegoId) }
                GuardarEnConsolaScreen(editorViewModel = editorViewModel, onBack = { navController.popBackStack() })
            }
            composable(RutaIntroduccion) {
                IntroduccionScreen(
                    onTerminar = {
                        // Abierta desde Ajustes se regresa; si fue el arranque, no hay a dónde
                        // regresar: se va al inicio y la Introducción sale del historial.
                        if (!navController.popBackStack()) {
                            navController.navigate(Destino.Inicio.ruta) {
                                popUpTo(RutaIntroduccion) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
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
