// Genera android/app/src/main/assets/motor/: la página del puente, su JS empaquetado, el
// runtime de .NET con PKHeX.Core (runtime/) y los avisos de licencia del motor.
// El runtime va en runtime/ y no en _framework/ como en el paquete: Android descarta del APK las
// carpetas de assets que empiezan con guion bajo (patrón de AAPT "<dir>_*").
import { build } from 'esbuild';
import { cpSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const salida = join(aqui, '../android/app/src/main/assets/motor');
const paquete = join(aqui, 'node_modules/@pkhex-everywhere/engine');

rmSync(salida, { recursive: true, force: true });
mkdirSync(salida, { recursive: true });

await build({
  entryPoints: [join(aqui, 'src/puente.js')],
  bundle: true,
  format: 'esm',
  target: 'es2022',
  outfile: join(salida, 'puente.js'),
  legalComments: 'eof',
  logLevel: 'warning',
});

cpSync(join(paquete, '_framework'), join(salida, 'runtime'), { recursive: true });
cpSync(join(paquete, 'notices.json'), join(salida, 'notices.json'));
writeFileSync(
  join(salida, 'index.html'),
  `<!doctype html>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; connect-src 'self'">
<title>Motor PKHeX</title>
<script type="module" src="puente.js"></script>
`,
);
console.log(`motor generado en ${salida}`);
