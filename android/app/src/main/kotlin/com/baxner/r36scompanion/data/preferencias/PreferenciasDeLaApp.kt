package com.baxner.r36scompanion.data.preferencias

import android.content.Context
import android.content.SharedPreferences

/**
 * Ajustes de la propia app que no son de la consola (eso es [com.baxner.r36scompanion.data.conexion.PreferenciasDeConsola]):
 * si ya se vio la Introducción y si se respalda solo al conectar. No necesitan cifrado (no hay
 * nada sensible), a diferencia de las credenciales de la consola.
 */
interface PreferenciasDeLaApp {
    /** false hasta que el usuario termina (o salta) la Introducción una vez. */
    var introduccionVista: Boolean

    /** Respaldar automáticamente al conectar con la consola (ver docs/plan.md, Inicio). Encendido por defecto. */
    var respaldoAutomatico: Boolean
}

class PreferenciasDeLaAppSharedPrefs(context: Context) : PreferenciasDeLaApp {
    private val preferencias: SharedPreferences =
        context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)

    override var introduccionVista: Boolean
        get() = preferencias.getBoolean(ClaveIntroduccionVista, false)
        set(valor) = preferencias.edit().putBoolean(ClaveIntroduccionVista, valor).apply()

    override var respaldoAutomatico: Boolean
        get() = preferencias.getBoolean(ClaveRespaldoAutomatico, true)
        set(valor) = preferencias.edit().putBoolean(ClaveRespaldoAutomatico, valor).apply()

    private companion object {
        const val ClaveIntroduccionVista = "introduccion_vista"
        const val ClaveRespaldoAutomatico = "respaldo_automatico"
    }
}
