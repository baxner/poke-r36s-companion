package com.baxner.r36scompanion.data.conexion

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.baxner.r36scompanion.data.consola.Credenciales

/**
 * La consola fijada (huella SSH y nombre) y su usuario y contraseña, cifrados en el teléfono con
 * una llave del Keystore de Android, como el vínculo del agente en Vigía.
 */
class PreferenciasDeConsolaCifradas(context: Context) : PreferenciasDeConsola {

    private val preferencias: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "consola",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override var huellaFijada: String?
        get() = preferencias.getString(ClaveHuella, null)
        set(valor) = preferencias.edit().putString(ClaveHuella, valor).apply()

    override var nombreDeEquipo: String?
        get() = preferencias.getString(ClaveNombre, null)
        set(valor) = preferencias.edit().putString(ClaveNombre, valor).apply()

    override var credenciales: Credenciales
        get() = Credenciales(
            usuario = preferencias.getString(ClaveUsuario, null) ?: Credenciales().usuario,
            contrasena = preferencias.getString(ClaveContrasena, null) ?: Credenciales().contrasena,
        )
        set(valor) = preferencias.edit().putString(ClaveUsuario, valor.usuario).putString(ClaveContrasena, valor.contrasena).apply()

    private companion object {
        const val ClaveHuella = "huella"
        const val ClaveNombre = "nombre"
        const val ClaveUsuario = "usuario"
        const val ClaveContrasena = "contrasena"
    }
}
