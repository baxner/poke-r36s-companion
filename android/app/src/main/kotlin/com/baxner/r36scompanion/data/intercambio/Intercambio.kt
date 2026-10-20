package com.baxner.r36scompanion.data.intercambio

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.editor.Casilla
import com.baxner.r36scompanion.data.editor.Legalidad
import com.baxner.r36scompanion.data.editor.ResumenDePokemon

/*
 * Contrato del intercambio rápido entre dos partidas del usuario (fase 4). Lo escribe Opus. Usa el
 * intercambio del motor (ver docs/plan.md, "Diseño técnico de las fases 3 a 5"):
 * `transfer.open(partidaB)` con la partida A cargada → `transfer.preview(oferta)` (qué cambia,
 * qué rechaza el juego, legalidad en el destino) → `transfer.commit(oferta)`, que devuelve las
 * dos partidas exportadas. La evolución por intercambio va como `arrivals[].patch` (especie
 * nueva y, si gasta objeto, `heldItem` 0), decidida con [EvolucionesPorIntercambio].
 *
 * Probado con el Rojo Fuego real contra una copia de sí mismo (2026-10-19): con A cargada,
 * `transfer.open(bytesDeB, "B.srm")` → `transfer.partnerBoxes()` lista equipo y cajas de B →
 * cada llegada con evolución lleva `{at: <casilla de origen>, bytes: transfer.export(at, "mine").bytes
 * (el Pokémon ya en el formato de B, 100 bytes), patch: {species: 65}}` → `transfer.preview` dio
 * Kadabra → Alakazam legal, con `saveChanges` `pokedexCaught` en el que recibe → `transfer.commit`
 * devolvió `save` y `partner` (131 072 bytes cada una) y `arrived` con la casilla de caja donde cayó
 * cada uno (la primera libre); en B el Alakazam quedó legal.
 *
 * Escribir en la consola son DOS partidas: [EjecutorDeIntercambio.escribirEnLaConsola] escribe A y
 * luego B con `EscritorDePartidas` (cada una con su respaldo previo y su estado automático
 * apartado). **Si B falla después de escribir A, se restaura A** a su versión de antes del
 * intercambio, para que ningún Pokémon quede duplicado ni perdido; si hasta eso falla, el error
 * lo dice con las dos versiones de antes, que siguen en el historial para restaurarlas a mano.
 */

enum class Direccion { DE_A_HACIA_B, DE_B_HACIA_A }

data class OfertaDeIntercambio(
    /** Casillas de la partida A que van a B. */
    val envia: List<Casilla>,
    /** Casillas de la partida B que van a A. */
    val recibe: List<Casilla>,
    /** Aplicar las evoluciones por intercambio, como haría el juego. */
    val evolucionar: Boolean = true,
)

data class LlegadaPrevista(
    val direccion: Direccion,
    val sale: ResumenDePokemon,
    /** Cómo llega (con la especie ya evolucionada si toca). */
    val llega: ResumenDePokemon,
    val evolucion: EvolucionesPorIntercambio.Evolucion?,
    /** Lo que cambia en el Pokémon, ya en español (`TextosDelMotor.campoQueCambia`). */
    val cambios: List<String>,
    /** Lo que cambia en las partidas (Pokédex, objetos devueltos, eventos), ya en español (`TextosDelMotor.cambioEnPartida`). */
    val cambiosEnPartidas: List<String>,
    /** La revisión de legalidad en el juego de destino. */
    val legalidad: Legalidad,
)

/** [motivo] ya en español (`TextosDelMotor.rechazo`). */
data class Rechazo(val direccion: Direccion, val casilla: Casilla, val motivo: String)

data class VistaPreviaDeIntercambio(val llegadas: List<LlegadaPrevista>, val rechazos: List<Rechazo>) {
    val sePuede: Boolean get() = rechazos.isEmpty() && llegadas.isNotEmpty()
}

data class PartidasIntercambiadas(val partidaA: ByteArray, val partidaB: ByteArray)

interface Intercambios {
    /** Qué pasaría, sin cambiar nada. */
    suspend fun vistaPrevia(a: ByteArray, nombreA: String, b: ByteArray, nombreB: String, oferta: OfertaDeIntercambio): VistaPreviaDeIntercambio

    /** Hace el intercambio en memoria y devuelve las dos partidas nuevas (ya comprobadas con el motor). */
    suspend fun intercambiar(a: ByteArray, nombreA: String, b: ByteArray, nombreB: String, oferta: OfertaDeIntercambio): PartidasIntercambiadas
}

sealed class ErrorDeIntercambio(mensaje: String) : Exception(mensaje) {
    /** B no se pudo escribir, pero A se regresó a como estaba: no cambió nada en la consola. */
    class SeDeshizo(causa: Throwable) : ErrorDeIntercambio("no se pudo escribir la segunda partida; la primera se regresó a como estaba: ${causa.message}")

    /** B falló y A no se pudo regresar sola: hay que restaurar A desde el historial. */
    class QuedoAMedias(val juegoA: Long, val versionPreviaDeA: Long, causa: Throwable) :
        ErrorDeIntercambio("el intercambio quedó a medias: restaura la versión $versionPreviaDeA de la primera partida (${causa.message})")
}

interface EjecutorDeIntercambio {
    /**
     * Escribe las dos partidas en la consola con las reglas de arriba. [baseA] y [baseB] son los
     * SHA-256 de las versiones de las que salió el intercambio (si la consola tiene otra cosa, no
     * escribe nada).
     */
    suspend fun escribirEnLaConsola(sesion: SesionDeConsola, juegoA: Long, baseA: String, juegoB: Long, baseB: String, partidas: PartidasIntercambiadas)
}
