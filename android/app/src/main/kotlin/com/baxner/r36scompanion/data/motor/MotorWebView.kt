package com.baxner.r36scompanion.data.motor

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * El motor PKHeX (@pkhex-everywhere/engine) corriendo en un WebView invisible. Los archivos los
 * genera `scripts/build-motor.sh` en `assets/motor/`; la página carga `puente.js`, que recibe
 * `{id, metodo, args}` y contesta `{id, ok, resultado}` (ver `engine-bridge/src/puente.js`).
 *
 * Nada sale a internet: [PoliticaDeRed] solo deja pasar `https://appassets.androidplatform.net`
 * (los propios archivos de la app) y el puente bloquea el espacio `plugins`, que descarga
 * código de terceros.
 *
 * Una sola instancia por proceso ([obtener]). El motor carga una partida a la vez: quien la
 * use para algo de varios pasos (cargar, leer, exportar) tiene que serializarlo por su cuenta.
 */
class MotorWebView private constructor(context: Context) : Motor {

    private val appContext = context.applicationContext
    private val arranque = Mutex()
    private val siguienteId = AtomicLong(1)
    private val pendientes = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val _eventos = MutableSharedFlow<JsonElement>(extraBufferCapacity = 32)

    /** Eventos que levanta el motor (`gameLoaded`, …), tal cual los manda. */
    val eventos: SharedFlow<JsonElement> = _eventos.asSharedFlow()

    // Solo se tocan en el hilo principal.
    private var webView: WebView? = null
    private var listo: CompletableDeferred<Long>? = null
    @Volatile private var respuesta: JavaScriptReplyProxy? = null

    /** Arranca el runtime si hace falta y devuelve cuánto tardó en el WebView (ms). */
    suspend fun arrancar(): Long {
        val diferido = arranque.withLock {
            withContext(Dispatchers.Main) { listo ?: crear() }
        }
        return withTimeout(TiempoDeArranqueMs) { diferido.await() }
    }

    override suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement {
        arrancar()
        val id = siguienteId.getAndIncrement()
        val diferido = CompletableDeferred<JsonElement>()
        pendientes[id] = diferido
        try {
            val mensaje = buildJsonObject {
                put("id", id)
                put("metodo", metodo)
                put("args", JsonArray(args.toList()))
            }.toString()
            withContext(Dispatchers.Main) {
                val proxy = respuesta ?: throw MotorException("motor-sin-canal", "el motor todavía no abrió su canal")
                proxy.postMessage(mensaje)
            }
            return withTimeout(TiempoPorLlamadaMs) { diferido.await() }
        } finally {
            pendientes.remove(id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun crear(): CompletableDeferred<Long> {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "El WebView de este teléfono es muy viejo para el motor (falta WEB_MESSAGE_LISTENER)"
        }
        val diferido = CompletableDeferred<Long>()
        listo = diferido
        val cargador = WebViewAssetLoader.Builder()
            .setDomain(PoliticaDeRed.Dominio)
            .addPathHandler("/assets/", ArchivosDelMotor(appContext))
            .build()
        val vista = WebView(appContext)
        vista.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        vista.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                if (PoliticaDeRed.permitida(request.url)) {
                    cargador.shouldInterceptRequest(request.url) ?: ArchivosDelMotor.respuestaVacia(404, "No encontrado")
                } else {
                    ArchivosDelMotor.respuestaVacia(403, "Sin red")
                }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // El proceso del WebView se cayó (o Android lo mató por memoria): todo lo
                // pendiente falla y la próxima llamada vuelve a arrancar el motor de cero.
                caido(MotorException("motor-caido", "el proceso del motor se cerró"))
                return true
            }
        }
        WebViewCompat.addWebMessageListener(vista, "puenteMotor", setOf(PoliticaDeRed.Origen)) { _, mensaje, origen, esPrincipal, proxy ->
            if (!esPrincipal || origen.toString() != PoliticaDeRed.Origen) return@addWebMessageListener
            respuesta = proxy
            mensaje.data?.let(::recibir)
        }
        webView = vista
        vista.loadUrl(PoliticaDeRed.Pagina)
        return diferido
    }

    private fun recibir(texto: String) {
        val json = runCatching { Puente.json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return
        when (json["evento"]?.jsonPrimitive?.contentOrNull) {
            "listo" -> listo?.complete(json["ms"]?.jsonPrimitive?.longOrNull ?: -1)
            "fallo" -> listo?.completeExceptionally(MotorException("arranque", json["error"]?.toString() ?: "?"))
            "motor" -> _eventos.tryEmit(json["datos"] ?: JsonNull)
            null -> {
                val id = json["id"]?.jsonPrimitive?.longOrNull ?: return
                val diferido = pendientes[id] ?: return
                if (json["ok"]?.jsonPrimitive?.contentOrNull == "true") {
                    diferido.complete(json["resultado"] ?: JsonNull)
                } else {
                    diferido.completeExceptionally(Puente.error(json["error"]))
                }
            }
        }
    }

    private fun caido(motivo: MotorException) {
        listo?.completeExceptionally(motivo)
        pendientes.values.forEach { it.completeExceptionally(motivo) }
        pendientes.clear()
        respuesta = null
        listo = null
        webView?.destroy()
        webView = null
    }

    companion object {
        private const val TiempoDeArranqueMs = 60_000L
        private const val TiempoPorLlamadaMs = 30_000L

        @Volatile private var instancia: MotorWebView? = null

        fun obtener(context: Context): MotorWebView =
            instancia ?: synchronized(this) { instancia ?: MotorWebView(context).also { instancia = it } }
    }
}

/** Qué puede cargar el WebView del motor: solo los archivos de la app. Puro, para probarlo sin Android. */
object PoliticaDeRed {
    const val Dominio = "appassets.androidplatform.net"
    const val Origen = "https://$Dominio"
    const val Pagina = "$Origen/assets/motor/index.html"

    fun permitida(url: Uri): Boolean = permitida(url.scheme, url.host, url.path)

    fun permitida(esquema: String?, host: String?, ruta: String?): Boolean =
        esquema == "https" && host == Dominio && ruta != null && ruta.startsWith("/assets/motor/") && ".." !in ruta
}

/** Sirve `assets/motor/` con el tipo correcto: el runtime de .NET exige `application/wasm`. */
private class ArchivosDelMotor(private val context: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (!path.startsWith("motor/") || ".." in path) return null
        return try {
            val tipo = tipoDe(path)
            WebResourceResponse(tipo, if (tipo.startsWith("text/") || tipo == "application/json") "utf-8" else null, context.assets.open(path))
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        fun tipoDe(ruta: String): String = when (ruta.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "wasm" -> "application/wasm"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }

        fun respuestaVacia(codigo: Int, razon: String) =
            WebResourceResponse("text/plain", "utf-8", codigo, razon, emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}

/** Una llamada que el motor rechazó. [codigo] es el `ErrorCode` del motor (`no-save`, `invalid-save`, …). */
class MotorException(val codigo: String, mensaje: String, val candidatos: JsonElement? = null) : Exception(mensaje)

/** El motor visto desde la app: llamadas crudas por nombre (`game.load`, `party.get`, …). */
interface Motor {
    suspend fun llamar(metodo: String, vararg args: JsonElement): JsonElement
}

/** Formato de los mensajes del puente, compartido con las pruebas. */
object Puente {
    /**
     * `encodeDefaults`: el motor no conoce los valores por defecto de Kotlin. Sin esto, una casilla
     * `slot = 0` (el primero del equipo) se mandaba como `{"source":"party"}` y el motor la
     * rechazaba (`Invalid argument 'at'`). `explicitNulls = false`: los nulos no se mandan, que en
     * un `PokemonPatch` significa "no tocar este campo", y al leer un campo ausente queda en null.
     */
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    /** Bytes en el formato del puente: `{"$bytes": "<base64>"}`. */
    fun bytes(datos: ByteArray): JsonElement =
        JsonObject(mapOf("\$bytes" to JsonPrimitive(java.util.Base64.getEncoder().encodeToString(datos))))

    fun bytesDe(elemento: JsonElement): ByteArray {
        val base64 = (elemento as? JsonObject)?.get("\$bytes")?.jsonPrimitive?.contentOrNull
            ?: throw MotorException("respuesta-rara", "se esperaban bytes y llegó: $elemento")
        return java.util.Base64.getDecoder().decode(base64)
    }

    fun texto(valor: String?): JsonElement = valor?.let(::JsonPrimitive) ?: JsonNull

    fun numero(valor: Number): JsonElement = JsonPrimitive(valor)

    fun error(elemento: JsonElement?): MotorException {
        val error = elemento as? JsonObject
        return MotorException(
            codigo = error?.get("code")?.jsonPrimitive?.contentOrNull ?: "error",
            mensaje = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "error del motor",
            candidatos = error?.get("candidates")?.takeIf { it != JsonNull },
        )
    }
}
