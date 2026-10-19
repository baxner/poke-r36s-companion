package com.baxner.r36scompanion.data.motor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El WebView del motor solo puede cargar los archivos del motor dentro de la app. */
class PoliticaDeRedTest {
    @Test
    fun dejaPasarSoloLosArchivosDelMotor() {
        assertTrue(PoliticaDeRed.permitida("https", "appassets.androidplatform.net", "/assets/motor/index.html"))
        assertTrue(PoliticaDeRed.permitida("https", "appassets.androidplatform.net", "/assets/motor/runtime/dotnet.js"))
    }

    @Test
    fun bloqueaInternetYCualquierOtraCosa() {
        assertFalse(PoliticaDeRed.permitida("https", "cdn.jsdelivr.net", "/npm/@pkhex-everywhere/engine/_framework/dotnet.js"))
        assertFalse(PoliticaDeRed.permitida("https", "www.googletagmanager.com", "/gtag/js"))
        assertFalse(PoliticaDeRed.permitida("http", "appassets.androidplatform.net", "/assets/motor/index.html"))
        assertFalse(PoliticaDeRed.permitida("https", "appassets.androidplatform.net", "/assets/otra-cosa.js"))
        assertFalse(PoliticaDeRed.permitida("https", "appassets.androidplatform.net", "/assets/motor/../../datos"))
        assertFalse(PoliticaDeRed.permitida("https", "10.101.33.156", "/"))
        assertFalse(PoliticaDeRed.permitida(null, null, null))
    }
}
