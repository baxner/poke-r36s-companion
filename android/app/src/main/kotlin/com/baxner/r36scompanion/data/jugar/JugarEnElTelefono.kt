package com.baxner.r36scompanion.data.jugar

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import com.baxner.r36scompanion.data.intercambio.EjecutorDeIntercambio
import com.baxner.r36scompanion.data.intercambio.PartidasIntercambiadas
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.EscritorDePartidas
import com.baxner.r36scompanion.data.respaldos.EstadoDePartida
import com.baxner.r36scompanion.data.respaldos.JuegoRespaldado
import com.baxner.r36scompanion.data.respaldos.LectorDeResumen
import com.baxner.r36scompanion.data.respaldos.Reloj
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.data.respaldos.ResultadoDeEscritura
import com.baxner.r36scompanion.data.respaldos.TipoDeArchivo
import com.baxner.r36scompanion.data.respaldos.diferencias
import com.baxner.r36scompanion.data.respaldos.formatoDeFechaParaCarpeta
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val JugarJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Lo que se persiste en `carpeta/estado.json` (ver el KDoc de [JugarEnElTelefono]). */
@Serializable
private data class EstadoDeJugarJson(
    val prestamos: List<PrestamoJson> = emptyList(),
    val intercambio: IntercambioJson? = null,
)

@Serializable
private data class PrestamoJson(
    val juegoId: Long,
    val base: String?,
    val desde: Long,
    val actual: String?,
    val vecesGuardada: Int,
    val ultimaVezGuardada: Long?,
)

private fun PrestamoJson.aDominio() = PartidaPrestada(juegoId, base, desde, actual, vecesGuardada, ultimaVezGuardada)

private fun PartidaPrestada.aJson() = PrestamoJson(juegoId, base, desde, actual, vecesGuardada, ultimaVezGuardada)

@Serializable
private data class IntercambioJson(
    val id: Long,
    val juegoA: Long,
    val baseA: String?,
    val juegoB: Long,
    val baseB: String?,
    val empezado: Long,
    val vecesGuardadaA: Int,
    val vecesGuardadaB: Int,
    val estado: EstadoDeIntercambioReal,
)

private fun IntercambioJson.aDominio() =
    IntercambioReal(id, juegoA, baseA, juegoB, baseB, empezado, vecesGuardadaA, vecesGuardadaB, estado)

private fun IntercambioReal.aJson() =
    IntercambioJson(id, juegoA, baseA, juegoB, baseB, empezado, vecesGuardadaA, vecesGuardadaB, estado)

/**
 * [Prestamos] e [IntercambiosReales] sobre un solo estado (ver el encargo de la fase 5, punto 2):
 * se usan por [prestamos] e [intercambiosReales], que comparten un [Mutex] y un solo archivo de
 * estado (`carpeta/estado.json`, escrito atómico con
 * [escribirArchivoAtomico]) para que una partida esté a lo más en un préstamo o en un intercambio
 * real a la vez ([ErrorDeJuego.YaEstaEnElTelefono], [ErrorDeJuego.HayOtroIntercambio]).
 *
 * Las partidas en sí viven en archivos aparte, también escritos atómicos:
 * `carpeta/prestamos/<juegoId>/original.sav` y `actual.sav`; `carpeta/intercambio/<id>/a-original.sav`,
 * `a-actual.sav`, `b-original.sav`, `b-actual.sav`. Lo descartado se mueve (no se borra) a
 * `carpeta/descartadas/`.
 *
 * Al crear el objeto se carga el estado de `estado.json`: si Android mató la app, el préstamo o
 * el intercambio sin terminar siguen ahí.
 */
class JugarEnElTelefono(
    private val carpeta: File,
    private val repositorio: RepositorioDeRespaldos,
    private val escritor: EscritorDePartidas,
    private val ejecutor: EjecutorDeIntercambio,
    private val lector: LectorDeResumen,
    private val reloj: Reloj,
) {
    private val mutex = Mutex()
    private val archivoEstado = File(carpeta, "estado.json")

    private val prestamosFlow: MutableStateFlow<List<PartidaPrestada>>
    private val pendienteFlow: MutableStateFlow<IntercambioReal?>

    init {
        carpeta.mkdirs()
        val estadoInicial = cargarEstado()
        prestamosFlow = MutableStateFlow(estadoInicial.prestamos.map { it.aDominio() })
        pendienteFlow = MutableStateFlow(estadoInicial.intercambio?.aDominio())
    }

    /**
     * Las dos caras de esta clase. Van como objetos aparte porque [Prestamos.descartar] e
     * [IntercambiosReales.descartar] tienen la misma firma en la JVM (`descartar(Long)`): una
     * sola clase no puede implementar las dos.
     */
    val prestamos: Prestamos = object : Prestamos {
        override fun prestadas() = this@JugarEnElTelefono.prestadas()
        override suspend fun prestada(juegoId: Long) = this@JugarEnElTelefono.prestada(juegoId)
        override suspend fun avisosAntesDeLlevar(juegoId: Long) = this@JugarEnElTelefono.avisosAntesDeLlevar(juegoId)
        override suspend fun llevar(juegoId: Long) = this@JugarEnElTelefono.llevar(juegoId)
        override suspend fun partida(juegoId: Long) = this@JugarEnElTelefono.partida(juegoId)
        override suspend fun guardar(juegoId: Long, bytes: ByteArray) = this@JugarEnElTelefono.guardar(juegoId, bytes)
        override suspend fun regresar(sesion: SesionDeConsola, juegoId: Long) = this@JugarEnElTelefono.regresar(sesion, juegoId)
        override suspend fun regresarAunqueCambio(sesion: SesionDeConsola, juegoId: Long) =
            this@JugarEnElTelefono.regresarAunqueCambio(sesion, juegoId)
        override suspend fun descartar(juegoId: Long) = descartarPrestamo(juegoId)
    }

    val intercambiosReales: IntercambiosReales = object : IntercambiosReales {
        override fun pendiente() = this@JugarEnElTelefono.pendiente()
        override suspend fun empezar(juegoA: Long, juegoB: Long) = this@JugarEnElTelefono.empezar(juegoA, juegoB)
        override suspend fun partidas(id: Long) = this@JugarEnElTelefono.partidas(id)
        override suspend fun guardar(id: Long, jugador: Int, bytes: ByteArray) = this@JugarEnElTelefono.guardar(id, jugador, bytes)
        override suspend fun terminarDeJugar(id: Long) = this@JugarEnElTelefono.terminarDeJugar(id)
        override suspend fun cambios(id: Long) = this@JugarEnElTelefono.cambios(id)
        override suspend fun escribir(sesion: SesionDeConsola, id: Long) = this@JugarEnElTelefono.escribir(sesion, id)
        override suspend fun descartar(id: Long) = descartarIntercambio(id)
    }

    private fun cargarEstado(): EstadoDeJugarJson =
        if (archivoEstado.exists()) {
            runCatching { JugarJson.decodeFromString(EstadoDeJugarJson.serializer(), archivoEstado.readText()) }
                .getOrDefault(EstadoDeJugarJson())
        } else {
            EstadoDeJugarJson()
        }

    private fun persistirEstado() {
        val json = EstadoDeJugarJson(
            prestamos = prestamosFlow.value.map { it.aJson() },
            intercambio = pendienteFlow.value?.aJson(),
        )
        escribirArchivoAtomico(archivoEstado, JugarJson.encodeToString(EstadoDeJugarJson.serializer(), json).encodeToByteArray())
    }

    // ---------------------------------------------------------------------------------------
    // Prestamos
    // ---------------------------------------------------------------------------------------

    fun prestadas(): Flow<List<PartidaPrestada>> = prestamosFlow.asStateFlow()

    suspend fun prestada(juegoId: Long): PartidaPrestada? =
        prestamosFlow.value.firstOrNull { it.juegoId == juegoId }

    suspend fun avisosAntesDeLlevar(juegoId: Long): List<AvisoAntesDeJugar> {
        val juego = juegoRespaldado(juegoId)
        val versiones = repositorio.versiones(juegoId).first()
        val partida = versiones.firstOrNull { it.tipo == TipoDeArchivo.PARTIDA }
        val estadoAutomatico = versiones.firstOrNull { it.tipo == TipoDeArchivo.ESTADO_AUTOMATICO }

        val avisos = mutableListOf<AvisoAntesDeJugar>()

        if (estadoAutomatico != null) {
            val avanzaSoloEnElEstado = partida == null || estadoAutomatico.modificadoEnConsola >= partida.modificadoEnConsola - 300
            if (avanzaSoloEnElEstado) {
                avisos += AvisoAntesDeJugar.AvanceSoloEnElEstado(estadoAutomatico.modificadoEnConsola, partida?.modificadoEnConsola)
            }
        }
        if (partida == null || partida.estadoDePartida == EstadoDePartida.VACIA) {
            avisos += AvisoAntesDeJugar.PartidaVacia
        }
        if (juego?.esPokemon == true && juego.resumen == null) {
            avisos += AvisoAntesDeJugar.NoReconocida
        }
        return avisos
    }

    suspend fun llevar(juegoId: Long): PartidaPrestada = mutex.withLock {
        prestamosFlow.value.firstOrNull { it.juegoId == juegoId }?.let { return@withLock it }
        pendienteFlow.value?.let { intercambio ->
            if (intercambio.juegoA == juegoId || intercambio.juegoB == juegoId) throw ErrorDeJuego.YaEstaEnElTelefono(juegoId)
        }

        val (base, bytes) = ultimaPartidaDe(juegoId)
        escribirArchivoAtomico(archivoDePrestamo(juegoId, "original"), bytes)
        escribirArchivoAtomico(archivoDePrestamo(juegoId, "actual"), bytes)

        val nuevo = PartidaPrestada(juegoId, base = base, desde = reloj.ahoraMs(), actual = base, vecesGuardada = 0, ultimaVezGuardada = null)
        prestamosFlow.value = prestamosFlow.value + nuevo
        persistirEstado()
        nuevo
    }

    suspend fun partida(juegoId: Long): ByteArray = mutex.withLock {
        prestamoDeOError(juegoId)
        archivoDePrestamo(juegoId, "actual").readBytes()
    }

    suspend fun guardar(juegoId: Long, bytes: ByteArray): PartidaPrestada = mutex.withLock {
        val prestamo = prestamoDeOError(juegoId)
        escribirArchivoAtomico(archivoDePrestamo(juegoId, "actual"), bytes)
        val nuevo = prestamo.copy(
            actual = AlmacenDeBlobs.sha256Hex(bytes),
            vecesGuardada = prestamo.vecesGuardada + 1,
            ultimaVezGuardada = reloj.ahoraMs(),
        )
        prestamosFlow.value = prestamosFlow.value.map { if (it.juegoId == juegoId) nuevo else it }
        persistirEstado()
        nuevo
    }

    suspend fun regresar(sesion: SesionDeConsola, juegoId: Long): ResultadoDeEscritura? =
        regresarConBase(sesion, juegoId) { it.base }

    suspend fun regresarAunqueCambio(sesion: SesionDeConsola, juegoId: Long): ResultadoDeEscritura? =
        regresarConBase(sesion, juegoId) { null }

    private suspend fun regresarConBase(
        sesion: SesionDeConsola,
        juegoId: Long,
        base: (PartidaPrestada) -> String?,
    ): ResultadoDeEscritura? = mutex.withLock {
        val prestamo = prestamoDeOError(juegoId)
        if (prestamo.vecesGuardada == 0) {
            quitarPrestamoDelEstado(juegoId)
            carpetaDePrestamo(juegoId).deleteRecursively()
            return@withLock null
        }

        val bytes = archivoDePrestamo(juegoId, "actual").readBytes()
        val resultado = escritor.escribir(sesion, juegoId, bytes, base(prestamo))
        // Solo se llega aquí si escribió bien: si lanzó, el préstamo sigue como estaba (arriba).
        quitarPrestamoDelEstado(juegoId)
        carpetaDePrestamo(juegoId).deleteRecursively()
        resultado
    }

    private fun descartarPrestamoSinLock(juegoId: Long) {
        moverADescartadas(archivoDePrestamo(juegoId, "actual"), juegoId, sufijo = null)
        quitarPrestamoDelEstado(juegoId)
        carpetaDePrestamo(juegoId).deleteRecursively()
    }

    private fun quitarPrestamoDelEstado(juegoId: Long) {
        prestamosFlow.value = prestamosFlow.value.filterNot { it.juegoId == juegoId }
        persistirEstado()
    }

    private fun prestamoDeOError(juegoId: Long): PartidaPrestada =
        prestamosFlow.value.firstOrNull { it.juegoId == juegoId } ?: throw ErrorDeJuego.NoEstaEnElTelefono(juegoId)

    // ---------------------------------------------------------------------------------------
    // IntercambiosReales
    // ---------------------------------------------------------------------------------------

    fun pendiente(): Flow<IntercambioReal?> = pendienteFlow.asStateFlow()

    suspend fun empezar(juegoA: Long, juegoB: Long): IntercambioReal = mutex.withLock {
        pendienteFlow.value?.let { throw ErrorDeJuego.HayOtroIntercambio(it.id) }
        for (juegoId in listOf(juegoA, juegoB)) {
            if (prestamosFlow.value.any { it.juegoId == juegoId }) throw ErrorDeJuego.YaEstaEnElTelefono(juegoId)
        }

        val (baseA, bytesA) = ultimaPartidaDe(juegoA)
        val (baseB, bytesB) = ultimaPartidaDe(juegoB)
        val ahora = reloj.ahoraMs()

        escribirArchivoAtomico(archivoDeIntercambio(ahora, "a-original"), bytesA)
        escribirArchivoAtomico(archivoDeIntercambio(ahora, "a-actual"), bytesA)
        escribirArchivoAtomico(archivoDeIntercambio(ahora, "b-original"), bytesB)
        escribirArchivoAtomico(archivoDeIntercambio(ahora, "b-actual"), bytesB)

        val nuevo = IntercambioReal(
            id = ahora,
            juegoA = juegoA,
            baseA = baseA,
            juegoB = juegoB,
            baseB = baseB,
            empezado = ahora,
            vecesGuardadaA = 0,
            vecesGuardadaB = 0,
            estado = EstadoDeIntercambioReal.JUGANDO,
        )
        pendienteFlow.value = nuevo
        persistirEstado()
        nuevo
    }

    suspend fun partidas(id: Long): Pair<ByteArray, ByteArray> = mutex.withLock {
        intercambioDeOError(id)
        archivoDeIntercambio(id, "a-actual").readBytes() to archivoDeIntercambio(id, "b-actual").readBytes()
    }

    suspend fun guardar(id: Long, jugador: Int, bytes: ByteArray): IntercambioReal = mutex.withLock {
        val actual = intercambioDeOError(id)
        escribirArchivoAtomico(archivoDeIntercambio(id, "${ladoDe(jugador)}-actual"), bytes)
        val nuevo = if (jugador == 0) {
            actual.copy(vecesGuardadaA = actual.vecesGuardadaA + 1)
        } else {
            actual.copy(vecesGuardadaB = actual.vecesGuardadaB + 1)
        }
        pendienteFlow.value = nuevo
        persistirEstado()
        nuevo
    }

    suspend fun terminarDeJugar(id: Long): IntercambioReal = mutex.withLock {
        val nuevo = intercambioDeOError(id).copy(estado = EstadoDeIntercambioReal.POR_ESCRIBIR)
        pendienteFlow.value = nuevo
        persistirEstado()
        nuevo
    }

    suspend fun cambios(id: Long): CambiosDeIntercambioReal = mutex.withLock {
        val actual = intercambioDeOError(id)
        val nombreA = nombreDeArchivoDePartida(actual.juegoA)
        val nombreB = nombreDeArchivoDePartida(actual.juegoB)

        val resumenOriginalA = lector.resumir(archivoDeIntercambio(id, "a-original").readBytes(), nombreA)
        val resumenActualA = lector.resumir(archivoDeIntercambio(id, "a-actual").readBytes(), nombreA)
        val resumenOriginalB = lector.resumir(archivoDeIntercambio(id, "b-original").readBytes(), nombreB)
        val resumenActualB = lector.resumir(archivoDeIntercambio(id, "b-actual").readBytes(), nombreB)

        CambiosDeIntercambioReal(
            resumenA = resumenActualA,
            resumenB = resumenActualB,
            cambiosA = diferencias(resumenOriginalA, resumenActualA),
            cambiosB = diferencias(resumenOriginalB, resumenActualB),
        )
    }

    suspend fun escribir(sesion: SesionDeConsola, id: Long): Unit = mutex.withLock {
        val actual = intercambioDeOError(id)
        val guardoA = actual.vecesGuardadaA > 0
        val guardoB = actual.vecesGuardadaB > 0

        when {
            guardoA && guardoB -> {
                val bytesA = archivoDeIntercambio(id, "a-actual").readBytes()
                val bytesB = archivoDeIntercambio(id, "b-actual").readBytes()
                ejecutor.escribirEnLaConsola(sesion, actual.juegoA, actual.baseA, actual.juegoB, actual.baseB, PartidasIntercambiadas(bytesA, bytesB))
            }
            guardoA -> {
                val bytesA = archivoDeIntercambio(id, "a-actual").readBytes()
                escritor.escribir(sesion, actual.juegoA, bytesA, actual.baseA)
            }
            guardoB -> {
                val bytesB = archivoDeIntercambio(id, "b-actual").readBytes()
                escritor.escribir(sesion, actual.juegoB, bytesB, actual.baseB)
            }
            // ninguna guardó: no se escribe nada en la consola, solo se termina.
        }

        // Solo se llega aquí si todo lo de arriba salió bien: si algo lanzó, el intercambio sigue
        // pendiente tal cual (se puede reintentar o descartar) y la excepción sale sin tocar nada.
        quitarIntercambioDelEstado()
        carpetaDeIntercambio(id).deleteRecursively()
    }

    private fun descartarIntercambioSinLock(intercambio: IntercambioReal) {
        moverADescartadas(archivoDeIntercambio(intercambio.id, "a-actual"), intercambio.juegoA, sufijo = "a")
        moverADescartadas(archivoDeIntercambio(intercambio.id, "b-actual"), intercambio.juegoB, sufijo = "b")
        quitarIntercambioDelEstado()
        carpetaDeIntercambio(intercambio.id).deleteRecursively()
    }

    /** [Prestamos.descartar]. */
    suspend fun descartarPrestamo(juegoId: Long): Unit = mutex.withLock {
        prestamoDeOError(juegoId)
        descartarPrestamoSinLock(juegoId)
    }

    /** [IntercambiosReales.descartar]. */
    suspend fun descartarIntercambio(id: Long): Unit = mutex.withLock {
        descartarIntercambioSinLock(intercambioDeOError(id))
    }

    private fun quitarIntercambioDelEstado() {
        pendienteFlow.value = null
        persistirEstado()
    }

    private fun intercambioDeOError(id: Long): IntercambioReal =
        pendienteFlow.value?.takeIf { it.id == id } ?: throw ErrorDeJuego.NoEstaEnElTelefono(id)

    private fun ladoDe(jugador: Int): String = if (jugador == 0) "a" else "b"

    // ---------------------------------------------------------------------------------------
    // Comunes
    // ---------------------------------------------------------------------------------------

    private suspend fun juegoRespaldado(juegoId: Long): JuegoRespaldado? =
        repositorio.juegos().first().firstOrNull { it.id == juegoId }

    private suspend fun nombreDeArchivoDePartida(juegoId: Long): String =
        juegoRespaldado(juegoId)?.rutaDePartida?.substringAfterLast('/') ?: ""

    /**
     * "Última versión respaldada de la partida" (la más reciente de tipo PARTIDA de
     * [RepositorioDeRespaldos.versiones]) y sus bytes; base y partida vacía si no hay ninguna.
     */
    private suspend fun ultimaPartidaDe(juegoId: Long): Pair<String?, ByteArray> {
        val version = repositorio.versiones(juegoId).first().firstOrNull { it.tipo == TipoDeArchivo.PARTIDA }
        val bytes = version?.let { repositorio.bytesDe(it.id) } ?: ByteArray(0)
        return version?.sha256 to bytes
    }

    private fun carpetaDePrestamo(juegoId: Long): File = File(File(carpeta, "prestamos"), juegoId.toString())

    private fun archivoDePrestamo(juegoId: Long, nombre: String): File = File(carpetaDePrestamo(juegoId), "$nombre.sav")

    private fun carpetaDeIntercambio(id: Long): File = File(File(carpeta, "intercambio"), id.toString())

    private fun archivoDeIntercambio(id: Long, nombre: String): File = File(carpetaDeIntercambio(id), "$nombre.sav")

    /** Mueve (no borra) lo descartado a `carpeta/descartadas/<fecha-hora>-<juegoId>[-<sufijo>].sav`. */
    private fun moverADescartadas(origen: File, juegoId: Long, sufijo: String?) {
        if (!origen.exists()) return
        val carpetaDescartadas = File(carpeta, "descartadas")
        carpetaDescartadas.mkdirs()
        val nombre = buildString {
            append(formatoDeFechaParaCarpeta(reloj.ahoraMs()))
            append('-')
            append(juegoId)
            if (sufijo != null) {
                append('-')
                append(sufijo)
            }
            append(".sav")
        }
        Files.move(origen.toPath(), File(carpetaDescartadas, nombre).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
