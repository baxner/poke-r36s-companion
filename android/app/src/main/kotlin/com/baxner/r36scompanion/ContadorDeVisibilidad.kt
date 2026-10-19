package com.baxner.r36scompanion

/**
 * Cuenta actividades en primer plano para saber si la app en conjunto está a la vista (ver
 * [CompanionApplication]: con eso se decide cuándo [com.baxner.r36scompanion.data.conexion.ConexionConConsola]
 * deja de observar el anclaje, para no gastar batería con la app en segundo plano). Pura (sin
 * Android) para poder probarla con JUnit normal; lo único que hace falta de Android
 * (`registerActivityLifecycleCallbacks`, un `Handler` para el margen de la rotación) vive en
 * [CompanionApplication].
 *
 * Solo cuenta: no sabe nada de rotaciones ni de márgenes de tiempo. Una actividad que rota
 * (`onStop` de la vieja, `onStart` de la nueva) pasa brevemente por 0, por eso
 * [CompanionApplication] no actúa al toque sobre [aVisible] == false, sino con un pequeño retraso
 * cancelable.
 */
class ContadorDeVisibilidad {
    private var contador = 0

    /** true la primera vez que algo queda en pantalla (0 -> 1): ya es visible. */
    fun alIniciarUnaActividad(): Boolean {
        contador++
        return contador == 1
    }

    /** true si ya no queda nada en pantalla (1 -> 0): dejó de ser visible. */
    fun alDetenerUnaActividad(): Boolean {
        contador = (contador - 1).coerceAtLeast(0)
        return contador == 0
    }
}
