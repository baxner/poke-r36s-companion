package com.baxner.r36scompanion.data.consola

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * El [Anclaje] de verdad: lee `java.net.NetworkInterface` y los broadcasts pegajosos de Android.
 * Lo que decide algo (qué es una interfaz de anclaje, cómo armar el estado) está en
 * [AnclajePuro.kt][esNombreDeInterfazDeAnclaje]; aquí solo se junta esa decisión con las APIs de
 * Android, así que esta clase casi no tiene nada que probar por su cuenta (con Robolectric donde
 * sí hace falta: ver `AnclajeAndroidTest`).
 */
class AnclajeAndroid(private val context: Context) : Anclaje {

    override fun observar(): Flow<EstadoDelAnclaje> = flow {
        while (true) {
            emit(estadoActual())
            delay(IntervaloDeSondeoMs)
        }
    }.distinctUntilChanged()

    override fun estadoActual(): EstadoDelAnclaje =
        estadoDelAnclaje(cableConectado(), interfacesDeAnclajeDesde(interfacesDeRed()))

    /**
     * Prueba, en orden, los componentes conocidos de la pantalla de anclaje ([ComponentesConocidos]:
     * Samsung y AOSP) comprobando con [android.content.pm.PackageManager.resolveActivity] antes de
     * lanzar; si ninguno existe en este equipo, abre los ajustes de redes inalámbricas genéricos.
     * Nunca truena por falta de uno: si de plano nada resuelve, devuelve `false`.
     */
    override fun abrirAjustesDeAnclaje(): Boolean {
        for (componente in ComponentesConocidos) {
            val intent = Intent().setComponent(componente).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, 0) != null && intentarAbrir(intent)) return true
        }
        return intentarAbrir(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun intentarAbrir(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * `USB_STATE` es un broadcast oculto del SDK (no hay una constante pública): se lee con
     * `registerReceiver(null, ...)`, el truco de siempre para preguntar el último valor de un
     * sticky sin quedarse registrado. Si nunca se disparó (sticky vacío), se cae al respaldo de
     * `BatteryManager`.
     */
    private fun cableConectado(): Boolean {
        val estadoUsb = context.registerReceiver(null, IntentFilter(AccionUsbState))
        val bateria = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val conectadoPorUsbState = estadoUsb?.takeIf { it.hasExtra(ExtraConectado) }?.getBooleanExtra(ExtraConectado, false)
        val enchufadoPorUsb = bateria?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }
            ?.let { it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == BatteryManager.BATTERY_PLUGGED_USB }
        return cableConectadoDesde(conectadoPorUsbState, enchufadoPorUsb)
    }

    private fun interfacesDeRed(): List<InfoDeInterfazDeRed> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).map { interfaz ->
            InfoDeInterfazDeRed(
                nombre = interfaz.name,
                arriba = runCatching { interfaz.isUp }.getOrDefault(false),
                direccionesIpv4 = interfaz.interfaceAddresses.mapNotNull { direccion ->
                    val ip = direccion.address as? Inet4Address ?: return@mapNotNull null
                    InfoDeInterfazDeRed.DireccionIpv4(ip.hostAddress ?: return@mapNotNull null, direccion.networkPrefixLength.toInt())
                },
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val IntervaloDeSondeoMs = 2_000L
        private const val AccionUsbState = "android.hardware.usb.action.USB_STATE"
        private const val ExtraConectado = "connected"

        /**
         * Samsung (el S24 del usuario) y AOSP, en ese orden; ver el encargo de este módulo para el
         * porqué de la lista exacta. [PackageManager.resolveActivity] descarta la que no exista
         * en este equipo antes de intentar lanzarla.
         */
        private val ComponentesConocidos = listOf(
            ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
            ComponentName("com.android.settings", "com.android.settings.TetherSettings"),
        )
    }
}
