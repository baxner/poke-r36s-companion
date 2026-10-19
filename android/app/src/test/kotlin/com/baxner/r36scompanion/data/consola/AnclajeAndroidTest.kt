package com.baxner.r36scompanion.data.consola

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [AnclajeAndroid] con Robolectric: lo que decide algo ya está probado sin Android en
 * [AnclajePuroTest]; aquí solo se prueba que la parte que sí toca Android (broadcasts pegajosos,
 * `PackageManager`, `startActivity`) no truena y hace lo esperado en los casos fáciles de armar
 * con sombras de Robolectric.
 *
 * `sdk = [35]`: igual que en Vigía (mismo stack, ver `AGENTS.md`), porque el `android-all` de la
 * 36 (`compileSdk`) necesita Java 21 y el proyecto compila con Java 17.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnclajeAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val anclaje = AnclajeAndroid(context)

    @Test
    fun `estadoActual no truena sin broadcasts pegajosos ni interfaces de anclaje`() {
        val estado = anclaje.estadoActual()

        assertFalse(estado.anclajeActivo)
        assertTrue(estado.interfaces.isEmpty())
        assertFalse(estado.cableConectado)
    }

    @Test
    fun `abrirAjustesDeAnclaje cae a los ajustes inalambricos genericos si no hay ningun componente conocido instalado`() {
        val pudoAbrir = anclaje.abrirAjustesDeAnclaje()

        assertTrue(pudoAbrir)
        assertEquals(Settings.ACTION_WIRELESS_SETTINGS, shadowOf(context).nextStartedActivity?.action)
    }

    @Test
    fun `abrirAjustesDeAnclaje prefiere un componente conocido si de verdad esta instalado`() {
        registrarActividad(context, "com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")

        val pudoAbrir = anclaje.abrirAjustesDeAnclaje()

        assertTrue(pudoAbrir)
        val siguiente = shadowOf(context).nextStartedActivity
        assertEquals("com.android.settings", siguiente?.component?.packageName)
        assertEquals("com.android.settings.Settings\$TetherSettingsActivity", siguiente?.component?.className)
    }

    @Test
    fun `abrirAjustesDeAnclaje usa la accion de anclaje si una pantalla la declara (como en el S24)`() {
        registrarActividad(context, "com.android.settings", "com.android.settings.Settings\$SecTetherSettingsActivity")
        shadowOf(context.packageManager).addIntentFilterForActivity(
            android.content.ComponentName("com.android.settings", "com.android.settings.Settings\$SecTetherSettingsActivity"),
            android.content.IntentFilter("android.settings.TETHER_SETTINGS").apply { addCategory(android.content.Intent.CATEGORY_DEFAULT) },
        )

        assertTrue(anclaje.abrirAjustesDeAnclaje())

        assertEquals("android.settings.TETHER_SETTINGS", shadowOf(context).nextStartedActivity?.action)
    }

    @Test
    fun `abrirAjustesDeAnclaje prefiere la pantalla de Samsung a la de AOSP`() {
        registrarActividad(context, "com.android.settings", "com.android.settings.TetherSettings")
        registrarActividad(context, "com.android.settings", "com.android.settings.Settings\$SecTetherSettingsActivity")

        assertTrue(anclaje.abrirAjustesDeAnclaje())

        assertEquals("com.android.settings.Settings\$SecTetherSettingsActivity", shadowOf(context).nextStartedActivity?.component?.className)
    }

    private fun registrarActividad(context: Context, paquete: String, clase: String) {
        val info = ActivityInfo().apply {
            packageName = paquete
            name = clase
            enabled = true
            exported = true
            applicationInfo = ApplicationInfo().apply {
                packageName = paquete
                this.enabled = true
            }
        }
        shadowOf(context.packageManager).addOrUpdateActivity(info)
    }
}
