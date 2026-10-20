package com.baxner.r36scompanion.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.data.jugar.EstadoDeIntercambioReal
import com.baxner.r36scompanion.data.jugar.IntercambioReal
import com.baxner.r36scompanion.data.jugar.PartidaPrestada
import com.baxner.r36scompanion.data.respaldos.ResultadoDeRespaldo
import com.baxner.r36scompanion.data.textos.Textos
import com.baxner.r36scompanion.ui.inicio.InicioScreenContent
import com.baxner.r36scompanion.ui.inicio.InicioUiState
import com.baxner.r36scompanion.ui.inicio.organizarJuegos
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión visual de Inicio con datos realistas (ver el encargo, punto 9, y el de la fase 5,
 * punto 5): Rojo Fuego completo, Verde Hoja vacía, un hack, un juego que no es de Pokémon,
 * conectada y sin conexión, y las tarjetas de "seguir jugando"/intercambio real pendiente.
 * Regenerar con `cd android && ./gradlew :app:testDebugUnitTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.MediumPhone)
class InicioScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val textos = Textos { ruta -> File("src/main/assets/$ruta").inputStream() }

    private fun capturar(nombre: String, uiState: InicioUiState, altura: androidx.compose.ui.unit.Dp = 1_000.dp) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompanionScreenshotFrame(height = altura) {
                InicioScreenContent(
                    uiState = uiState,
                    textos = textos,
                    mostrarConfirmarOlvidar = false,
                    onReintentar = {},
                    onActivarAnclaje = {},
                    onPedirConfirmarOlvidar = {},
                    onConfirmarOlvidar = {},
                    onCancelarOlvidar = {},
                    onIrAAjustes = {},
                    onVerDiagnostico = {},
                    onRespaldarTodo = {},
                    onDescartarResultado = {},
                    onDescartarError = {},
                    onAbrirJuego = {},
                    onSeguirJugando = {},
                    onRegresarDeInmediato = {},
                    onContinuarIntercambioReal = { _, _ -> },
                    onEscribirIntercambioReal = {},
                    onDescartarIntercambioReal = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/inicio_$nombre.png")
    }

    // El `height` del frame no basta solo: Robolectric recorta a la altura del dispositivo
    // simulado (MediumPhone) si no se agranda también con `@Config(qualifiers = "+hNNNNdp")`,
    // como en DeviceDetailScreenshotTest de Vigía.
    @Test
    @Config(qualifiers = "+h2200dp")
    fun conectadaConTodo() = capturar(
        "conectada_con_todo",
        InicioUiState(
            estadoDeConexion = EstadoDeConexion.Conectada(
                com.baxner.r36scompanion.data.consola.Consola("10.101.33.156", 22, "SHA256:abc", "ssh-ed25519", "rg351mp"),
                esNueva = false,
            ),
            juegos = organizarJuegos(DatosDeMuestra.juegos),
            resultado = ResultadoDeRespaldo(nuevas = listOf(DatosDeMuestra.historialDeRojoFuego.first()), sinCambios = 4, fallidos = emptyList()),
        ),
        altura = 2_200.dp,
    )

    @Test
    @Config(qualifiers = "+h2200dp")
    fun sinConexionConLaMismaLista() = capturar(
        "sin_conexion",
        InicioUiState(
            estadoDeConexion = EstadoDeConexion.NoEncontrada,
            juegos = organizarJuegos(DatosDeMuestra.juegos),
        ),
        altura = 2_200.dp,
    )

    @Test
    fun vacia() = capturar("vacia", InicioUiState(estadoDeConexion = EstadoDeConexion.SinCable))

    // Fase 5 (ver su encargo, punto 5): una partida prestada y un intercambio real pendiente.
    @Test
    @Config(qualifiers = "+h2400dp")
    fun conPrestamoYConIntercambioRealPendiente() = capturar(
        "con_jugar",
        InicioUiState(
            estadoDeConexion = EstadoDeConexion.Conectada(
                com.baxner.r36scompanion.data.consola.Consola("10.101.33.156", 22, "SHA256:abc", "ssh-ed25519", "rg351mp"),
                esNueva = false,
            ),
            juegos = organizarJuegos(DatosDeMuestra.juegos),
            prestadas = listOf(
                PartidaPrestada(juegoId = DatosDeMuestra.juegoRojoFuego.id, base = "abc", desde = 1_760_000_000_000, actual = "abc", vecesGuardada = 2, ultimaVezGuardada = 1_760_000_500_000),
            ),
            intercambioRealPendiente = IntercambioReal(
                id = 1L,
                juegoA = DatosDeMuestra.juegoVerdeHoja.id,
                baseA = "a",
                juegoB = DatosDeMuestraFase4.juegoEsmeralda.id,
                baseB = "b",
                empezado = 1_760_000_000_000,
                vecesGuardadaA = 1,
                vecesGuardadaB = 2,
                estado = EstadoDeIntercambioReal.POR_ESCRIBIR,
            ),
            nombresDeJuego = DatosDeMuestra.juegos.associateBy { it.id } + (DatosDeMuestraFase4.juegoEsmeralda.id to DatosDeMuestraFase4.juegoEsmeralda),
        ),
        altura = 2_400.dp,
    )
}
