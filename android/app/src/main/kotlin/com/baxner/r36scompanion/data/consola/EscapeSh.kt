package com.baxner.r36scompanion.data.consola

/**
 * Escapa [valor] para usarlo como un solo argumento de `sh` en la consola: lo envuelve en comillas
 * simples, que en POSIX no interpretan nada adentro (ni `$`, ni espacios, ni acentos); la única
 * comilla que no se puede meter tal cual es la simple, así que cada una se parte en tres piezas:
 * cierra comillas (`'`), la comilla escapada fuera de ellas (`\'`) y vuelve a abrir (`'`). Nombres
 * reales de la consola del usuario como `Sound of Thunder, A (USA) (En,Fr,De,Es,It).srm` o uno con
 * acentos y comilla simple quedan como un solo argumento, sin que `(`, `!` o `,` disparen nada.
 */
internal fun comillaSimpleSh(valor: String): String = "'" + valor.replace("'", "'\\''") + "'"
