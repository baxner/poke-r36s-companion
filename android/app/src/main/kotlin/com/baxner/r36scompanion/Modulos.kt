package com.baxner.r36scompanion

import android.content.Context
import com.baxner.r36scompanion.data.banco.Banco
import com.baxner.r36scompanion.data.banco.BancoConMotor
import com.baxner.r36scompanion.data.banco.BancoDatabase
import com.baxner.r36scompanion.data.conexion.ConexionConConsola
import com.baxner.r36scompanion.data.conexion.PreferenciasDeConsola
import com.baxner.r36scompanion.data.conexion.PreferenciasDeConsolaCifradas
import com.baxner.r36scompanion.data.consola.Anclaje
import com.baxner.r36scompanion.data.consola.AnclajeAndroid
import com.baxner.r36scompanion.data.consola.BuscadorDeConsola
import com.baxner.r36scompanion.data.consola.BuscadorPorAnclaje
import com.baxner.r36scompanion.data.consola.ConectorDeConsola
import com.baxner.r36scompanion.data.consola.ConectorDeConsolaJsch
import com.baxner.r36scompanion.data.editor.EditorDePartidas
import com.baxner.r36scompanion.data.editor.EditorDePartidasConMotor
import com.baxner.r36scompanion.data.intercambio.EjecutorDeIntercambio
import com.baxner.r36scompanion.data.intercambio.EjecutorDeIntercambioConEscritor
import com.baxner.r36scompanion.data.intercambio.Intercambios
import com.baxner.r36scompanion.data.intercambio.IntercambiosConMotor
import com.baxner.r36scompanion.data.motor.MotorPartidas
import com.baxner.r36scompanion.data.motor.MotorWebView
import com.baxner.r36scompanion.data.pokedex.PokedexGlobal
import com.baxner.r36scompanion.data.pokedex.PokedexGlobalConRespaldos
import com.baxner.r36scompanion.data.preferencias.PreferenciasDeLaApp
import com.baxner.r36scompanion.data.preferencias.PreferenciasDeLaAppSharedPrefs
import com.baxner.r36scompanion.data.respaldos.AlmacenDeBlobs
import com.baxner.r36scompanion.data.respaldos.EscritorDePartidas
import com.baxner.r36scompanion.data.respaldos.EscritorDePartidasImpl
import com.baxner.r36scompanion.data.respaldos.LectorDeResumen
import com.baxner.r36scompanion.data.respaldos.LectorDeResumenConMotor
import com.baxner.r36scompanion.data.respaldos.Reloj
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldos
import com.baxner.r36scompanion.data.respaldos.RepositorioDeRespaldosImpl
import com.baxner.r36scompanion.data.respaldos.RespaldosDatabase
import com.baxner.r36scompanion.data.textos.Textos
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor manual de dependencias (sin librerías de DI: ver el encargo de esta fase). Una sola
 * instancia por proceso, creada por [CompanionApplication] y compartida por todas las pantallas:
 * así hay una sola base de datos, un solo almacén de blobs y, sobre todo, una sola
 * [ConexionConConsola] (su sesión SSH la usan respaldos, historial y, en fases futuras, el editor
 * y los intercambios).
 *
 * [alcanceDeProceso] vive mientras viva la app: no se cancela nunca a mano (no hay un "fin" del
 * proceso que valga la pena capturar); Android lo libera al matar el proceso.
 */
class Modulos(context: Context) {
    private val appContext = context.applicationContext

    val textos: Textos = Textos.de(appContext)

    private val motor = MotorWebView.obtener(appContext)
    val motorPartidas = MotorPartidas(motor)

    private val anclaje: Anclaje = AnclajeAndroid(appContext)
    private val buscador: BuscadorDeConsola = BuscadorPorAnclaje(anclaje)
    private val conector: ConectorDeConsola = ConectorDeConsolaJsch()
    val preferenciasDeConsola: PreferenciasDeConsola = PreferenciasDeConsolaCifradas(appContext)
    val preferenciasDeLaApp: PreferenciasDeLaApp = PreferenciasDeLaAppSharedPrefs(appContext)

    private val alcanceDeProceso = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Una sola conexión para toda la app (ver el KDoc de la clase). */
    val conexion = ConexionConConsola(anclaje, buscador, conector, preferenciasDeConsola, alcanceDeProceso)

    private val db = RespaldosDatabase.crear(appContext)
    private val almacenDeBlobs = AlmacenDeBlobs(File(appContext.filesDir, "respaldos/blobs"))
    private val lectorDeResumen: LectorDeResumen = LectorDeResumenConMotor(motorPartidas)
    private val reloj: Reloj = Reloj { System.currentTimeMillis() }

    val repositorioDeRespaldos: RepositorioDeRespaldos = RepositorioDeRespaldosImpl(db, almacenDeBlobs, lectorDeResumen, reloj)
    val escritorDePartidas: EscritorDePartidas = EscritorDePartidasImpl(db, almacenDeBlobs, lectorDeResumen, reloj)

    // Fase 4: banco, intercambio rápido y Pokédex global (ver docs/plan.md y el encargo de esta fase).
    private val bancoDb = BancoDatabase.crear(appContext)
    val banco: Banco = BancoConMotor(bancoDb, motorPartidas, reloj)
    /** El editor (fase 3): una [com.baxner.r36scompanion.data.editor.SesionDeEdicion] por juego abierto, nunca compartida entre pantallas de distintos juegos. */
    val editorDePartidas: EditorDePartidas = EditorDePartidasConMotor(motorPartidas)
    val intercambios: Intercambios = IntercambiosConMotor(motorPartidas)
    val ejecutorDeIntercambio: EjecutorDeIntercambio = EjecutorDeIntercambioConEscritor(escritorDePartidas)
    val pokedexGlobal: PokedexGlobal = PokedexGlobalConRespaldos(repositorioDeRespaldos, banco, motorPartidas, textos::lugarGen3DesdeIngles)
}
