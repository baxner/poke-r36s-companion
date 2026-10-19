package com.baxner.r36scompanion.data.consola

import kotlinx.coroutines.flow.Flow

/*
 * Contrato de la conexión con la consola (fase 1). Lo escribe Opus; la implementación vive en
 * este mismo paquete y el resto de la app (respaldos, escritura segura, diagnóstico) solo usa
 * estas interfaces. Ver docs/plan.md, "Anclaje USB y consola automática" y "Reglas de
 * seguridad", y lo medido en la consola real en "Primeras pruebas con las partidas reales".
 *
 * Lo que se sabe de la consola del usuario (ArkOS R3XS V2.0, 2026-10-19): SSH de OpenSSH en el 22
 * con llave ED25519, usuario `ark` / contraseña `ark`, equipo `rg351mp`; juegos en `/roms/<sistema>/`
 * (exFAT, nombres con espacios, paréntesis, comas y acentos); partidas `.srm` y estados
 * `.state`, `.state1`… `.state.auto` junto a las ROMs; RetroArch en `~/.config/retroarch/` y
 * `~/.config/retroarch32/`. La IP cambia en cada conexión (10.101.33.x con el S24).
 */

/** Una interfaz de anclaje USB del teléfono (`rndis0`, `ncm0`, `usb0`…) con su IPv4. */
data class InterfazDeAnclaje(val nombre: String, val direccion: String, val prefijo: Int)

/** Algo con el puerto SSH abierto en la red de una interfaz de anclaje: quizá la consola. */
data class Candidata(val host: String, val puerto: Int = PuertoSsh, val interfaz: String)

/** Usuario y contraseña de la consola. ArkOS trae `ark` / `ark` de fábrica. */
data class Credenciales(val usuario: String = "ark", val contrasena: String = "ark")

/**
 * La consola ya identificada. [huella] es la de su llave SSH en el formato de OpenSSH
 * (`SHA256:<base64 sin '='>`): se fija la primera vez y desde ahí se exige, como el certificado
 * del agente en Vigía.
 */
data class Consola(val host: String, val puerto: Int, val huella: String, val algoritmoDeLlave: String, val nombreDeEquipo: String)

data class ArchivoRemoto(
    val ruta: String,
    val nombre: String,
    val tamano: Long,
    /** Segundos desde 1970 (UTC), como los da SFTP. */
    val modificado: Long,
    val esCarpeta: Boolean,
)

/** Un juego que RetroArch tiene abierto ahora mismo (sacado de `ps`). */
data class JuegoAbierto(val emulador: String, val nucleo: String?, val rom: String)

/**
 * Los ajustes de RetroArch que deciden dónde quedan las partidas y si un estado automático
 * las pisa al abrir el juego. null = el ajuste no aparece en el archivo.
 */
data class AjustesRetroArch(
    val archivo: String,
    val cargaEstadoAutomatico: Boolean?,
    val guardaEstadoAutomatico: Boolean?,
    val partidasJuntoALaRom: Boolean?,
    val estadosJuntoALaRom: Boolean?,
    val carpetaDePartidas: String?,
    val carpetaDeEstados: String?,
    /** Segundos; 0 = la partida solo se escribe al cerrar el juego. */
    val intervaloDeAutoguardado: Int?,
)

/** Por qué falló algo con la consola, con lo necesario para explicarlo en la pantalla. */
sealed class ErrorDeConsola(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa) {
    /** No contesta (no hay anclaje, la consola está apagada o sin "Enable Remote Services"). */
    class SinConexion(mensaje: String, causa: Throwable? = null) : ErrorDeConsola(mensaje, causa)

    /** La llave SSH no es la fijada: otro equipo, o la consola se reinstaló. La contraseña no se mandó. */
    class HuellaDistinta(val esperada: String, val recibida: String) :
        ErrorDeConsola("la llave de la consola cambió (esperada $esperada, recibida $recibida)")

    /** Usuario o contraseña incorrectos. */
    class CredencialesIncorrectas(causa: Throwable? = null) : ErrorDeConsola("usuario o contraseña incorrectos", causa)

    /** La ruta no existe. */
    class NoExiste(val ruta: String) : ErrorDeConsola("no existe: $ruta")

    /** Ya existe algo en el destino de un [SesionDeConsola.mover]: no se pisa nada. */
    class YaExiste(val ruta: String) : ErrorDeConsola("ya existe: $ruta")

    /** El archivo pasa del límite pedido a [SesionDeConsola.leer]. */
    class MuyGrande(val ruta: String, val tamano: Long) : ErrorDeConsola("muy grande: $ruta ($tamano bytes)")

    /** Lo escrito no coincide con lo que se quería escribir; el original quedó intacto. */
    class VerificacionFallida(val ruta: String, mensaje: String) : ErrorDeConsola(mensaje)
}

/** Encuentra la consola en la red del anclaje USB (la IP cambia en cada conexión). */
interface BuscadorDeConsola {
    /** Interfaces de anclaje activas ahora, con IPv4. Vacío = el anclaje está apagado. */
    fun interfacesDeAnclaje(): List<InterfazDeAnclaje>

    /**
     * Busca el puerto SSH en la subred de cada interfaz de anclaje (como mucho una /24 alrededor
     * de la IP del teléfono, sin contarlo a él). Rápido (todo en paralelo, con tiempo límite
     * corto por equipo) y sin autenticar a nadie: solo dice quién tiene el puerto abierto.
     */
    suspend fun buscar(): List<Candidata>
}

/** Entra a una consola por SSH. */
interface ConectorDeConsola {
    /**
     * Conecta y autentica con [credenciales]. Con [huellaFijada] null es la primera vez: se
     * acepta la llave que presente y su huella queda en [SesionDeConsola.consola] para guardarla.
     * Con [huellaFijada], si la llave no coincide lanza [ErrorDeConsola.HuellaDistinta]
     * **antes de mandar la contraseña**.
     */
    suspend fun conectar(candidata: Candidata, credenciales: Credenciales, huellaFijada: String?): SesionDeConsola
}

/** Una conexión abierta con la consola. Todas las rutas son absolutas en la consola. */
interface SesionDeConsola : AutoCloseable {
    val consola: Consola

    suspend fun listar(carpeta: String): List<ArchivoRemoto>

    /** null si no existe. */
    suspend fun info(ruta: String): ArchivoRemoto?

    suspend fun leer(ruta: String, maximo: Long = 64L * 1024 * 1024): ByteArray

    /**
     * Reemplaza [ruta] por [bytes] sin dejarla nunca a medias: sube a un temporal en la misma
     * carpeta, lo vuelve a leer y compara, cambia el original por el temporal, vuelve a leer el
     * resultado y compara, y fuerza la escritura a la tarjeta (`sync`). Si cualquier paso falla,
     * [ruta] queda como estaba y no quedan temporales. Si [ruta] no existía, la crea igual.
     */
    suspend fun escribirSeguro(ruta: String, bytes: ByteArray)

    /** Mueve o renombra. Nunca pisa: si [hacia] existe lanza [ErrorDeConsola.YaExiste]. */
    suspend fun mover(desde: String, hacia: String)

    suspend fun crearCarpetas(carpeta: String)

    /** Los juegos que RetroArch tiene abiertos ahora (normalmente ninguno o uno). */
    suspend fun juegosAbiertos(): List<JuegoAbierto>

    /** Los ajustes de cada `retroarch.cfg` que exista (RetroArch de 64 y de 32 bits). */
    suspend fun ajustesRetroArch(): List<AjustesRetroArch>

    override fun close()
}

/** Si el teléfono tiene el cable y el anclaje USB activos (lo que puede saber una app normal). */
data class EstadoDelAnclaje(
    /** El teléfono está conectado por USB a algo (la consola le da corriente al conectarse). */
    val cableConectado: Boolean,
    /** Hay una interfaz de anclaje con IPv4: el anclaje USB está prendido. */
    val anclajeActivo: Boolean,
    val interfaces: List<InterfazDeAnclaje>,
)

/**
 * Android no deja que una app normal prenda el anclaje (`TetheringManager.startTethering`
 * necesita `TETHER_PRIVILEGED`): la app lo detecta y lleva al usuario a la pantalla correcta.
 */
interface Anclaje {
    /** Se actualiza mientras alguien lo observe (sondeo corto + avisos del sistema). */
    fun observar(): Flow<EstadoDelAnclaje>

    fun estadoActual(): EstadoDelAnclaje

    /** Abre la pantalla de anclaje del teléfono (la de Samsung si existe, si no la genérica). Devuelve si pudo. */
    fun abrirAjustesDeAnclaje(): Boolean
}

const val PuertoSsh = 22
