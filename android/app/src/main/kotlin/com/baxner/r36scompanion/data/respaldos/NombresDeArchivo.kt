package com.baxner.r36scompanion.data.respaldos

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reconoce el tipo de un archivo de `/roms/<sistema>/` por su nombre y el nombre de su juego
 * (igual al de la ROM, sin extensión). `null` = no es una partida ni un estado (una ROM, un
 * archivo suelto, etc.): se ignora. Ver Respaldos.kt, [TipoDeArchivo].
 *
 * Nombres reales con los que se probó (docs/plan.md, "Partidas encontradas"): paréntesis, comas,
 * espacios, acentos y comillas en el nombre del juego no estorban porque solo se mira el final
 * del nombre de archivo; `.state.auto` se reconoce antes que el patrón general de `.state`, si no
 * "Juego.state.auto" calzaría con ese patrón y perdería el ".auto".
 */
fun tipoYNombreDeArchivo(nombreDeArchivo: String): Pair<TipoDeArchivo, String>? = when {
    nombreDeArchivo.endsWith(SufijoPartida) ->
        TipoDeArchivo.PARTIDA to nombreDeArchivo.removeSuffix(SufijoPartida)

    nombreDeArchivo.endsWith(SufijoEstadoAutomatico) ->
        TipoDeArchivo.ESTADO_AUTOMATICO to nombreDeArchivo.removeSuffix(SufijoEstadoAutomatico)

    else -> PatronEstado.matchEntire(nombreDeArchivo)?.let { TipoDeArchivo.ESTADO to it.groupValues[1] }
}

private const val SufijoPartida = ".srm"
private const val SufijoEstadoAutomatico = ".state.auto"

/** `Nombre.state`, `Nombre.state1`, `Nombre.state12`… (no `.state.auto`: ese ya se descartó arriba). */
private val PatronEstado = Regex("""(.+)\.state\d*""")

/** Quita acentos y pasa a minúsculas, para comparar nombres sin depender de cómo los tecleó quien nombró el archivo. */
fun normalizarNombre(nombre: String): String =
    Normalizer.normalize(nombre, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** El nombre (ya normalizado) menciona "pokemon": hacks como *Pokemon Lets Go Eevee* que el motor no reconoce. */
fun pareceDePokemon(nombre: String): Boolean = "pokemon" in normalizarNombre(nombre)

/** El nombre base de una ROM (quita carpeta y extensión), para comparar con [JuegoRespaldado.nombre] sin importar la extensión real del archivo que reportó `ps` (`.gba`, `.zip`…). */
fun nombreBaseDeRom(rutaOArchivo: String): String =
    rutaOArchivo.substringAfterLast('/').substringBeforeLast('.')

/**
 * Fecha y hora de [Reloj.ahoraMs] en un formato que sirve de nombre de carpeta en cualquier
 * sistema de archivos (sin `:` ni `/`) y que ordena igual que el tiempo real. En UTC a propósito:
 * no depende de la zona horaria del teléfono que corre la app ni de la consola.
 */
fun formatoDeFechaParaCarpeta(ahoraMs: Long): String =
    FormatoDeCarpeta.format(Instant.ofEpochMilli(ahoraMs))

private val FormatoDeCarpeta: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC)

/** El archivo tiene todos sus bytes en `0xFF`: nunca se guardó dentro del juego (docs/plan.md, "Verde Hoja... están vacías"). */
fun esPartidaVacia(bytes: ByteArray): Boolean = bytes.isNotEmpty() && bytes.all { it == 0xFF.toByte() }
