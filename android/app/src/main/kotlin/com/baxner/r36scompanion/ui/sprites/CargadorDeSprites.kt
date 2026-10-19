package com.baxner.r36scompanion.ui.sprites

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Sprites de `assets/sprites/<carpeta>/<número nacional>.png` (`normal`, `shiny`, `icono`; ver
 * AGENTS.md), con caché en memoria para no releer ni decodificar el mismo PNG en cada
 * recomposición o cada vez que la lista de juegos se vuelve a dibujar. Un número sin sprite (o
 * fuera del rango 1..386) cachea `null` igual que uno que sí existe: la pantalla lo resuelve con
 * un círculo y el número (ver [SpriteDeEspecie]), nunca truena.
 *
 * `object`: una sola caché por proceso, como [com.baxner.r36scompanion.data.motor.MotorWebView.obtener].
 */
object CargadorDeSprites {
    private val cache = ConcurrentHashMap<String, Resultado>()

    /** null si no hay sprite para ese número en esa carpeta (no existe el archivo, o falló al decodificarlo). */
    fun obtener(context: Context, carpeta: String, especieId: Int): ImageBitmap? {
        val clave = "$carpeta/$especieId"
        val resultado = cache.getOrPut(clave) { cargar(context, carpeta, especieId) }
        return resultado.bitmap
    }

    private fun cargar(context: Context, carpeta: String, especieId: Int): Resultado = try {
        context.applicationContext.assets.open("sprites/$carpeta/$especieId.png").use { flujo ->
            val bitmap = BitmapFactory.decodeStream(flujo) ?: return Resultado(null)
            Resultado(bitmap.asImageBitmap())
        }
    } catch (_: FileNotFoundException) {
        Resultado(null)
    } catch (_: Exception) {
        // Un PNG corrupto o un assets/ raro: mismo resultado que si no existiera (ver KDoc de la clase).
        Resultado(null)
    }

    /** Envuelve el bitmap para poder cachear "ya lo intenté y no hay" (ImageBitmap? directo no distingue "sin cachear" de "cacheado en null"). */
    private data class Resultado(val bitmap: ImageBitmap?)
}
