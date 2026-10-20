package com.baxner.r36scompanion.ui.juego

import com.baxner.r36scompanion.data.respaldos.OrigenDeVersion
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.VersionRespaldada
import com.baxner.r36scompanion.data.textos.Textos

/** Una versión del historial, ya con su etiqueta de origen y sus frases de "qué cambió" (ver el encargo, punto 4). */
data class ItemDeHistorial(val version: VersionRespaldada, val etiquetaDeOrigen: String, val cambios: List<String>)

fun etiquetaDeOrigen(origen: OrigenDeVersion): String = when (origen) {
    OrigenDeVersion.CONSOLA -> "Respaldo"
    OrigenDeVersion.ANTES_DE_ESCRIBIR -> "Antes de que la app escribiera"
    OrigenDeVersion.ESCRITA_POR_LA_APP -> "Escrita por la app"
}

/** "Estado automático" / "Estado": para la sección aparte de estados (ver el encargo, punto 4). [TipoDeArchivo.PARTIDA] no aplica ahí. */
fun etiquetaDeTipoDeEstado(tipo: TipoDeArchivo): String = when (tipo) {
    TipoDeArchivo.ESTADO_AUTOMATICO -> "Estado automático"
    TipoDeArchivo.ESTADO -> "Estado"
    TipoDeArchivo.PARTIDA -> "Partida"
}

/**
 * El historial de la partida (solo [com.baxner.r36scompanion.data.respaldos.TipoDeArchivo.PARTIDA]:
 * las de estados van aparte, sin "qué cambió", ver el encargo, punto 4), de [versiones] ya
 * ordenadas de la más reciente a la más vieja (como las da
 * [com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos.versiones]): cada una se compara
 * contra la siguiente de la lista (la anterior en el tiempo). Pura aparte de [Textos] (que tampoco
 * depende de Android).
 */
fun historialDePartida(versiones: List<VersionRespaldada>, textos: Textos): List<ItemDeHistorial> =
    versiones.mapIndexed { indice, version ->
        val anterior = versiones.getOrNull(indice + 1)
        ItemDeHistorial(
            version = version,
            etiquetaDeOrigen = etiquetaDeOrigen(version.origen),
            cambios = frasesDeCambios(anterior?.resumen, version.resumen, textos),
        )
    }
