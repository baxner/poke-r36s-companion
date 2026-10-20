package com.baxner.r36scompanion.ui.inicio

import com.baxner.r36scompanion.data.conexion.EstadoDeConexion
import com.baxner.r36scompanion.ui.componentes.NivelVisual

/** Qué botón (si alguno) ofrece la tarjeta de conexión para ese [EstadoDeConexion]. */
enum class AccionDeTarjeta { NINGUNA, ACTIVAR_ANCLAJE, REINTENTAR, CONFIRMAR_HUELLA, IR_A_AJUSTES }

/** Lo que muestra la tarjeta de conexión de Inicio: título, texto explicando qué pasa (y qué hacer si hace falta) y su acción. */
data class InfoDeTarjeta(
    val titulo: String,
    val cuerpo: String,
    val nivel: NivelVisual,
    val accion: AccionDeTarjeta = AccionDeTarjeta.NINGUNA,
)

/**
 * Traduce el [EstadoDeConexion] de [com.baxner.r36scompanion.data.conexion.ConexionConConsola] a
 * lo que necesita pintar la tarjeta, en palabras simples para alguien que no sabe qué es SSH (ver
 * el encargo de esta fase, punto 3: "en palabras simples"). Pura, para poder probarla sin Compose
 * ni Android.
 */
fun infoDeTarjeta(estado: EstadoDeConexion): InfoDeTarjeta = when (estado) {
    EstadoDeConexion.SinCable -> InfoDeTarjeta(
        titulo = "Sin cable",
        cuerpo = "Conecta la consola al puerto OTG con el cable USB-A y el adaptador OTG.",
        nivel = NivelVisual.INFO,
    )

    EstadoDeConexion.SinAnclaje -> InfoDeTarjeta(
        titulo = "Falta el anclaje de USB",
        cuerpo = "Hay cable, pero el anclaje de USB está apagado. Actívalo en el teléfono. " +
            "Si usaste un cable C a C, el botón puede salir en gris: prueba con un cable USB-A y un adaptador OTG del lado de la consola.",
        nivel = NivelVisual.AVISO,
        accion = AccionDeTarjeta.ACTIVAR_ANCLAJE,
    )

    EstadoDeConexion.Buscando -> InfoDeTarjeta(
        titulo = "Buscando la consola…",
        cuerpo = "Esto toma unos segundos.",
        nivel = NivelVisual.INFO,
    )

    EstadoDeConexion.NoEncontrada -> InfoDeTarjeta(
        titulo = "No se encontró la consola",
        cuerpo = "¿Activaste Options → Enable Remote Services en la consola? Se apaga cada vez que la reinicias.",
        nivel = NivelVisual.AVISO,
        accion = AccionDeTarjeta.REINTENTAR,
    )

    is EstadoDeConexion.Conectada -> InfoDeTarjeta(
        titulo = estado.consola.nombreDeEquipo,
        cuerpo = if (estado.esNueva) "Consola nueva: la recordaré." else "Conectada.",
        nivel = NivelVisual.OK,
    )

    is EstadoDeConexion.HuellaDistinta -> InfoDeTarjeta(
        titulo = "La consola que contestó no es la tuya",
        cuerpo = "Si de verdad reinstalaste el sistema de tu consola, puedes decirle a la app que la olvide y la recuerde de nuevo.",
        nivel = NivelVisual.ERROR,
        accion = AccionDeTarjeta.CONFIRMAR_HUELLA,
    )

    EstadoDeConexion.CredencialesIncorrectas -> InfoDeTarjeta(
        titulo = "Usuario o contraseña incorrectos",
        cuerpo = "Revísalos en Ajustes.",
        nivel = NivelVisual.ERROR,
        accion = AccionDeTarjeta.IR_A_AJUSTES,
    )

    is EstadoDeConexion.Fallo -> InfoDeTarjeta(
        titulo = "No se pudo conectar",
        cuerpo = estado.mensaje,
        nivel = NivelVisual.ERROR,
        accion = AccionDeTarjeta.REINTENTAR,
    )
}
