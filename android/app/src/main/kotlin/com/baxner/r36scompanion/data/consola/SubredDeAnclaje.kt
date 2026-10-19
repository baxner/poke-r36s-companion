package com.baxner.r36scompanion.data.consola

/*
 * Aritmética de subredes IPv4, pura (nada de `java.net`): de la IP y el prefijo de una interfaz de
 * anclaje a la lista de hosts que vale la pena tocarle el puerto 22. Todo en Long (tratando la
 * dirección como 32 bits sin signo) para no toparse con el signo de Int en octetos >= 128
 * (192.168.x.x, por ejemplo).
 */

/** Las direcciones de [interfaz] que [BuscadorPorAnclaje.buscar] debe probar (ver [direccionesParaBuscar]). */
internal fun direccionesParaBuscar(interfaz: InterfazDeAnclaje): List<String> =
    direccionesParaBuscar(interfaz.direccion, interfaz.prefijo)

/**
 * Las direcciones IPv4 a probar para encontrar la consola alrededor de [direccion]/[prefijo]: como
 * mucho una /24 (si el prefijo real es más corto, una red más grande, se acota a la /24 que
 * contiene a [direccion], para no tardarse de más probando cientos de hosts; si es /25 o más
 * angosto se respeta tal cual, son menos direcciones). Sin la dirección de red, la de broadcast
 * ni la del propio [direccion]. Vacío para /31 y /32 (no hay hueco para un host distinto).
 */
internal fun direccionesParaBuscar(direccion: String, prefijo: Int): List<String> {
    require(prefijo in 0..32) { "prefijo de red inválido: $prefijo" }
    val direccionEntera = ipv4AEntero(direccion)
    val prefijoEfectivo = maxOf(prefijo, 24)
    val bitsDeHost = 32 - prefijoEfectivo
    val totalDeDirecciones = 1L shl bitsDeHost
    if (totalDeDirecciones <= 2) return emptyList()
    val mascaraDeRed = (0xFFFFFFFFL shl bitsDeHost) and 0xFFFFFFFFL
    val red = direccionEntera and mascaraDeRed
    val broadcast = red + totalDeDirecciones - 1
    return (red + 1 until broadcast)
        .filter { it != direccionEntera }
        .map { enteroAIpv4(it) }
}

/** "a.b.c.d" a sus 32 bits, en un Long para no pelearse con el signo de Int. */
internal fun ipv4AEntero(ip: String): Long {
    val partes = ip.split(".")
    require(partes.size == 4) { "no es una dirección IPv4: $ip" }
    return partes.fold(0L) { acumulado, parte ->
        val octeto = parte.toIntOrNull()
        require(octeto != null && octeto in 0..255) { "octeto inválido en $ip: '$parte'" }
        (acumulado shl 8) or octeto.toLong()
    }
}

/** El inverso de [ipv4AEntero]. */
internal fun enteroAIpv4(valor: Long): String {
    val a = (valor shr 24) and 0xFF
    val b = (valor shr 16) and 0xFF
    val c = (valor shr 8) and 0xFF
    val d = valor and 0xFF
    return "$a.$b.$c.$d"
}
