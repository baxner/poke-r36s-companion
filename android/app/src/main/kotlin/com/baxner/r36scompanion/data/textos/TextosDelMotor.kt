package com.baxner.r36scompanion.data.textos

/**
 * Los códigos fijos que da el motor PKHeX (en inglés), dichos en español: motivos por los que un
 * intercambio no se puede, campos que cambian en un Pokémon al llegar, cambios en las partidas y
 * formas de encuentro. Los valores salen de `types.d.ts` del motor (`TransferRefusal`,
 * `TransferField`, `TransferSaveChangeKind`) y de lo que devolvió `encounters.search` con el Rojo
 * Fuego real ("Wild Encounter (FR) Grass", "Static Encounter", "Egg"…). Un código nuevo que no
 * esté aquí se dice de forma genérica, nunca en inglés.
 */
object TextosDelMotor {

    /** Por qué el juego no deja mover a ese Pokémon (`TransferRefusal`). */
    fun rechazo(codigo: String): String = when (codigo) {
        "noRoute" -> "El juego de destino no puede recibirlo (por ejemplo, Rojo Fuego y Verde Hoja necesitan la Pokédex Nacional para intercambiar con Rubí, Zafiro y Esmeralda)"
        "slotLocked" -> "Su casilla está bloqueada en el juego"
        "eggAcrossGenerations" -> "Un huevo no se puede pasar a otra generación"
        "languageMismatch" -> "Los dos juegos son de idiomas que no se pueden intercambiar entre sí"
        "speciesNotInGame" -> "Esa especie no existe en el juego de destino"
        "lastPartyMember" -> "Es el último Pokémon del equipo: el juego no deja quedarse sin ninguno"
        "noRoom" -> "No hay espacio en las cajas del juego de destino"
        "bagFull" -> "No cabe en la mochila el objeto que lleva"
        "conversionFailed" -> "No se pudo convertir al formato del otro juego"
        "notInGame" -> "No se puede tener en el juego de destino"
        "fused" -> "Está fusionado con otro Pokémon"
        "holdingItem" -> "Lleva un objeto que el juego de destino no acepta"
        else -> "El juego no deja moverlo"
    }

    /** Qué cambia en el Pokémon al llegar (`TransferField`). */
    fun campoQueCambia(campo: String): String = when (campo) {
        "species" -> "Cambia de especie (evoluciona)"
        "form" -> "Cambia su forma"
        "heldItem" -> "Cambia o pierde el objeto que lleva"
        "moves" -> "Cambian sus movimientos"
        "metLocation" -> "Cambia el lugar donde se atrapó"
        "metLevel" -> "Cambia el nivel al que se atrapó"
        "ball" -> "Cambia su Poké Ball"
        "friendship" -> "Cambia su amistad"
        "nickname" -> "Cambia su mote"
        "ability" -> "Cambia su habilidad"
        "level" -> "Cambia su nivel"
        "nature" -> "Cambia su naturaleza"
        "gender" -> "Cambia su sexo"
        "shiny" -> "Cambia si es shiny"
        "language" -> "Cambia su idioma"
        "originalTrainer" -> "Cambia su entrenador original"
        "trainerId" -> "Cambia el ID de su entrenador"
        "originGame" -> "Cambia su juego de origen"
        "metDate" -> "Cambia la fecha en que se atrapó"
        else -> "Cambia un dato del Pokémon"
    }

    /** Qué cambia en una de las partidas (`TransferSaveChangeKind`). */
    fun cambioEnPartida(tipo: String): String = when (tipo) {
        "pokedexCaught" -> "Se registra como atrapado en la Pokédex del juego que lo recibe"
        "itemReturned" -> "Se devuelve a la mochila un objeto que llevaba"
        "eventVar" -> "Cambia un dato de un evento del juego"
        else -> "Cambia un dato de la partida"
    }

    /**
     * La forma de encuentro a partir del nombre que da `encounters.search` (`EncounterRow.name`),
     * por ejemplo "Wild Encounter (FR) Grass" → "En la hierba". null para los huevos (no son un
     * lugar: se avisan aparte).
     */
    fun formaDeEncuentro(nombre: String, esHuevo: Boolean): String? {
        if (esHuevo || nombre.equals("Egg", ignoreCase = true)) return null
        val n = nombre.lowercase()
        return when {
            "super rod" in n -> "Pescando con la Supercaña"
            "good rod" in n -> "Pescando con la Caña Buena"
            "old rod" in n -> "Pescando con la Caña Vieja"
            "rock smash" in n -> "Rompiendo rocas"
            "surf" in n -> "Surfeando"
            "grass" in n -> "En la hierba"
            "static" in n -> "Encuentro fijo o regalo"
            "trade" in n -> "Intercambio con un personaje del juego"
            "gift" in n -> "Regalo en el juego"
            "wild" in n -> "Salvaje"
            else -> "Encuentro"
        }
    }
}
