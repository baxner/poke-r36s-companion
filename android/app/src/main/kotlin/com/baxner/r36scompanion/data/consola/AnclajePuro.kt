package com.baxner.r36scompanion.data.consola

/*
 * Lo de Anclaje que no toca Android: a qué le decimos "interfaz de anclaje" y cómo juntar eso con
 * si hay cable para armar un EstadoDelAnclaje. Aparte de AnclajeAndroid para probarlo con JUnit
 * normal, sin Robolectric ni permisos de red de verdad.
 */

/**
 * Lo que hace falta de una interfaz de red para decidir si es de anclaje, sin [java.net.NetworkInterface]
 * (así se puede probar con datos de mentira, sin red real ni Robolectric).
 */
internal data class InfoDeInterfazDeRed(val nombre: String, val arriba: Boolean, val direccionesIpv4: List<DireccionIpv4>) {
    /** IP + prefijo, sin [java.net.InterfaceAddress] (mismo motivo que la clase de arriba). */
    data class DireccionIpv4(val direccion: String, val prefijo: Int)
}

private val PrefijosDeAnclaje = listOf("rndis", "ncm", "usb")

/**
 * `rndis0`, `ncm0`, `usb0`… son de anclaje; `rmnet_usb0` (la radio de algunos equipos) no, aunque
 * traiga "usb" en el nombre — por eso cada prefijo se compara con `startsWith` y nunca con
 * `nombre.contains("usb")`, que sí lo agarraría.
 */
internal fun esNombreDeInterfazDeAnclaje(nombre: String): Boolean = PrefijosDeAnclaje.any { nombre.startsWith(it) }

/** Las [InterfazDeAnclaje] de [interfaces]: arriba, de anclaje, con IPv4 (una por cada dirección, si tuviera varias). */
internal fun interfacesDeAnclajeDesde(interfaces: List<InfoDeInterfazDeRed>): List<InterfazDeAnclaje> =
    interfaces
        .filter { it.arriba && esNombreDeInterfazDeAnclaje(it.nombre) }
        .flatMap { info -> info.direccionesIpv4.map { InterfazDeAnclaje(info.nombre, it.direccion, it.prefijo) } }

/** [EstadoDelAnclaje] a partir de si hay cable conectado y qué interfaces de anclaje hay ahora mismo. */
internal fun estadoDelAnclaje(cableConectado: Boolean, interfaces: List<InterfazDeAnclaje>): EstadoDelAnclaje =
    EstadoDelAnclaje(cableConectado = cableConectado, anclajeActivo = interfaces.isNotEmpty(), interfaces = interfaces)

/**
 * Si hay cable USB conectado: [usbStateConectado] (el extra `connected` del broadcast pegajoso
 * `USB_STATE`) manda cuando se sabe; `null` significa que ese broadcast nunca se disparó (nada se
 * conectó por USB desde que prendió el teléfono) y ahí se cae al respaldo, [bateriaEnchufeUsb]
 * (`BatteryManager`/`ACTION_BATTERY_CHANGED`, `EXTRA_PLUGGED == BATTERY_PLUGGED_USB`). Sin ninguno
 * de los dos, no hay cable.
 */
internal fun cableConectadoDesde(usbStateConectado: Boolean?, bateriaEnchufeUsb: Boolean?): Boolean =
    usbStateConectado ?: bateriaEnchufeUsb ?: false
