// Puente entre la app y el motor PKHeX (@pkhex-everywhere/engine), dentro de un WebView
// invisible. Kotlin manda {id, metodo: "espacio.funcion", args} por el canal `puenteMotor`
// (WebViewCompat.addWebMessageListener) y recibe {id, ok, resultado} o {id, ok: false, error}.
//
// Los bytes viajan como {"$bytes": "<base64>"} en los dos sentidos, a cualquier profundidad.
// El runtime de .NET se carga de los archivos de la app (runtime/, junto a esta página):
// nunca del CDN de jsDelivr, que es lo que el paquete haría por defecto.
import { createEngine, wasmHost } from '@pkhex-everywhere/engine';

const canal = globalThis.puenteMotor;
const enviar = (mensaje) => canal.postMessage(JSON.stringify(mensaje));

// `plugins` descarga y ejecuta código de terceros: aquí nunca.
const ESPACIOS_BLOQUEADOS = new Set(['plugins']);

const dotnetUrl = new URL('runtime/dotnet.js', document.baseURI).href;
const motor = createEngine({ host: wasmHost({ dotnetUrl }) });

function base64DeBytes(bytes) {
  let texto = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    texto += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  }
  return btoa(texto);
}

function bytesDeBase64(base64) {
  const texto = atob(base64);
  const bytes = new Uint8Array(texto.length);
  for (let i = 0; i < texto.length; i++) bytes[i] = texto.charCodeAt(i);
  return bytes;
}

function codificar(valor) {
  if (valor instanceof Uint8Array) return { $bytes: base64DeBytes(valor) };
  if (Array.isArray(valor)) return valor.map(codificar);
  if (valor && typeof valor === 'object') {
    return Object.fromEntries(Object.entries(valor).map(([clave, v]) => [clave, codificar(v)]));
  }
  return valor;
}

function decodificar(valor) {
  if (Array.isArray(valor)) return valor.map(decodificar);
  if (valor && typeof valor === 'object') {
    if (typeof valor.$bytes === 'string' && Object.keys(valor).length === 1) return bytesDeBase64(valor.$bytes);
    return Object.fromEntries(Object.entries(valor).map(([clave, v]) => [clave, decodificar(v)]));
  }
  return valor;
}

canal.onmessage = async (evento) => {
  let mensaje;
  try {
    mensaje = JSON.parse(evento.data);
  } catch {
    return;
  }
  const { id, metodo, args = [] } = mensaje;
  try {
    const [espacio, funcion, sobra] = String(metodo).split('.');
    const destino = motor[espacio];
    if (sobra !== undefined || ESPACIOS_BLOQUEADOS.has(espacio) || !destino || typeof destino[funcion] !== 'function') {
      throw Object.assign(new Error(`método no permitido: ${metodo}`), { code: 'metodo-no-permitido' });
    }
    const resultado = await destino[funcion](...args.map(decodificar));
    enviar({ id, ok: true, resultado: codificar(resultado ?? null) });
  } catch (error) {
    enviar({
      id,
      ok: false,
      error: {
        code: error?.code ?? 'error',
        message: String(error?.message ?? error),
        candidates: error?.candidates ? codificar(error.candidates) : null,
      },
    });
  }
};

motor.onEvent((evento) => enviar({ evento: 'motor', datos: codificar(evento) }));

// La primera llamada arranca el runtime; `game.get` no necesita una partida cargada.
const inicio = performance.now();
motor.game.get().then(
  () => enviar({ evento: 'listo', ms: Math.round(performance.now() - inicio) }),
  (error) => enviar({ evento: 'fallo', error: String(error?.message ?? error) }),
);
