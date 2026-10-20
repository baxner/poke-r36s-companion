package com.baxner.r36scompanion.ui.diagnostico

import com.baxner.r36scompanion.data.consola.AjustesRetroArch
import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.componentes.NivelVisual

/** Un paso del Diagnóstico: título, nivel (✅/⚠️/❌) y, si no es [NivelVisual.OK], qué hacer. */
data class PasoDeDiagnostico(val titulo: String, val nivel: NivelVisual, val detalle: String? = null)

/**
 * Lo que [DiagnosticoViewModel] junta en vivo ([estadoDeConexion], y si hay sesión, lo que se
 * alcanzó a leer de la consola) para que [pasosDeDiagnostico] los traduzca a algo que alguien sin
 * idea de SSH pueda entender. No hace falta el [com.baxner.r36scompanion.data.consola.EstadoDelAnclaje]
 * crudo: mientras [com.baxner.r36scompanion.data.conexion.ConexionConConsola] siga observando el
 * anclaje, cualquier estado más allá de [EstadoDeConexion.SinCable]/[EstadoDeConexion.SinAnclaje]
 * ya implica cable conectado y anclaje activo (en cuanto uno de los dos falta, el observador lo
 * regresa a esos dos estados) — ver [cableConectado] y [anclajeActivo].
 *
 * `null` en los campos que dependen de la consola significa "no se pudo comprobar" (sin sesión, o
 * la lectura falló): el paso queda en [NivelVisual.INFO], no en error, porque el fallo real ya lo
 * cuenta [estadoDeConexion].
 */
data class HechosDeDiagnostico(
    val estadoDeConexion: EstadoDeConexion,
    val carpetaGbaExiste: Boolean? = null,
    val partidasEncontradas: Int? = null,
    val ajustesRetroArch: List<AjustesRetroArch> = emptyList(),
    /** Nombres de juego con la partida vacía pero con un `.state.auto` que sí tiene el avance (ver docs/plan.md, Verde Hoja). */
    val partidasVaciasConEstadoAutomatico: List<String> = emptyList(),
)

private fun EstadoDeConexion.cableConectado(): Boolean = this != EstadoDeConexion.SinCable

private fun EstadoDeConexion.anclajeActivo(): Boolean = cableConectado() && this != EstadoDeConexion.SinAnclaje

/**
 * Traduce [hechos] a la lista de pasos del Diagnóstico (ver el encargo, punto 6, y docs/plan.md,
 * "Introducción y diagnóstico": "Diagnóstico en vivo… que también sirve como '¿por qué no
 * conecta?'"). Pura: nada de SSH ni de Android aquí, para poder probarla con JUnit.
 */
fun pasosDeDiagnostico(hechos: HechosDeDiagnostico): List<PasoDeDiagnostico> {
    val estado = hechos.estadoDeConexion
    val pasos = mutableListOf<PasoDeDiagnostico>()

    pasos += if (estado.cableConectado()) {
        PasoDeDiagnostico("Cable conectado", NivelVisual.OK)
    } else {
        PasoDeDiagnostico("Sin cable", NivelVisual.ERROR, "Conecta la consola al puerto OTG con el cable USB-A y el adaptador OTG.")
    }

    pasos += when {
        estado.anclajeActivo() -> PasoDeDiagnostico("Anclaje de USB activo", NivelVisual.OK)
        estado.cableConectado() -> PasoDeDiagnostico(
            "Anclaje apagado",
            NivelVisual.ERROR,
            "Actívalo en el teléfono (Conexiones → Zona Wi-Fi y Anclaje a red → Anclaje de USB). Con un cable C a C el botón sale en gris.",
        )
        else -> PasoDeDiagnostico("Anclaje apagado", NivelVisual.INFO, "Conecta primero el cable.")
    }

    pasos += pasoDeConsolaEncontrada(estado)
    pasos += pasoDeConexionSsh(estado)

    val conectada = estado is EstadoDeConexion.Conectada
    pasos += when {
        !conectada -> PasoDeDiagnostico("Carpeta de partidas (/roms/gba)", NivelVisual.INFO, "Se revisa al conectar.")
        hechos.carpetaGbaExiste == true -> PasoDeDiagnostico("Carpeta de partidas (/roms/gba)", NivelVisual.OK)
        hechos.carpetaGbaExiste == false -> PasoDeDiagnostico(
            "No existe /roms/gba en la consola",
            NivelVisual.ERROR,
            "¿Es una R36S con ArkOS? Revisa que tenga ROMs de GBA instaladas.",
        )
        else -> PasoDeDiagnostico("Carpeta de partidas (/roms/gba)", NivelVisual.INFO, "No se pudo comprobar.")
    }

    if (conectada && hechos.carpetaGbaExiste == true) {
        val total = hechos.partidasEncontradas
        pasos += if (total != null) {
            PasoDeDiagnostico("Partidas encontradas", NivelVisual.OK, "$total partida${if (total == 1) "" else "s"} de GBA.")
        } else {
            PasoDeDiagnostico("Partidas encontradas", NivelVisual.INFO, "No se pudo comprobar.")
        }

        val algunaConCargaAutomatica = hechos.ajustesRetroArch.any { it.cargaEstadoAutomatico == true }
        pasos += if (hechos.ajustesRetroArch.isEmpty()) {
            PasoDeDiagnostico("Carga automática de estado en RetroArch", NivelVisual.INFO, "No se pudo leer el retroarch.cfg de la consola.")
        } else if (algunaConCargaAutomatica) {
            PasoDeDiagnostico(
                "Tu RetroArch retoma desde el estado automático",
                NivelVisual.AVISO,
                "Al abrir el juego vuelve a donde ibas, no a la última partida guardada: conviene guardar dentro del juego de vez en cuando.",
            )
        } else {
            PasoDeDiagnostico("Carga automática de estado en RetroArch", NivelVisual.OK, "Apagada: tu RetroArch abre desde la partida guardada.")
        }

        pasos += if (hechos.partidasVaciasConEstadoAutomatico.isEmpty()) {
            PasoDeDiagnostico("Partidas vacías con avance solo en un estado", NivelVisual.OK)
        } else {
            PasoDeDiagnostico(
                "Partidas vacías con avance solo en un estado",
                NivelVisual.AVISO,
                "${hechos.partidasVaciasConEstadoAutomatico.joinToString(", ")}: nunca se guardó dentro del juego. " +
                    "Tu avance vive solo en un estado automático. Abre el juego y guarda desde su menú para no arriesgarlo.",
            )
        }
    }

    return pasos
}

private fun pasoDeConsolaEncontrada(estado: EstadoDeConexion): PasoDeDiagnostico = when {
    !estado.anclajeActivo() -> PasoDeDiagnostico("Consola encontrada", NivelVisual.INFO, "Activa el anclaje primero.")
    estado == EstadoDeConexion.NoEncontrada -> PasoDeDiagnostico(
        "No contesta nadie por SSH",
        NivelVisual.ERROR,
        "¿Activaste Options → Enable Remote Services en la consola? Se apaga cada vez que la reinicias.",
    )
    estado == EstadoDeConexion.Buscando -> PasoDeDiagnostico("Buscando la consola…", NivelVisual.INFO)
    else -> PasoDeDiagnostico("Consola encontrada", NivelVisual.OK)
}

private fun pasoDeConexionSsh(estado: EstadoDeConexion): PasoDeDiagnostico = when (estado) {
    is EstadoDeConexion.Conectada -> PasoDeDiagnostico("Conexión SSH", NivelVisual.OK, "Conectado a ${estado.consola.nombreDeEquipo}.")
    is EstadoDeConexion.HuellaDistinta -> PasoDeDiagnostico(
        "Conexión SSH",
        NivelVisual.ERROR,
        "La consola que contestó no es la que tienes fijada. Revisa en Ajustes si de verdad reinstalaste tu consola.",
    )
    EstadoDeConexion.CredencialesIncorrectas -> PasoDeDiagnostico("Conexión SSH", NivelVisual.ERROR, "Usuario o contraseña incorrectos: revísalos en Ajustes.")
    is EstadoDeConexion.Fallo -> PasoDeDiagnostico("Conexión SSH", NivelVisual.ERROR, estado.mensaje)
    EstadoDeConexion.NoEncontrada, EstadoDeConexion.Buscando -> PasoDeDiagnostico("Conexión SSH", NivelVisual.INFO, "Depende del paso anterior.")
    EstadoDeConexion.SinAnclaje, EstadoDeConexion.SinCable -> PasoDeDiagnostico("Conexión SSH", NivelVisual.INFO, "Depende de los pasos anteriores.")
}
