package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.OrigenDeCasilla
import com.baxner.r36scompanion.data.editor.RevisionDeGuardado
import com.baxner.r36scompanion.data.respaldos.OrigenDeVersion
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import com.baxner.r36scompanion.ui.editor.EditorViewModel
import com.baxner.r36scompanion.ui.editor.GuardarEnConsolaScreenContent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Revisión visual de "Guardar en la consola": qué va a pasar, avisos y el resultado (ver el encargo de esta fase, puntos 5 y 6). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class GuardarEnConsolaScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun capturar(nombre: String, estadoDeGuardado: EditorViewModel.EstadoDeGuardado, conectada: Boolean, altura: Int = 900) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura.dp) {
                GuardarEnConsolaScreenContent(estadoDeGuardado = estadoDeGuardado, conectada = conectada, onConfirmar = {}, onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/editor_guardar_$nombre.png")
    }

    @Test
    fun listoParaConfirmarSinIlegales() = capturar(
        "listo",
        EditorViewModel.EstadoDeGuardado.ListoParaConfirmar(RevisionDeGuardado(abre = true, pokemonIlegales = emptyList(), problemas = emptyList())),
        conectada = true,
    )

    @Test
    fun listoConIlegalesYSinConsolaConectada() = capturar(
        "ilegales_sin_conexion",
        EditorViewModel.EstadoDeGuardado.ListoParaConfirmar(
            RevisionDeGuardado(
                abre = true,
                pokemonIlegales = listOf(
                    Casilla(OrigenDeCasilla.EQUIPO, 2) to Legalidad(valida = false, mensajes = listOf("Level exceeds max for Encounter Type.")),
                ),
                problemas = emptyList(),
            ),
        ),
        conectada = false,
    )

    @Test
    fun exitoConEstadosApartadosYManuales() = capturar(
        "exito",
        EditorViewModel.EstadoDeGuardado.Exito(
            ResultadoDeEscritura(
                versionEscrita = version(1),
                copiaPrevia = version(2),
                estadosApartados = listOf("/roms/gba/.pr36c/apartados/2026-10-20_10-00-00/Pokemon Edicion Rojo Fuego.state.auto"),
                estadosManuales = listOf("/roms/gba/Pokemon Edicion Rojo Fuego.state1"),
            ),
        ),
        conectada = true,
    )

    private fun version(id: Long) = VersionRespaldada(
        id = id,
        juegoId = 1,
        tipo = TipoDeArchivo.PARTIDA,
        rutaEnConsola = "/roms/gba/Pokemon Edicion Rojo Fuego.srm",
        sha256 = "hash$id",
        tamano = 131_072,
        modificadoEnConsola = 1_792_166_400L,
        respaldadaEn = 1_792_166_400_000L,
        origen = OrigenDeVersion.ESCRITA_POR_LA_APP,
        estadoDePartida = com.baxner.r36scompanion.data.respaldos.EstadoDePartida.CON_DATOS,
        resumen = null,
    )
}
