package com.baxner.r36scompanion.data.respaldos

import com.baxner.r36scompanion.data.consola.SesionDeConsola
import java.io.IOException

/**
 * Implementación real de [EscritorDePartidas] (ver su KDoc en Respaldos.kt y docs/plan.md,
 * "Reglas de seguridad para no perder una partida"). [db], [almacen], [lector] y [reloj] son las
 * mismas instancias que usa [RepositorioDeRespaldosImpl] (ver su KDoc).
 */
class EscritorDePartidasImpl(
    private val db: RespaldosDatabase,
    private val almacen: AlmacenDeBlobs,
    private val lector: LectorDeResumen,
    private val reloj: Reloj,
) : EscritorDePartidas {
    private val dao get() = db.respaldosDao()
    private val nucleo = NucleoDeRespaldos(db, almacen, lector, reloj)

    override suspend fun escribir(sesion: SesionDeConsola, juegoId: Long, bytes: ByteArray, base: String?): ResultadoDeEscritura {
        val juego = dao.juegoPorId(juegoId) ?: throw NoSuchElementException("no existe el juego $juegoId")
        val carpeta = carpetaDelSistema(juego.sistema)

        // (a) el juego no puede estar abierto: su partida en memoria pisaría lo que se escriba.
        val abierto = sesion.juegosAbiertos().firstOrNull { nombreBaseDeRom(it.rom) == juego.nombre }
        if (abierto != null) throw ErrorDeEscritura.JuegoAbierto(abierto.rom)

        // (b) si se dio una base, la partida de la consola tiene que ser esa.
        val partidaActual = sesion.info(juego.rutaDePartida)?.let { info -> sesion.leer(juego.rutaDePartida) to info.modificado }
        val hashActual = partidaActual?.let { (bytesActuales, _) -> AlmacenDeBlobs.sha256Hex(bytesActuales) }
        if (base != null && hashActual != base) {
            throw ErrorDeEscritura.Conflicto(esperado = base, encontrado = hashActual)
        }

        // (c) se respalda lo que había, si había algo (si no había partida, copiaPrevia queda null).
        val copiaPrevia = partidaActual?.let { (bytesActuales, modificado) ->
            nucleo.registrarVersion(juego, TipoDeArchivo.PARTIDA, juego.rutaDePartida, bytesActuales, modificado, OrigenDeVersion.ANTES_DE_ESCRIBIR)
        }

        // (d) se aparta (nunca se borra) el estado automático: si no, RetroArch retoma desde ahí
        // al abrir el juego y la edición se pierde en cuanto se vuelva a jugar.
        val rutaDelEstadoAutomatico = "$carpeta/${juego.nombre}.state.auto"
        val rutaApartada = if (sesion.info(rutaDelEstadoAutomatico) != null) {
            val carpetaDeApartados = "$carpeta/.pr36c/apartados/${formatoDeFechaParaCarpeta(reloj.ahoraMs())}"
            sesion.crearCarpetas(carpetaDeApartados)
            val destino = "$carpetaDeApartados/${juego.nombre}.state.auto"
            sesion.mover(rutaDelEstadoAutomatico, destino)
            destino
        } else {
            null
        }

        // (e) escritura segura; si falla después de apartar el estado, se intenta devolverlo.
        try {
            sesion.escribirSeguro(juego.rutaDePartida, bytes)
        } catch (original: Exception) {
            throw errorDeEscrituraConReversion(sesion, juego.rutaDePartida, rutaApartada, rutaDelEstadoAutomatico, original)
        }

        // (f) se registra lo escrito (con su resumen, si el motor la reconoce) y se informa qué
        // quedó apartado y qué estados manuales siguen ahí (si el usuario carga uno, pierde la edición).
        val infoEscrita = sesion.info(juego.rutaDePartida)
            ?: error("la partida no existe justo después de escribirla en ${juego.rutaDePartida}")
        val versionEscrita =
            nucleo.registrarVersion(juego, TipoDeArchivo.PARTIDA, juego.rutaDePartida, bytes, infoEscrita.modificado, OrigenDeVersion.ESCRITA_POR_LA_APP)

        val estadosManuales = sesion.listar(carpeta)
            .filter { !it.esCarpeta }
            .mapNotNull { archivo -> tipoYNombreDeArchivo(archivo.nombre)?.let { (tipo, nombre) -> Triple(archivo.ruta, tipo, nombre) } }
            .filter { (_, tipo, nombre) -> tipo == TipoDeArchivo.ESTADO && nombre == juego.nombre }
            .map { (ruta, _, _) -> ruta }

        return ResultadoDeEscritura(
            versionEscrita = versionEscrita,
            copiaPrevia = copiaPrevia,
            estadosApartados = listOfNotNull(rutaApartada),
            estadosManuales = estadosManuales,
        )
    }

    /**
     * No exige una base concreta (se llama a [escribir] con `base = null`, que se salta el paso
     * (b)), pero sí respalda lo que hubiera en la consola — eso lo hace igual [escribir] en su
     * paso (c), sin importar `base`. Es una decisión explícita del usuario: si lo que hay en la
     * consola no es lo que se esperaba, se respalda de todos modos y se sigue adelante.
     */
    override suspend fun restaurar(sesion: SesionDeConsola, versionId: Long): ResultadoDeEscritura {
        val version = dao.versionPorId(versionId) ?: throw NoSuchElementException("no existe la versión $versionId")
        val bytes = almacen.leer(version.sha256)
        return escribir(sesion, version.juegoId, bytes, base = null)
    }

    /** Si ya se había apartado el estado automático, intenta devolverlo antes de contar el fallo. */
    private suspend fun errorDeEscrituraConReversion(
        sesion: SesionDeConsola,
        rutaDePartida: String,
        rutaApartada: String?,
        rutaOriginalDelEstado: String,
        original: Exception,
    ): Exception {
        if (rutaApartada == null) {
            return IOException("no se pudo escribir la partida en $rutaDePartida: ${original.message}", original)
        }
        return try {
            sesion.mover(rutaApartada, rutaOriginalDelEstado)
            IOException(
                "no se pudo escribir la partida en $rutaDePartida (el estado automático se devolvió a $rutaOriginalDelEstado): ${original.message}",
                original,
            )
        } catch (fallaAlDevolver: Exception) {
            IOException(
                "no se pudo escribir la partida en $rutaDePartida y tampoco se pudo devolver el estado automático a " +
                    "$rutaOriginalDelEstado (quedó en $rutaApartada): ${original.message} / ${fallaAlDevolver.message}",
                original,
            )
        }
    }
}
