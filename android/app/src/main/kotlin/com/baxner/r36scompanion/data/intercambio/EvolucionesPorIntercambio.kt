package com.baxner.r36scompanion.data.intercambio

/**
 * Las evoluciones por intercambio de la 3.ª generación: lo que el juego haría al recibir el
 * Pokémon. Los intercambios de la app (fase 4) las aplican como `patch` de la llegada
 * (`transfer.preview`/`commit` con `arrivals`), y el motor juzga la legalidad del resultado en
 * el juego de destino.
 *
 * Especies por número de la Pokédex Nacional; objetos con la numeración de la 3.ª generación
 * (la de `heldItem` en las partidas de GBA, comprobada con el motor y `assets/textos/es/objetos-gen3.txt`).
 */
object EvolucionesPorIntercambio {

    /** Qué pasa al llegar: [especie] nueva y si se gasta el objeto que llevaba. */
    data class Evolucion(val desde: Int, val hacia: Int, val objetoNecesario: Int?) {
        val gastaObjeto: Boolean get() = objetoNecesario != null
    }

    const val PiedraEterna = 195
    const val RocaDelRey = 187
    const val RevestimientoMetalico = 199
    const val Escamadragon = 201
    const val Mejora = 218
    const val DienteMarino = 192
    const val EscamaMarina = 193

    private val tabla = listOf(
        Evolucion(desde = 64, hacia = 65, objetoNecesario = null), // Kadabra → Alakazam
        Evolucion(desde = 67, hacia = 68, objetoNecesario = null), // Machoke → Machamp
        Evolucion(desde = 75, hacia = 76, objetoNecesario = null), // Graveler → Golem
        Evolucion(desde = 93, hacia = 94, objetoNecesario = null), // Haunter → Gengar
        Evolucion(desde = 61, hacia = 186, objetoNecesario = RocaDelRey), // Poliwhirl → Politoed
        Evolucion(desde = 79, hacia = 199, objetoNecesario = RocaDelRey), // Slowpoke → Slowking
        Evolucion(desde = 95, hacia = 208, objetoNecesario = RevestimientoMetalico), // Onix → Steelix
        Evolucion(desde = 123, hacia = 212, objetoNecesario = RevestimientoMetalico), // Scyther → Scizor
        Evolucion(desde = 117, hacia = 230, objetoNecesario = Escamadragon), // Seadra → Kingdra
        Evolucion(desde = 137, hacia = 233, objetoNecesario = Mejora), // Porygon → Porygon2
        Evolucion(desde = 366, hacia = 367, objetoNecesario = DienteMarino), // Clamperl → Huntail
        Evolucion(desde = 366, hacia = 368, objetoNecesario = EscamaMarina), // Clamperl → Gorebyss
    )

    /**
     * La evolución que haría el juego al recibir [especie] con [objeto] equipado, o null si no
     * evoluciona: no es de las que evolucionan así, le falta el objeto, lleva la Piedra Eterna
     * (que la impide) o es un huevo.
     */
    fun alRecibir(especie: Int, objeto: Int, esHuevo: Boolean = false): Evolucion? {
        if (esHuevo || objeto == PiedraEterna) return null
        return tabla.firstOrNull { it.desde == especie && (it.objetoNecesario == null || it.objetoNecesario == objeto) }
    }

    /** Si [especie] evoluciona por intercambio con algún objeto, cuál le falta (para avisarlo en la pantalla). */
    fun objetosQueLaHacenEvolucionar(especie: Int): List<Int> = tabla.filter { it.desde == especie }.mapNotNull { it.objetoNecesario }
}
