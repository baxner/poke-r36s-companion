package com.baxner.r36scompanion

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * El proceso: crea [Modulos] una sola vez y conecta su ciclo de vida con el de la app en
 * conjunto (no el de una sola Activity, aunque hoy solo hay [MainActivity]).
 *
 * [ConexionConConsola][com.baxner.r36scompanion.data.conexion.ConexionConConsola] empieza a
 * observar el anclaje ([Modulos.conexion] `.iniciar()`) en cuanto algo queda en pantalla, y deja
 * de hacerlo ([Modulos.conexion] `.detener()`) cuando ya no queda nada — así no sondea el anclaje
 * ni mantiene la sesión SSH abierta con la app en segundo plano. [MargenDeRotacionMs] evita que
 * una rotación de pantalla (que para la actividad vieja y arranca la nueva) se lea como "la app se
 * fue a segundo plano": ver [ContadorDeVisibilidad].
 */
class CompanionApplication : Application() {
    lateinit var modulos: Modulos
        private set

    override fun onCreate() {
        super.onCreate()
        modulos = Modulos(this)

        val contador = ContadorDeVisibilidad()
        val manejador = Handler(Looper.getMainLooper())
        var saliendoAlFondo: Runnable? = null

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                saliendoAlFondo?.let(manejador::removeCallbacks)
                saliendoAlFondo = null
                if (contador.alIniciarUnaActividad()) modulos.conexion.iniciar()
            }

            override fun onActivityStopped(activity: Activity) {
                if (contador.alDetenerUnaActividad()) {
                    val tarea = Runnable { modulos.conexion.detener() }
                    saliendoAlFondo = tarea
                    manejador.postDelayed(tarea, MargenDeRotacionMs)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private companion object {
        const val MargenDeRotacionMs = 700L
    }
}
