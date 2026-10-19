package com.baxner.r36scompanion.data.textos

import android.content.Context
import java.io.InputStream

/**
 * Nombres en español (el de los juegos) por número, con las tablas de PKHeX de `assets/textos/es/` (ver su
 * `ORIGEN.md`). El motor solo da nombres en inglés: la app nunca los muestra y traduce por id.
 * Si un id no está en la tabla, devuelve null para que la pantalla decida qué poner.
 */
class Textos(private val abrir: (String) -> InputStream) {

    private val especies by lazy { tabla("especies.txt") }
    private val movimientos by lazy { tabla("movimientos.txt") }
    private val objetosGen3 by lazy { tabla("objetos-gen3.txt") }
    private val objetos by lazy { tabla("objetos.txt") }
    private val habilidades by lazy { tabla("habilidades.txt") }
    private val naturalezas by lazy { tabla("naturalezas.txt") }
    private val tipos by lazy { tabla("tipos.txt") }
    private val juegos by lazy { tabla("juegos.txt") }
    private val lugaresGen3 by lazy { tabla("lugares-gen3.txt") }

    /** Por número de la Pokédex Nacional (1 = Bulbasaur). */
    fun especie(id: Int): String? = especies.nombre(id).takeIf { id > 0 }

    fun movimiento(id: Int): String? = movimientos.nombre(id).takeIf { id > 0 }

    /** Numeración de la 3.ª generación: la de los objetos equipados y la mochila en las partidas de GBA. */
    fun objetoGen3(id: Int): String? = objetosGen3.nombre(id).takeIf { id > 0 }

    /** Numeración de la 4.ª generación en adelante (la de `catalog.names` del motor). */
    fun objeto(id: Int): String? = objetos.nombre(id).takeIf { id > 0 }

    fun habilidad(id: Int): String? = habilidades.nombre(id).takeIf { id > 0 }

    fun naturaleza(id: Int): String? = naturalezas.nombre(id)

    fun tipo(id: Int): String? = tipos.nombre(id)

    /** Por `GameVersion` de PKHeX (4 = Rojo Fuego). */
    fun juego(versionId: Int): String? = juegos.nombre(versionId).takeIf { versionId > 0 }

    /** El nombre en español del juego a partir del nombre en inglés que da el motor (`FireRed`). */
    fun juego(versionEnIngles: String): String = VersionesGen3[versionEnIngles]?.let(::juego) ?: versionEnIngles

    fun lugarGen3(id: Int): String? = lugaresGen3.nombre(id)

    private fun tabla(archivo: String) = Tabla(abrir("textos/es/$archivo").bufferedReader(Charsets.UTF_8).use { it.readLines() })

    private class Tabla(private val lineas: List<String>) {
        fun nombre(id: Int): String? = lineas.getOrNull(id)?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        /** Nombre en inglés que da el motor → `GameVersion` de PKHeX. */
        private val VersionesGen3 = mapOf("Sapphire" to 1, "Ruby" to 2, "Emerald" to 3, "FireRed" to 4, "LeafGreen" to 5)

        fun de(context: Context): Textos {
            val assets = context.applicationContext.assets
            return Textos { ruta -> assets.open(ruta) }
        }
    }
}
