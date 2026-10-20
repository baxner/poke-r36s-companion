package com.baxner.r36scompanion.ui.jugar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.baxner.r36scompanion.emulador.TeclaGba
import com.baxner.r36scompanion.ui.theme.CompanionColors

/*
 * Los controles táctiles del emulador (ver el encargo, punto 2): cruceta y A/B/L/R/START/SELECT,
 * semitransparentes, con varios dedos a la vez (un dedo puede deslizarse de un botón a otro sin
 * soltar). Todo el área de controles tiene un solo detector de toques (no uno por botón): así, en
 * cada evento, se revisa la posición de **todos** los dedos activos contra los rectángulos de cada
 * zona (medidos con [onGloballyPositioned] + [localBoundingBoxOf], relativos a la raíz), en vez de
 * que Compose le asigne cada dedo para siempre al botón donde empezó (que es lo que pasaría con un
 * `pointerInput` por botón).
 */

private val TamanoCruceta = 150.dp
private val TamanoBoton = 64.dp
private val TamanoBotonChico = 52.dp
private const val AlfaDeFondo = 0.35f

@Composable
fun ControlesEnPantalla(alCambiar: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current

    var raiz by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rectCruceta by remember { mutableStateOf(Rect.Zero) }
    var rectA by remember { mutableStateOf(Rect.Zero) }
    var rectB by remember { mutableStateOf(Rect.Zero) }
    var rectL by remember { mutableStateOf(Rect.Zero) }
    var rectR by remember { mutableStateOf(Rect.Zero) }
    var rectStart by remember { mutableStateOf(Rect.Zero) }
    var rectSelect by remember { mutableStateOf(Rect.Zero) }
    var mascaraActual by remember { mutableIntStateOf(0) }

    fun registrar(coordenadas: LayoutCoordinates): Rect = raiz?.localBoundingBoxOf(coordenadas) ?: Rect.Zero

    fun mascaraDeToque(posicion: Offset): Int = when {
        rectCruceta.contains(posicion) -> TeclaGba.mascara(teclasDeCruceta(posicion.x - rectCruceta.left, posicion.y - rectCruceta.top, rectCruceta.width))
        rectA.contains(posicion) -> TeclaGba.A.mascara
        rectB.contains(posicion) -> TeclaGba.B.mascara
        rectL.contains(posicion) -> TeclaGba.L.mascara
        rectR.contains(posicion) -> TeclaGba.R.mascara
        rectStart.contains(posicion) -> TeclaGba.START.mascara
        rectSelect.contains(posicion) -> TeclaGba.SELECT.mascara
        else -> 0
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { raiz = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val posiciones = mutableMapOf<Long, Offset>()
                    while (true) {
                        val evento = awaitPointerEvent()
                        evento.changes.forEach { cambio ->
                            if (cambio.pressed) posiciones[cambio.id.value] = cambio.position else posiciones.remove(cambio.id.value)
                            cambio.consume()
                        }
                        val nuevaMascara = posiciones.values.fold(0) { acumulado, posicion -> acumulado or mascaraDeToque(posicion) }
                        if (nuevaMascara != mascaraActual) {
                            if (nuevaMascara and mascaraActual.inv() != 0) {
                                // Algún botón nuevo quedó apretado: una vibración corta avisa que se registró.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            mascaraActual = nuevaMascara
                            alCambiar(nuevaMascara)
                        }
                    }
                }
            },
    ) {
        CrucetaVisual(
            activas = teclasActivas(mascaraActual, TeclaGba.ARRIBA, TeclaGba.ABAJO, TeclaGba.IZQUIERDA, TeclaGba.DERECHA),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .size(TamanoCruceta)
                .onGloballyPositioned { rectCruceta = registrar(it) },
        )

        // L y R arriba, a los costados: donde caen los índices al sostener el teléfono apaisado.
        BotonDeMando(
            "L",
            activo = mascaraActual and TeclaGba.L.mascara != 0,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(TamanoBotonChico).onGloballyPositioned { rectL = registrar(it) },
        )
        BotonDeMando(
            "R",
            activo = mascaraActual and TeclaGba.R.mascara != 0,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(TamanoBotonChico).onGloballyPositioned { rectR = registrar(it) },
        )

        // A y B, como en un GBA: B abajo-izquierda de A.
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(TamanoBoton * 2.2f)) {
            BotonDeMando(
                "A",
                activo = mascaraActual and TeclaGba.A.mascara != 0,
                modifier = Modifier.align(Alignment.TopEnd).size(TamanoBoton).onGloballyPositioned { rectA = registrar(it) },
            )
            BotonDeMando(
                "B",
                activo = mascaraActual and TeclaGba.B.mascara != 0,
                modifier = Modifier.align(Alignment.BottomStart).size(TamanoBoton).onGloballyPositioned { rectB = registrar(it) },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) {
            BotonChico("SELECT", activo = mascaraActual and TeclaGba.SELECT.mascara != 0, modifier = Modifier.onGloballyPositioned { rectSelect = registrar(it) })
            BotonChico("START", activo = mascaraActual and TeclaGba.START.mascara != 0, modifier = Modifier.onGloballyPositioned { rectStart = registrar(it) })
        }
    }
}

private fun teclasActivas(mascara: Int, vararg teclas: TeclaGba): Set<TeclaGba> = teclas.filter { mascara and it.mascara != 0 }.toSet()

@Composable
private fun CrucetaVisual(activas: Set<TeclaGba>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(CompanionColors.SurfaceHigh.copy(alpha = AlfaDeFondo), RoundedCornerShape(16.dp))) {
        FlechaDeCruceta(Icons.Default.KeyboardArrowUp, activa = TeclaGba.ARRIBA in activas, modifier = Modifier.align(Alignment.TopCenter))
        FlechaDeCruceta(Icons.Default.KeyboardArrowDown, activa = TeclaGba.ABAJO in activas, modifier = Modifier.align(Alignment.BottomCenter))
        FlechaDeCruceta(Icons.AutoMirrored.Filled.KeyboardArrowLeft, activa = TeclaGba.IZQUIERDA in activas, modifier = Modifier.align(Alignment.CenterStart))
        FlechaDeCruceta(Icons.AutoMirrored.Filled.KeyboardArrowRight, activa = TeclaGba.DERECHA in activas, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun FlechaDeCruceta(icono: ImageVector, activa: Boolean, modifier: Modifier = Modifier) {
    Icon(
        icono,
        contentDescription = null,
        tint = if (activa) CompanionColors.Primary else CompanionColors.TextSecondary,
        modifier = modifier.padding(4.dp).size(36.dp),
    )
}

@Composable
private fun BotonDeMando(etiqueta: String, activo: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(colorDeBoton(activo), CircleShape), contentAlignment = Alignment.Center) {
        Text(etiqueta, style = MaterialTheme.typography.titleMedium, color = CompanionColors.TextPrimary)
    }
}

@Composable
private fun BotonChico(etiqueta: String, activo: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 56.dp, height = 28.dp).background(colorDeBoton(activo), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = CompanionColors.TextPrimary)
    }
}

private fun colorDeBoton(activo: Boolean): Color =
    if (activo) CompanionColors.Primary.copy(alpha = AlfaDeFondo + 0.3f) else CompanionColors.SurfaceHigh.copy(alpha = AlfaDeFondo)
