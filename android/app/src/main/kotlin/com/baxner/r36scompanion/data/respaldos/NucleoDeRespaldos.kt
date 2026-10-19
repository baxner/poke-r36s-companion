package com.baxner.r36scompanion.data.respaldos

import androidx.room.withTransaction

/** `/roms/<sistema>`: dónde viven las ROMs, partidas y estados de un sistema en la consola. */
internal const val CarpetaRoms = "/roms"

/** Único sistema que respalda esta fase (ver docs/plan.md, fase 2: GBA primero). */
internal const val SistemaGba = "gba"

internal fun carpetaDelSistema(sistema: String): String = "$CarpetaRoms/$sistema"

internal fun rutaDePartidaDe(sistema: String, nombre: String): String = "${carpetaDelSistema(sistema)}/$nombre.srm"

/**
 * Lo que comparten [RepositorioDeRespaldosImpl] y [EscritorDePartidasImpl]: encontrar o crear la
 * fila de un juego y registrar una nueva versión suya (guardar el blob, calcular su
 * [EstadoDePartida] y [ResumenGuardado] si es una partida, y mantener al día las columnas
 * resumidas de `juegos`). No implementa ninguno de los dos contratos por sí solo.
 */
internal class NucleoDeRespaldos(
    private val db: RespaldosDatabase,
    private val almacen: AlmacenDeBlobs,
    private val lector: LectorDeResumen,
    private val reloj: Reloj,
) {
    private val dao get() = db.respaldosDao()

    suspend fun obtenerOCrearJuego(sistema: String, nombre: String): JuegoEntity {
        dao.buscarJuego(sistema, nombre)?.let { return it }
        val nuevo = JuegoEntity(
            sistema = sistema,
            nombre = nombre,
            rutaDePartida = rutaDePartidaDe(sistema, nombre),
            esPokemon = pareceDePokemon(nombre),
            editable = false,
            ultimoRespaldo = null,
            resumenJson = null,
            estadoDePartida = EstadoDePartida.NO_APLICA,
        )
        val id = dao.insertarJuego(nuevo)
        return nuevo.copy(id = id)
    }

    /**
     * Guarda [bytes] en el almacén y registra la versión en `versiones`. Si [tipo] es
     * [TipoDeArchivo.PARTIDA], además decide [EstadoDePartida] (todo `0xFF` → VACIA, si no se le
     * pide el resumen a [lector]) y deja esas columnas al día en la fila de [juego] — son las que
     * lee [JuegoEntity.aDominio] para listar juegos sin tener que releer `versiones`.
     */
    suspend fun registrarVersion(
        juego: JuegoEntity,
        tipo: TipoDeArchivo,
        rutaEnConsola: String,
        bytes: ByteArray,
        modificadoEnConsola: Long,
        origen: OrigenDeVersion,
    ): VersionRespaldada {
        val hash = almacen.guardar(bytes)
        val nombreDeArchivo = rutaEnConsola.substringAfterLast('/')
        val (estadoDePartida, resumen) = if (tipo == TipoDeArchivo.PARTIDA) {
            estadoYResumenDePartida(bytes, nombreDeArchivo)
        } else {
            EstadoDePartida.NO_APLICA to null
        }
        val ahora = reloj.ahoraMs()
        val resumenJson = resumen?.let { RespaldosJson.encodeToString(ResumenGuardado.serializer(), it) }

        val entidad = VersionEntity(
            juegoId = juego.id,
            tipo = tipo,
            rutaEnConsola = rutaEnConsola,
            sha256 = hash,
            tamano = bytes.size.toLong(),
            modificadoEnConsola = modificadoEnConsola,
            respaldadaEn = ahora,
            origen = origen,
            estadoDePartida = estadoDePartida,
            resumenJson = resumenJson,
        )

        val idDeLaVersion = db.withTransaction {
            val nuevoId = dao.insertarVersion(entidad)
            if (tipo == TipoDeArchivo.PARTIDA) {
                dao.actualizarResumenDeJuego(
                    id = juego.id,
                    esPokemon = resumen != null || pareceDePokemon(juego.nombre),
                    editable = resumen != null,
                    estadoDePartida = estadoDePartida,
                    resumenJson = resumenJson,
                    ultimoRespaldo = ahora,
                )
            } else {
                dao.tocarUltimoRespaldo(juego.id, ahora)
            }
            nuevoId
        }
        return entidad.copy(id = idDeLaVersion).aDominio()
    }

    private suspend fun estadoYResumenDePartida(bytes: ByteArray, nombreDeArchivo: String): Pair<EstadoDePartida, ResumenGuardado?> =
        if (esPartidaVacia(bytes)) {
            EstadoDePartida.VACIA to null
        } else {
            EstadoDePartida.CON_DATOS to lector.resumir(bytes, nombreDeArchivo)
        }
}
