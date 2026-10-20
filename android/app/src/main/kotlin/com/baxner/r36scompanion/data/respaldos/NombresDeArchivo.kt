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

/**
 * Deduce el juego de Pokémon por el nombre de su archivo, para cuando el motor no puede leer la
 * partida (vacía, o un hack que no reconoce) y no hay [com.baxner.r36scompanion.data.respaldos.ResumenGuardado]
 * del que sacar el nombre real (ver el encargo de esta fase, punto 1: "Rojo Fuego (francés)" o
 * "Verde Hoja"). `null` si no se reconoce ningún patrón: la pantalla muestra el nombre del
 * archivo tal cual, como hoy.
 *
 * El idioma solo se agrega al texto cuando no es español: las partidas del usuario ya vienen tituladas
 * en español ("Pokemon Edicion Rojo Fuego") y marcarlo sería ruido; el francés sí importa porque
 * avisa que es una versión distinta a la que el jugador espera (ver *Rouge Feu (F)* en docs/plan.md).
 */
fun juegoPorNombreDeArchivo(nombreDeArchivo: String): String? {
    val normalizado = normalizarNombre(nombreDeArchivo)
    val juego = JuegosConocidosPorNombre.firstOrNull { (_, patrones) -> patrones.any { it in normalizado } }?.first ?: return null
    val idioma = idiomaDeNombreDeArchivo(normalizado)
    return if (idioma != null && idioma != "español") "$juego ($idioma)" else juego
}

private val JuegosConocidosPorNombre: List<Pair<String, List<String>>> = listOf(
    "Rojo Fuego" to listOf("rojo fuego", "firered", "rouge feu"),
    "Verde Hoja" to listOf("verde hoja", "leafgreen", "vert feuille"),
    "Esmeralda" to listOf("esmeralda", "emerald", "emeraude"),
    "Rubí" to listOf("rubi", "ruby", "rubis"),
    "Zafiro" to listOf("zafiro", "sapphire", "saphir"),
)

/**
 * Por marcas entre paréntesis (`(F)`, `(Fr)`, `(S)`, `(Es)`, `(E)`, `(USA)`) o la palabra "Edicion"
 * de los títulos en español. Los nombres reales traen varias marcas separadas por comas dentro del
 * mismo paréntesis (`(USA, Europe)`, visto en docs/plan.md con *Sound of Thunder, A*), así que se
 * parte cada paréntesis por comas en vez de buscar el paréntesis completo tal cual.
 */
private fun idiomaDeNombreDeArchivo(normalizado: String): String? {
    val marcas = Regex("""\(([^)]*)\)""").findAll(normalizado).flatMap { it.groupValues[1].split(',') }.map { it.trim() }.toSet()
    return when {
        "fr" in marcas -> "francés"
        "es" in marcas -> "español"
        "usa" in marcas -> "inglés"
        "f" in marcas -> "francés"
        "s" in marcas -> "español"
        "e" in marcas -> "inglés"
        "edicion" in normalizado -> "español"
        else -> null
    }
}
