/*
 * Uno o dos Game Boy Advance emulados con mGBA 0.10.5, para PokeR36S Companion.
 *
 * Una "sala" tiene uno o dos juegos. Cada juego corre en su propio hilo de mGBA (mCoreThread);
 * los dos comparten la salida de sonido (AAudio) y, si la sala es con cable, el cable link de
 * mGBA (GBASIOLockstep), igual que la ventana de varios jugadores de mGBA de escritorio: los
 * callbacks del cable son los de src/platform/qt/MultiplayerController.cpp pasados a C.
 *
 * La partida va en memoria, del mismo tamaño fijo que le da el núcleo de RetroArch de la R36S
 * (128 KB, VFileFromMemory), para que lo que sale de aquí sea byte por byte lo que ese núcleo
 * espera. Cuando el juego termina de guardar (savedataUpdated de mGBA), se copia a
 * "partidaEstable": esa copia es la que se entrega, nunca una a medio escribir.
 *
 * El ritmo lo marca el sonido (como en mGBA de escritorio): el núcleo espera a que AAudio se
 * lleve las muestras. Si no hay sonido, cada juego duerme hasta su siguiente cuadro (60 por s).
 */
#include <mgba/flags.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/core.h>
#include <mgba/core/thread.h>
#include <mgba/gba/core.h>
#include <mgba/internal/gba/audio.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/io.h>
#include <mgba/internal/gba/sio/lockstep.h>
#include <mgba-util/vfs.h>

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define ETIQUETA "emulador"
#define AVISO(...) __android_log_print(ANDROID_LOG_INFO, ETIQUETA, __VA_ARGS__)

#define ANCHO 240
#define ALTO 160
#define MAX_JUEGOS 2
/* SIZE_CART_FLASH1M: lo que reserva el núcleo de RetroArch para cualquier partida de GBA. */
#define TAM_MEMORIA_PARTIDA 0x20000
#define NS_POR_CUADRO (1000000000LL / 60)
/* Muestras por bloque de sonido: ~21 ms a 48 kHz. */
#define MUESTRAS_POR_BLOQUE 1024
/* El búfer de mGBA es de 0x4000 muestras: si nadie se lleva el sonido, se tira antes de llenarse. */
#define MUESTRAS_DE_SOBRA 8192
#define MAX_CUADROS_DE_AUDIO 4096

struct Sala;

struct Juego {
	struct Sala* sala;
	struct mCore* core;
	struct mCoreThread hilo;
	atomic_bool arrancado;
	color_t* video;

	pthread_mutex_t ventanaCandado;
	ANativeWindow* ventana;
	int escala;
	int escalaPuesta;

	uint8_t* memoriaPartida;
	size_t tamanoOriginal;
	pthread_mutex_t partidaCandado;
	uint8_t* partidaEstable;
	size_t tamanoEstable;
	atomic_int versionPartida;

	atomic_uint teclas;
	atomic_llong cuadros;
	int64_t siguienteCuadro;

	/* Cable link (solo en una sala con cable). Los campos son los del Player de Qt. */
	struct GBASIOLockstepNode nodo;
	bool conectado;
	int despierto;
	int32_t ciclosPublicados;
	unsigned mascaraEspera;
	/* Si sigue el reloj (sonido o cuadros). El cable lo apaga mientras lleva él el ritmo. */
	bool sincronizado;
};

struct Sala {
	struct Juego juegos[MAX_JUEGOS];
	int cuantos;
	bool conCable;
	bool arrancada;
	bool pausada;
	bool cerrada;

	struct GBASIOLockstep enlace;
	pthread_mutex_t enlaceCandado;

	pthread_mutex_t audioCandado;
	AAudioStream* audio;
	atomic_bool audioActivo;
	int tasaDeAudio;
	atomic_int audible;
	int16_t descarte[MAX_CUADROS_DE_AUDIO * 2];
};

static int64_t _ahoraNs(void) {
	struct timespec t;
	clock_gettime(CLOCK_MONOTONIC, &t);
	return (int64_t) t.tv_sec * 1000000000LL + t.tv_nsec;
}

/* El CoreController::setSync de Qt. */
static void _sincronizar(struct Juego* juego, bool si) {
	juego->sincronizado = si;
	struct mCoreThreadInternal* impl = juego->hilo.impl;
	if (!impl) {
		return;
	}
	impl->sync.audioWait = si && atomic_load(&juego->sala->audioActivo);
	impl->sync.videoFrameWait = false;
}

/* Prende o apaga el ritmo por sonido en todos los juegos; al apagarlo despierta al que esperaba. */
static void _ponerAudioActivo(struct Sala* sala, bool activo) {
	atomic_store(&sala->audioActivo, activo);
	for (int i = 0; i < sala->cuantos; ++i) {
		struct Juego* juego = &sala->juegos[i];
		if (!atomic_load(&juego->arrancado)) {
			continue;
		}
		struct mCoreSync* sync = &juego->hilo.impl->sync;
		mCoreSyncLockAudio(sync);
		sync->audioWait = activo && juego->sincronizado;
		mCoreSyncConsumeAudio(sync);
	}
}

/* ---------- Cable link: callbacks de mLockstep (MultiplayerController.cpp de mGBA) ---------- */

static struct Juego* _jugador(struct Sala* sala, int id) {
	struct GBASIOLockstepNode* nodo = sala->enlace.players[id];
	for (int i = 0; i < sala->cuantos; ++i) {
		if (&sala->juegos[i].nodo == nodo) {
			return &sala->juegos[i];
		}
	}
	return &sala->juegos[0];
}

static void _enlaceCerrar(struct mLockstep* enlace) {
	struct Sala* sala = enlace->context;
	pthread_mutex_lock(&sala->enlaceCandado);
}

static void _enlaceAbrir(struct mLockstep* enlace) {
	struct Sala* sala = enlace->context;
	pthread_mutex_unlock(&sala->enlaceCandado);
}

static bool _enlaceAvisar(struct mLockstep* enlace, unsigned mascara) {
	struct Juego* primero = _jugador(enlace->context, 0);
	bool desperto = false;
	primero->mascaraEspera &= ~mascara;
	if (!primero->mascaraEspera && primero->despierto < 1) {
		mCoreThreadStopWaiting(&primero->hilo);
		primero->despierto = 1;
		desperto = true;
	}
	return desperto;
}

static bool _enlaceEsperar(struct mLockstep* enlace, unsigned mascara) {
	struct Juego* primero = _jugador(enlace->context, 0);
	bool durmio = false;
	primero->mascaraEspera |= mascara;
	if (primero->despierto > 0) {
		mCoreThreadWaitFromThread(&primero->hilo);
		primero->despierto = 0;
		durmio = true;
	}
	_sincronizar(primero, true);
	return durmio;
}

static void _enlaceSumarCiclos(struct mLockstep* enlace, int id, int32_t ciclos) {
	if (ciclos < 0) {
		abort();
	}
	struct Sala* sala = enlace->context;
	if (!id) {
		for (int i = 1; i < enlace->attached; ++i) {
			struct Juego* juego = _jugador(sala, i);
			if (juego->nodo.d.p->mode > SIO_MULTI) {
				_sincronizar(juego, true);
				continue;
			}
			_sincronizar(juego, false);
			juego->ciclosPublicados += ciclos;
			if (juego->despierto < 1) {
				juego->nodo.nextEvent += juego->ciclosPublicados;
			}
			mCoreThreadStopWaiting(&juego->hilo);
			juego->despierto = 1;
		}
	} else {
		struct Juego* juego = _jugador(sala, id);
		_sincronizar(juego, true);
		juego->ciclosPublicados += ciclos;
	}
}

static int32_t _enlaceUsarCiclos(struct mLockstep* enlace, int id, int32_t ciclos) {
	struct Juego* juego = _jugador(enlace->context, id);
	juego->ciclosPublicados -= ciclos;
	if (juego->ciclosPublicados <= 0) {
		mCoreThreadWaitFromThread(&juego->hilo);
		juego->despierto = 0;
	}
	return juego->ciclosPublicados;
}

static int32_t _enlaceCiclosSinUsar(struct mLockstep* enlace, int id) {
	return _jugador(enlace->context, id)->ciclosPublicados;
}

static void _enlaceDescargar(struct mLockstep* enlace, int id) {
	struct Sala* sala = enlace->context;
	if (id) {
		struct Juego* juego = _jugador(sala, id);
		_sincronizar(juego, true);
		juego->ciclosPublicados = 0;

		/* Suelta al primero si estaba esperando a este. */
		struct Juego* primero = _jugador(sala, 0);
		primero->mascaraEspera &= ~(1u << id);
		if (!primero->mascaraEspera && primero->despierto < 1) {
			mCoreThreadStopWaiting(&primero->hilo);
			primero->despierto = 1;
		}
	} else {
		for (int i = 1; i < enlace->attached; ++i) {
			struct Juego* juego = _jugador(sala, i);
			_sincronizar(juego, true);
			juego->ciclosPublicados += sala->enlace.players[0]->eventDiff;
			if (juego->despierto < 1) {
				juego->nodo.nextEvent += juego->ciclosPublicados;
				mCoreThreadStopWaiting(&juego->hilo);
				juego->despierto = 1;
			}
		}
	}
}

/* ---------- Hilo de cada juego ---------- */

static void _alArrancar(struct mCoreThread* hilo) {
	struct Juego* juego = hilo->userData;
	struct Sala* sala = juego->sala;
	_sincronizar(juego, true);
	if (sala->conCable) {
		/* Como MultiplayerController::attachGame: el orden de llegada da el número de jugador. */
		struct GBA* gba = hilo->core->board;
		GBASIOLockstepNodeCreate(&juego->nodo);
		GBASIOLockstepAttachNode(&sala->enlace, &juego->nodo);
		GBASIOSetDriver(&gba->sio, &juego->nodo.d, SIO_MULTI);
		GBASIOSetDriver(&gba->sio, &juego->nodo.d, SIO_NORMAL_32);
		juego->conectado = true;
	}
}

static void _presentar(struct Juego* juego) {
	pthread_mutex_lock(&juego->ventanaCandado);
	ANativeWindow* ventana = juego->ventana;
	int e = juego->escala;
	if (ventana) {
		if (juego->escalaPuesta != e &&
		    ANativeWindow_setBuffersGeometry(ventana, ANCHO * e, ALTO * e, WINDOW_FORMAT_RGBX_8888) == 0) {
			juego->escalaPuesta = e;
		}
		ANativeWindow_Buffer lienzo;
		if (juego->escalaPuesta == e && ANativeWindow_lock(ventana, &lienzo, NULL) == 0) {
			if (lienzo.width >= ANCHO * e && lienzo.height >= ALTO * e && lienzo.format == WINDOW_FORMAT_RGBX_8888) {
				uint32_t* destino = lienzo.bits;
				size_t paso = (size_t) lienzo.stride;
				for (int y = 0; y < ALTO; ++y) {
					const color_t* fila = &juego->video[y * ANCHO];
					uint32_t* d = &destino[(size_t) y * e * paso];
					for (int x = 0; x < ANCHO; ++x) {
						uint32_t c = (uint32_t) fila[x] | 0xFF000000u;
						for (int k = 0; k < e; ++k) {
							*d++ = c;
						}
					}
					for (int r = 1; r < e; ++r) {
						memcpy(&destino[((size_t) y * e + r) * paso], &destino[(size_t) y * e * paso], (size_t) ANCHO * e * 4);
					}
				}
			}
			ANativeWindow_unlockAndPost(ventana);
		}
	}
	pthread_mutex_unlock(&juego->ventanaCandado);
}

static void _tirarSonidoDeSobra(struct Juego* juego) {
	struct mCore* core = juego->core;
	struct mCoreSync* sync = &juego->hilo.impl->sync;
	mCoreSyncLockAudio(sync);
	blip_t* izquierda = core->getAudioChannel(core, 0);
	if (blip_samples_avail(izquierda) > MUESTRAS_DE_SOBRA) {
		blip_clear(izquierda);
		blip_clear(core->getAudioChannel(core, 1));
	}
	mCoreSyncUnlockAudio(sync);
}

static void _esperarSiguienteCuadro(struct Juego* juego) {
	int64_t ahora = _ahoraNs();
	if (!juego->siguienteCuadro || ahora - juego->siguienteCuadro > 100000000LL) {
		juego->siguienteCuadro = ahora;
	}
	juego->siguienteCuadro += NS_POR_CUADRO;
	int64_t falta = juego->siguienteCuadro - ahora;
	if (falta > 0) {
		struct timespec t = { .tv_sec = falta / 1000000000LL, .tv_nsec = falta % 1000000000LL };
		nanosleep(&t, NULL);
	}
}

static void _alTerminarCuadro(struct mCoreThread* hilo) {
	struct Juego* juego = hilo->userData;
	hilo->core->setKeys(hilo->core, atomic_load(&juego->teclas));
	atomic_fetch_add(&juego->cuadros, 1);
	_presentar(juego);
	_tirarSonidoDeSobra(juego);
	if (!atomic_load(&juego->sala->audioActivo) && juego->sincronizado) {
		_esperarSiguienteCuadro(juego);
	}
}

/* El juego terminó de escribir su partida: se guarda una copia completa. */
static void _alGuardarElJuego(void* contexto) {
	struct Juego* juego = contexto;
	size_t tamano = juego->tamanoOriginal;
	if (!tamano) {
		struct GBA* gba = juego->core->board;
		tamano = GBASavedataSize(&gba->memory.savedata);
	}
	if (!tamano || tamano > TAM_MEMORIA_PARTIDA) {
		tamano = TAM_MEMORIA_PARTIDA;
	}
	pthread_mutex_lock(&juego->partidaCandado);
	memcpy(juego->partidaEstable, juego->memoriaPartida, tamano);
	juego->tamanoEstable = tamano;
	pthread_mutex_unlock(&juego->partidaCandado);
	atomic_fetch_add(&juego->versionPartida, 1);
}

/* ---------- Sonido ---------- */

static aaudio_data_callback_result_t _alPedirSonido(AAudioStream* flujo, void* datos, void* salida, int32_t cuadros) {
	(void) flujo;
	struct Sala* sala = datos;
	int16_t* destinoAudible = salida;
	memset(destinoAudible, 0, (size_t) cuadros * 2 * sizeof(int16_t));
	if (cuadros > MAX_CUADROS_DE_AUDIO) {
		cuadros = MAX_CUADROS_DE_AUDIO;
	}
	int audible = atomic_load(&sala->audible);
	for (int i = 0; i < sala->cuantos; ++i) {
		struct Juego* juego = &sala->juegos[i];
		if (!atomic_load(&juego->arrancado)) {
			continue;
		}
		struct mCore* core = juego->core;
		struct mCoreSync* sync = &juego->hilo.impl->sync;
		int16_t* destino = i == audible ? destinoAudible : sala->descarte;
		double relojFalso = 1;
		if (sync->fpsTarget > 0) {
			relojFalso = GBAAudioCalculateRatio(1, sync->fpsTarget, 1);
		}
		mCoreSyncLockAudio(sync);
		blip_t* izquierda = core->getAudioChannel(core, 0);
		blip_t* derecha = core->getAudioChannel(core, 1);
		blip_set_rates(izquierda, core->frequency(core), sala->tasaDeAudio * relojFalso);
		blip_set_rates(derecha, core->frequency(core), sala->tasaDeAudio * relojFalso);
		int disponibles = blip_samples_avail(izquierda);
		if (disponibles > cuadros) {
			disponibles = cuadros;
		}
		blip_read_samples(izquierda, destino, disponibles, 1);
		blip_read_samples(derecha, destino + 1, disponibles, 1);
		mCoreSyncConsumeAudio(sync);
	}
	return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void _alFallarSonido(AAudioStream* flujo, void* datos, aaudio_result_t error) {
	(void) flujo;
	/*
	 * Se desconectó la salida (audífonos, Bluetooth): sin sonido hasta que Kotlin lo reabra
	 * (reabrirSonido). Se despierta a los juegos que esperaban al sonido, salvo que alguien esté
	 * cerrando o reabriendo el flujo (tiene el candado): ese ya los despierta.
	 */
	struct Sala* sala = datos;
	AVISO("el sonido se cortó: %s", AAudio_convertResultToText(error));
	if (pthread_mutex_trylock(&sala->audioCandado) == 0) {
		_ponerAudioActivo(sala, false);
		pthread_mutex_unlock(&sala->audioCandado);
	} else {
		atomic_store(&sala->audioActivo, false);
	}
}

static bool _abrirSonido(struct Sala* sala) {
	AAudioStreamBuilder* constructor;
	if (AAudio_createStreamBuilder(&constructor) != AAUDIO_OK) {
		return false;
	}
	AAudioStreamBuilder_setFormat(constructor, AAUDIO_FORMAT_PCM_I16);
	AAudioStreamBuilder_setChannelCount(constructor, 2);
	AAudioStreamBuilder_setPerformanceMode(constructor, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
	AAudioStreamBuilder_setSharingMode(constructor, AAUDIO_SHARING_MODE_SHARED);
	AAudioStreamBuilder_setDataCallback(constructor, _alPedirSonido, sala);
	AAudioStreamBuilder_setErrorCallback(constructor, _alFallarSonido, sala);
	AAudioStream* flujo = NULL;
	aaudio_result_t resultado = AAudioStreamBuilder_openStream(constructor, &flujo);
	AAudioStreamBuilder_delete(constructor);
	if (resultado != AAUDIO_OK) {
		AVISO("no se pudo abrir el sonido: %s", AAudio_convertResultToText(resultado));
		return false;
	}
	sala->tasaDeAudio = AAudioStream_getSampleRate(flujo);
	AAudioStream_setBufferSizeInFrames(flujo, AAudioStream_getFramesPerBurst(flujo) * 4);
	sala->audio = flujo;
	return true;
}

static void _cerrarSonido(struct Sala* sala) {
	if (sala->audio) {
		AAudioStream_requestStop(sala->audio);
		AAudioStream_close(sala->audio);
		sala->audio = NULL;
	}
	_ponerAudioActivo(sala, false);
}

/* ---------- Sala ---------- */

static struct Sala* _crearSala(bool conCable) {
	struct Sala* sala = calloc(1, sizeof(*sala));
	if (!sala) {
		return NULL;
	}
	sala->conCable = conCable;
	atomic_store(&sala->audible, 0);
	pthread_mutex_init(&sala->enlaceCandado, NULL);
	pthread_mutex_init(&sala->audioCandado, NULL);
	return sala;
}

/* 0 si cargó; negativo si no: -1 sala llena o ya arrancada, -2 sin memoria, -3 ROM ilegible, -4 no es de GBA. */
static int _agregarJuego(struct Sala* sala, const char* rutaRom, const uint8_t* partida, size_t tamanoPartida) {
	if (sala->cuantos >= MAX_JUEGOS || sala->arrancada) {
		return -1;
	}
	struct Juego* juego = &sala->juegos[sala->cuantos];
	memset(juego, 0, sizeof(*juego));
	juego->sala = sala;
	juego->escala = 1;
	pthread_mutex_init(&juego->ventanaCandado, NULL);
	pthread_mutex_init(&juego->partidaCandado, NULL);

	juego->video = calloc(ANCHO * ALTO, sizeof(color_t));
	juego->memoriaPartida = malloc(TAM_MEMORIA_PARTIDA);
	juego->partidaEstable = malloc(TAM_MEMORIA_PARTIDA);
	if (!juego->video || !juego->memoriaPartida || !juego->partidaEstable) {
		goto sinMemoria;
	}
	memset(juego->memoriaPartida, 0xFF, TAM_MEMORIA_PARTIDA);
	if (tamanoPartida > TAM_MEMORIA_PARTIDA) {
		tamanoPartida = TAM_MEMORIA_PARTIDA;
	}
	if (tamanoPartida) {
		memcpy(juego->memoriaPartida, partida, tamanoPartida);
	}
	juego->tamanoOriginal = tamanoPartida;

	struct VFile* rom = VFileOpen(rutaRom, O_RDONLY);
	if (!rom) {
		free(juego->video);
		free(juego->memoriaPartida);
		free(juego->partidaEstable);
		return -3;
	}
	if (!GBAIsROM(rom)) {
		rom->close(rom);
		free(juego->video);
		free(juego->memoriaPartida);
		free(juego->partidaEstable);
		return -4;
	}
	struct mCore* core = GBACoreCreate();
	if (!core || !core->init(core)) {
		rom->close(rom);
		goto sinMemoria;
	}
	mCoreInitConfig(core, NULL);
	mCoreLoadConfig(core);
	/* Sin BIOS de Nintendo: la de mGBA (HLE), y directo al juego. */
	core->opts.useBios = false;
	core->opts.skipBios = true;
	core->setVideoBuffer(core, juego->video, ANCHO);
	core->setAudioBufferSize(core, MUESTRAS_POR_BLOQUE);
	if (!core->loadROM(core, rom)) {
		rom->close(rom);
		mCoreConfigDeinit(&core->config);
		core->deinit(core);
		free(juego->video);
		free(juego->memoriaPartida);
		free(juego->partidaEstable);
		return -4;
	}
	struct VFile* memoria = VFileFromMemory(juego->memoriaPartida, TAM_MEMORIA_PARTIDA);
	core->loadSave(core, memoria);

	struct mCoreCallbacks avisos = { .savedataUpdated = _alGuardarElJuego, .context = juego };
	core->addCoreCallbacks(core, &avisos);

	juego->core = core;
	juego->hilo.core = core;
	juego->hilo.userData = juego;
	juego->hilo.startCallback = _alArrancar;
	juego->hilo.frameCallback = _alTerminarCuadro;
	++sala->cuantos;
	return 0;

sinMemoria:
	free(juego->video);
	free(juego->memoriaPartida);
	free(juego->partidaEstable);
	return -2;
}

static bool _arrancar(struct Sala* sala) {
	if (sala->arrancada || !sala->cuantos) {
		return false;
	}
	if (sala->conCable) {
		mLockstepInit(&sala->enlace.d);
		sala->enlace.d.context = sala;
		sala->enlace.d.lock = _enlaceCerrar;
		sala->enlace.d.unlock = _enlaceAbrir;
		sala->enlace.d.signal = _enlaceAvisar;
		sala->enlace.d.wait = _enlaceEsperar;
		sala->enlace.d.addCycles = _enlaceSumarCiclos;
		sala->enlace.d.useCycles = _enlaceUsarCiclos;
		sala->enlace.d.unusedCycles = _enlaceCiclosSinUsar;
		sala->enlace.d.unload = _enlaceDescargar;
		GBASIOLockstepInit(&sala->enlace);
	}
	pthread_mutex_lock(&sala->audioCandado);
	atomic_store(&sala->audioActivo, _abrirSonido(sala));
	pthread_mutex_unlock(&sala->audioCandado);

	/* Uno tras otro: mCoreThreadStart regresa cuando el hilo ya corre, así el primero es el jugador 1. */
	for (int i = 0; i < sala->cuantos; ++i) {
		struct Juego* juego = &sala->juegos[i];
		juego->despierto = 1;
		if (!mCoreThreadStart(&juego->hilo)) {
			AVISO("no arrancó el juego %d", i);
			return false;
		}
		atomic_store(&juego->arrancado, true);
	}
	sala->arrancada = true;
	pthread_mutex_lock(&sala->audioCandado);
	if (sala->audio && AAudioStream_requestStart(sala->audio) != AAUDIO_OK) {
		_cerrarSonido(sala);
	}
	pthread_mutex_unlock(&sala->audioCandado);
	return true;
}

static void _pausar(struct Sala* sala, bool pausa) {
	if (!sala->arrancada || sala->cerrada || sala->pausada == pausa) {
		return;
	}
	sala->pausada = pausa;
	if (pausa) {
		for (int i = 0; i < sala->cuantos; ++i) {
			mCoreThreadPause(&sala->juegos[i].hilo);
		}
		pthread_mutex_lock(&sala->audioCandado);
		if (sala->audio) {
			AAudioStream_requestPause(sala->audio);
		}
		pthread_mutex_unlock(&sala->audioCandado);
	} else {
		pthread_mutex_lock(&sala->audioCandado);
		if (sala->audio) {
			AAudioStream_requestStart(sala->audio);
		}
		pthread_mutex_unlock(&sala->audioCandado);
		for (int i = 0; i < sala->cuantos; ++i) {
			sala->juegos[i].siguienteCuadro = 0;
			mCoreThreadUnpause(&sala->juegos[i].hilo);
		}
	}
}

/* Reabre el sonido si se cortó. true si quedó con sonido. */
static bool _reabrirSonido(struct Sala* sala) {
	if (!sala->arrancada || sala->cerrada) {
		return false;
	}
	pthread_mutex_lock(&sala->audioCandado);
	bool activo = atomic_load(&sala->audioActivo);
	if (!activo) {
		_cerrarSonido(sala);
		if (_abrirSonido(sala)) {
			if (sala->pausada || AAudioStream_requestStart(sala->audio) == AAUDIO_OK) {
				_ponerAudioActivo(sala, true);
				activo = true;
			} else {
				_cerrarSonido(sala);
			}
		}
	}
	pthread_mutex_unlock(&sala->audioCandado);
	return activo;
}

static void _cerrar(struct Sala* sala) {
	if (sala->cerrada) {
		return;
	}
	sala->cerrada = true;
	if (!sala->arrancada) {
		return;
	}
	pthread_mutex_lock(&sala->audioCandado);
	_cerrarSonido(sala);
	pthread_mutex_unlock(&sala->audioCandado);

	if (sala->conCable) {
		/* Como MultiplayerController::detachGame: todos detenidos mientras se quita el cable. */
		for (int i = 0; i < sala->cuantos; ++i) {
			mCoreThreadInterrupt(&sala->juegos[i].hilo);
		}
		for (int i = 0; i < sala->cuantos; ++i) {
			struct Juego* juego = &sala->juegos[i];
			if (!juego->conectado) {
				continue;
			}
			struct GBA* gba = juego->core->board;
			GBASIOSetDriver(&gba->sio, NULL, SIO_MULTI);
			GBASIOSetDriver(&gba->sio, NULL, SIO_NORMAL_32);
			GBASIOLockstepDetachNode(&sala->enlace, &juego->nodo);
			juego->conectado = false;
		}
		for (int i = 0; i < sala->cuantos; ++i) {
			mCoreThreadContinue(&sala->juegos[i].hilo);
		}
	}
	for (int i = 0; i < sala->cuantos; ++i) {
		mCoreThreadEnd(&sala->juegos[i].hilo);
	}
	for (int i = 0; i < sala->cuantos; ++i) {
		mCoreThreadJoin(&sala->juegos[i].hilo);
		atomic_store(&sala->juegos[i].arrancado, false);
	}
}

static void _liberar(struct Sala* sala) {
	_cerrar(sala);
	for (int i = 0; i < sala->cuantos; ++i) {
		struct Juego* juego = &sala->juegos[i];
		pthread_mutex_lock(&juego->ventanaCandado);
		if (juego->ventana) {
			ANativeWindow_release(juego->ventana);
			juego->ventana = NULL;
		}
		pthread_mutex_unlock(&juego->ventanaCandado);
		if (juego->core) {
			mCoreConfigDeinit(&juego->core->config);
			juego->core->deinit(juego->core);
		}
		free(juego->video);
		free(juego->memoriaPartida);
		free(juego->partidaEstable);
		pthread_mutex_destroy(&juego->ventanaCandado);
		pthread_mutex_destroy(&juego->partidaCandado);
	}
	if (sala->conCable && sala->arrancada) {
		mLockstepDeinit(&sala->enlace.d);
	}
	pthread_mutex_destroy(&sala->enlaceCandado);
	pthread_mutex_destroy(&sala->audioCandado);
	free(sala);
}

/* ---------- JNI: com.baxner.r36scompanion.emulador.Nativo ---------- */

#define JNI(nombre) Java_com_baxner_r36scompanion_emulador_Nativo_##nombre

static struct Juego* _juego(jlong puntero, jint jugador) {
	struct Sala* sala = (struct Sala*) (intptr_t) puntero;
	if (!sala || jugador < 0 || jugador >= sala->cuantos) {
		return NULL;
	}
	return &sala->juegos[jugador];
}

JNIEXPORT jlong JNICALL JNI(crearSala)(JNIEnv* env, jclass clase, jboolean conCable) {
	(void) env;
	(void) clase;
	return (jlong) (intptr_t) _crearSala(conCable);
}

JNIEXPORT jint JNICALL JNI(agregarJuego)(JNIEnv* env, jclass clase, jlong puntero, jstring rom, jbyteArray partida) {
	(void) clase;
	struct Sala* sala = (struct Sala*) (intptr_t) puntero;
	const char* ruta = (*env)->GetStringUTFChars(env, rom, NULL);
	jsize tamano = partida ? (*env)->GetArrayLength(env, partida) : 0;
	jbyte* bytes = tamano ? (*env)->GetByteArrayElements(env, partida, NULL) : NULL;
	int resultado = _agregarJuego(sala, ruta, (const uint8_t*) bytes, (size_t) tamano);
	if (bytes) {
		(*env)->ReleaseByteArrayElements(env, partida, bytes, JNI_ABORT);
	}
	(*env)->ReleaseStringUTFChars(env, rom, ruta);
	return resultado;
}

JNIEXPORT jboolean JNICALL JNI(arrancar)(JNIEnv* env, jclass clase, jlong puntero) {
	(void) env;
	(void) clase;
	return _arrancar((struct Sala*) (intptr_t) puntero);
}

JNIEXPORT void JNICALL JNI(pausar)(JNIEnv* env, jclass clase, jlong puntero, jboolean pausa) {
	(void) env;
	(void) clase;
	_pausar((struct Sala*) (intptr_t) puntero, pausa);
}

JNIEXPORT void JNICALL JNI(teclas)(JNIEnv* env, jclass clase, jlong puntero, jint jugador, jint mascara) {
	(void) env;
	(void) clase;
	struct Juego* juego = _juego(puntero, jugador);
	if (juego) {
		atomic_store(&juego->teclas, (unsigned) mascara & 0x3FF);
	}
}

JNIEXPORT void JNICALL JNI(pantalla)(JNIEnv* env, jclass clase, jlong puntero, jint jugador, jobject superficie, jint escala) {
	(void) clase;
	struct Juego* juego = _juego(puntero, jugador);
	if (!juego) {
		return;
	}
	ANativeWindow* nueva = superficie ? ANativeWindow_fromSurface(env, superficie) : NULL;
	pthread_mutex_lock(&juego->ventanaCandado);
	if (juego->ventana) {
		ANativeWindow_release(juego->ventana);
	}
	juego->ventana = nueva;
	juego->escala = escala < 1 ? 1 : (escala > 8 ? 8 : escala);
	juego->escalaPuesta = 0;
	pthread_mutex_unlock(&juego->ventanaCandado);
}

JNIEXPORT void JNICALL JNI(sonido)(JNIEnv* env, jclass clase, jlong puntero, jint jugador) {
	(void) env;
	(void) clase;
	struct Sala* sala = (struct Sala*) (intptr_t) puntero;
	atomic_store(&sala->audible, jugador);
}

JNIEXPORT jboolean JNICALL JNI(reabrirSonido)(JNIEnv* env, jclass clase, jlong puntero) {
	(void) env;
	(void) clase;
	return _reabrirSonido((struct Sala*) (intptr_t) puntero);
}

JNIEXPORT jint JNICALL JNI(versionDePartida)(JNIEnv* env, jclass clase, jlong puntero, jint jugador) {
	(void) env;
	(void) clase;
	struct Juego* juego = _juego(puntero, jugador);
	return juego ? atomic_load(&juego->versionPartida) : 0;
}

/* La última partida que el juego terminó de guardar; null si no ha guardado desde que arrancó. */
JNIEXPORT jbyteArray JNICALL JNI(partida)(JNIEnv* env, jclass clase, jlong puntero, jint jugador) {
	(void) clase;
	struct Juego* juego = _juego(puntero, jugador);
	if (!juego || !atomic_load(&juego->versionPartida)) {
		return NULL;
	}
	pthread_mutex_lock(&juego->partidaCandado);
	jbyteArray copia = (*env)->NewByteArray(env, (jsize) juego->tamanoEstable);
	if (copia) {
		(*env)->SetByteArrayRegion(env, copia, 0, (jsize) juego->tamanoEstable, (const jbyte*) juego->partidaEstable);
	}
	pthread_mutex_unlock(&juego->partidaCandado);
	return copia;
}

JNIEXPORT jlong JNICALL JNI(cuadros)(JNIEnv* env, jclass clase, jlong puntero, jint jugador) {
	(void) env;
	(void) clase;
	struct Juego* juego = _juego(puntero, jugador);
	return juego ? atomic_load(&juego->cuadros) : 0;
}

JNIEXPORT jboolean JNICALL JNI(conSonido)(JNIEnv* env, jclass clase, jlong puntero) {
	(void) env;
	(void) clase;
	struct Sala* sala = (struct Sala*) (intptr_t) puntero;
	return atomic_load(&sala->audioActivo);
}

/* Para diagnosticar el cable: registros del puerto serie de cada juego y el estado del enlace. */
JNIEXPORT jstring JNICALL JNI(diagnostico)(JNIEnv* env, jclass clase, jlong puntero) {
	(void) clase;
	struct Sala* sala = (struct Sala*) (intptr_t) puntero;
	char texto[1024];
	int n = snprintf(texto, sizeof(texto), "cable=%d conectados=%d multi=%d normal=%d transferencia=%d sonido=%d",
	                 sala->conCable, sala->enlace.d.attached, sala->enlace.attachedMulti, sala->enlace.attachedNormal,
	                 (int) sala->enlace.d.transferActive, (int) atomic_load(&sala->audioActivo));
	for (int i = 0; i < sala->cuantos && n < (int) sizeof(texto); ++i) {
		struct Juego* juego = &sala->juegos[i];
		if (!juego->core) {
			continue;
		}
		struct GBA* gba = juego->core->board;
		n += snprintf(texto + n, sizeof(texto) - n,
		              "; j%d: modo=%d siocnt=%04X rcnt=%04X send=%04X multi=%04X,%04X id=%d despierto=%d ciclos=%d espera=%X sinc=%d",
		              i + 1, (int) gba->sio.mode, gba->sio.siocnt, gba->sio.rcnt, gba->memory.io[REG_SIOMLT_SEND >> 1],
		              gba->memory.io[REG_SIOMULTI0 >> 1], gba->memory.io[REG_SIOMULTI1 >> 1], juego->nodo.id,
		              juego->despierto, (int) juego->ciclosPublicados, juego->mascaraEspera, juego->sincronizado);
	}
	return (*env)->NewStringUTF(env, texto);
}

JNIEXPORT void JNICALL JNI(cerrar)(JNIEnv* env, jclass clase, jlong puntero) {
	(void) env;
	(void) clase;
	_cerrar((struct Sala*) (intptr_t) puntero);
}

JNIEXPORT void JNICALL JNI(liberar)(JNIEnv* env, jclass clase, jlong puntero) {
	(void) env;
	(void) clase;
	_liberar((struct Sala*) (intptr_t) puntero);
}
