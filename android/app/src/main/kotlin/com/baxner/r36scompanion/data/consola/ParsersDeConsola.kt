package com.baxner.r36scompanion.data.consola

/*
 * Parsers puros de lo que [SesionDeConsolaJsch] saca de la consola por `exec`: la lista de
 * procesos (para [JuegoAbierto]) y el `retroarch.cfg` (para [AjustesRetroArch]). Nada de SFTP ni
 * de JSch aquí: solo String adentro, datos afuera, para probarlos con JUnit normal.
 */

/**
 * Cómo lanza ArkOS un juego (de `/etc/emulationstation/es_systems.cfg`):
 * `/usr/local/bin/retroarch -L <núcleo>_libretro.so <rom>` (o `retroarch32`, con
 * `~/.config/retroarch32/cores/…`). `ps -eo args` no conserva las comillas de la ROM, así que su
 * nombre (que puede traer espacios) es todo lo que sigue al `.so` del núcleo; por eso el patrón
 * exige que el núcleo no tenga espacios (cierto en la consola: vive bajo `~/.config/.../cores/`) y
 * se queda con el resto de la línea para la ROM. Cualquier otra línea (la del propio `ps`, un
 * `grep`, un `sudo`/`perfmax` que todavía no llegó a lanzar el emulador) simplemente no calza con
 * el patrón y se ignora sola, sin necesidad de una lista negra.
 */
private val PatronProcesoRetroArch = Regex("""^(?:\S*/)?(retroarch(?:32)?)\s+-L\s+(\S+\.so)\s+(.+)$""")

/** Los `retroarch`/`retroarch32` corriendo ahora mismo, de la salida cruda de `ps -eo args`. */
internal fun parsearJuegosAbiertos(salidaPs: String): List<JuegoAbierto> =
    salidaPs.lineSequence()
        .mapNotNull { linea -> PatronProcesoRetroArch.find(linea.trim()) }
        .map { coincidencia ->
            val (emulador, rutaDelNucleo, rom) = coincidencia.destructured
            JuegoAbierto(
                emulador = emulador,
                nucleo = rutaDelNucleo.substringAfterLast('/').removeSuffix("_libretro.so"),
                rom = rom.trim(),
            )
        }
        .toList()

/** Una línea `clave = "valor"` de un `retroarch.cfg` (comentarios y líneas vacías no calzan y se ignoran). */
private val PatronClaveValorCfg = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"(.*)"\s*$""")

/** El contenido crudo de un `retroarch.cfg` a sus pares clave/valor (sin interpretar nada todavía). */
internal fun parsearRetroArchCfg(contenido: String): Map<String, String> =
    contenido.lineSequence()
        .mapNotNull { linea -> PatronClaveValorCfg.find(linea)?.let { it.groupValues[1] to it.groupValues[2] } }
        .toMap()

/** De los pares clave/valor de [parsearRetroArchCfg] a [AjustesRetroArch] ([archivo] es solo para identificarlo). */
internal fun ajustesDesdeMapa(archivo: String, valores: Map<String, String>): AjustesRetroArch = AjustesRetroArch(
    archivo = archivo,
    cargaEstadoAutomatico = valores["savestate_auto_load"]?.let(::booleanoRetroArch),
    guardaEstadoAutomatico = valores["savestate_auto_save"]?.let(::booleanoRetroArch),
    partidasJuntoALaRom = valores["savefiles_in_content_dir"]?.let(::booleanoRetroArch),
    estadosJuntoALaRom = valores["savestates_in_content_dir"]?.let(::booleanoRetroArch),
    carpetaDePartidas = valores["savefile_directory"],
    carpetaDeEstados = valores["savestate_directory"],
    intervaloDeAutoguardado = valores["autosave_interval"]?.toIntOrNull(),
)

private fun booleanoRetroArch(valor: String): Boolean = valor.equals("true", ignoreCase = true)

/**
 * [valor] con un `~/` inicial cambiado por [home] (la consola no expande `~` fuera de una shell, y
 * SFTP no tiene shell): `~/.config/retroarch/saves` con `home = "/home/ark"` da
 * `/home/ark/.config/retroarch/saves`. Lo demás (ya absoluto, o null) se devuelve tal cual.
 */
internal fun expandirHome(valor: String?, home: String): String? =
    valor?.let { if (it.startsWith("~/")) home.trimEnd('/') + it.substring(1) else it }
