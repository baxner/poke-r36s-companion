package com.baxner.r36scompanion.data.jugar

import com.baxner.r36scompanion.data.consola.ArchivoRemoto
import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tope de lectura de una ROM, tanto en la consola como de la entrada de un `.zip` (ver el encargo). */
private const val TopeDeRom = 64L * 1024 * 1024

private val RomJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Extensiones (en minúsculas) que la app reconoce pero no puede abrir: [ErrorDeRom.FormatoNoSoportado]. */
private val FormatosNoSoportados = setOf("7z", "rar")

/** Metadatos de una ROM copiada, guardados en `<juegoId>.json` junto a su `<juegoId>.gba`. */
@Serializable
private data class RomGuardadaJson(
    val juegoId: Long,
    val rutaEnConsola: String,
    val tamanoEnConsola: Long,
    val modificadoEnConsola: Long,
    val codigoDeJuego: String,
    val tituloInterno: String,
    val tamano: Long,
)

private fun RomGuardadaJson.aDominio(archivo: File) = RomEnElTelefono(
    juegoId = juegoId,
    archivo = archivo,
    rutaEnConsola = rutaEnConsola,
    tamanoEnConsola = tamanoEnConsola,
    modificadoEnConsola = modificadoEnConsola,
    codigoDeJuego = codigoDeJuego,
    tituloInterno = tituloInterno,
    tamano = tamano,
)

private fun RomEnElTelefono.aJson() = RomGuardadaJson(
    juegoId = juegoId,
    rutaEnConsola = rutaEnConsola,
    tamanoEnConsola = tamanoEnConsola,
    modificadoEnConsola = modificadoEnConsola,
    codigoDeJuego = codigoDeJuego,
    tituloInterno = tituloInterno,
    tamano = tamano,
)

/**
 * [RomsDelTelefono] sobre archivos del teléfono (ver el KDoc del contrato en Jugar.kt y el
 * encargo de la fase 5, punto 1). Cada ROM copiada vive en [carpeta] como `<juegoId>.gba` más sus
 * metadatos en `<juegoId>.json`, escritos con [escribirArchivoAtomico]: si algo falla a medio
 * copiar no queda ni un archivo nuevo ni un temporal, y la copia vieja (si había) sigue intacta.
 *
 * [espacioLibre] es inyectable para que las pruebas simulen que no cabe sin llenar un disco de
 * verdad; en la app por omisión es el espacio libre real de [carpeta].
 */
class RomsDelTelefonoEnArchivos(
    private val carpeta: File,
    private val espacioLibre: () -> Long = { carpeta.usableSpace },
) : RomsDelTelefono {
    private val mutex = Mutex()
    private val estado: MutableStateFlow<List<RomEnElTelefono>>

    init {
        carpeta.mkdirs()
        estado = MutableStateFlow(cargarGuardadas())
    }

    /** Carga lo que ya había en [carpeta] (p. ej. de una sesión anterior de la app). Ignora los `.json` sin su `.gba`. */
    private fun cargarGuardadas(): List<RomEnElTelefono> =
        (carpeta.listFiles { archivo -> archivo.isFile && archivo.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { archivoJson ->
                runCatching {
                    val metadatos = RomJson.decodeFromString(RomGuardadaJson.serializer(), archivoJson.readText())
                    val archivoGba = File(carpeta, "${metadatos.juegoId}.gba")
                    metadatos.aDominio(archivoGba).takeIf { archivoGba.exists() }
                }.getOrNull()
            }

    override fun guardadas(): Flow<List<RomEnElTelefono>> = estado.asStateFlow()

    override suspend fun rom(juegoId: Long): RomEnElTelefono? = estado.value.firstOrNull { it.juegoId == juegoId }

    override suspend fun traer(sesion: SesionDeConsola, juego: JuegoRespaldado): RomEnElTelefono = mutex.withLock {
        val carpetaRemota = juego.rutaDePartida.substringBeforeLast('/')
        val coincidencias = sesion.listar(carpetaRemota)
            .filter { !it.esCarpeta && nombreSinExtension(it.nombre) == juego.nombre }

        val romGba = coincidencias.firstOrNull { it.extension().equals("gba", ignoreCase = true) }
        val romZip = coincidencias.firstOrNull { it.extension().equals("zip", ignoreCase = true) }
        val remoto = romGba ?: romZip ?: run {
            val noSoportado = coincidencias.firstOrNull { it.extension().lowercase() in FormatosNoSoportados }
            if (noSoportado != null) throw ErrorDeRom.FormatoNoSoportado(noSoportado.ruta, noSoportado.extension())
            throw ErrorDeRom.NoEncontrada(carpetaRemota, juego.nombre)
        }

        estado.value.firstOrNull { it.juegoId == juego.id }?.let { existente ->
            val sinCambios = existente.rutaEnConsola == remoto.ruta &&
                existente.tamanoEnConsola == remoto.tamano &&
                existente.modificadoEnConsola == remoto.modificado
            if (sinCambios) return@withLock existente
        }

        val esZip = remoto.extension().equals("zip", ignoreCase = true)
        val necesita = if (esZip) remoto.tamano * 2 else remoto.tamano
        val libre = espacioLibre()
        if (libre < necesita) throw ErrorDeRom.SinEspacio(necesita, libre)

        val leido = sesion.leer(remoto.ruta, maximo = TopeDeRom)
        val datosGba = if (esZip) withContext(Dispatchers.IO) { extraerGbaDeZip(leido, remoto.ruta) } else leido

        if (datosGba.size < 0xC0 || datosGba[3] != 0xEA.toByte()) throw ErrorDeRom.NoEsDeGba(remoto.ruta)
        val codigoDeJuego = String(datosGba, 0xAC, 4, Charsets.US_ASCII)
        val tituloInterno = String(datosGba, 0xA0, 12, Charsets.US_ASCII).trimEnd('\u0000', ' ')

        val nuevo = RomEnElTelefono(
            juegoId = juego.id,
            archivo = File(carpeta, "${juego.id}.gba"),
            rutaEnConsola = remoto.ruta,
            tamanoEnConsola = remoto.tamano,
            modificadoEnConsola = remoto.modificado,
            codigoDeJuego = codigoDeJuego,
            tituloInterno = tituloInterno,
            tamano = datosGba.size.toLong(),
        )

        // Primero la ROM, luego sus metadatos: si el .gba se escribe bien pero el .json falla, la
        // próxima vez que se compare contra la consola el .json (viejo) no calzará con lo remoto y
        // se vuelve a copiar entera, así que no queda una ROM a medias de verdad.
        escribirArchivoAtomico(nuevo.archivo, datosGba)
        escribirArchivoAtomico(
            File(carpeta, "${juego.id}.json"),
            RomJson.encodeToString(RomGuardadaJson.serializer(), nuevo.aJson()).encodeToByteArray(),
        )

        estado.value = estado.value.filterNot { it.juegoId == juego.id } + nuevo
        nuevo
    }

    override suspend fun borrar(juegoId: Long): Unit = mutex.withLock {
        File(carpeta, "$juegoId.gba").delete()
        File(carpeta, "$juegoId.json").delete()
        estado.value = estado.value.filterNot { it.juegoId == juegoId }
    }

    private fun ArchivoRemoto.extension(): String = nombre.substringAfterLast('.', "")
}

private fun nombreSinExtension(nombre: String): String = nombre.substringBeforeLast('.', nombre)

/** Saca la primera entrada que termine en `.gba` (sin importar mayúsculas) del `.zip`; si ninguna trae, [ErrorDeRom.NoEsDeGba]. */
private fun extraerGbaDeZip(zipBytes: ByteArray, rutaZip: String): ByteArray {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        while (true) {
            val entrada = zip.nextEntry ?: break
            if (!entrada.isDirectory && entrada.name.substringAfterLast('.', "").equals("gba", ignoreCase = true)) {
                return leerConTope(zip, TopeDeRom)
            }
        }
    }
    throw ErrorDeRom.NoEsDeGba(rutaZip)
}

/** Lee [entrada] completa sin pasarse de [tope] bytes (una entrada de zip puede mentir sobre su tamaño). */
private fun leerConTope(entrada: InputStream, tope: Long): ByteArray {
    val salida = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val leidos = entrada.read(buffer)
        if (leidos == -1) break
        total += leidos
        if (total > tope) throw IOException("la ROM dentro del .zip pasa del tope de $tope bytes")
        salida.write(buffer, 0, leidos)
    }
    return salida.toByteArray()
}
