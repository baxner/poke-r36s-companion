# PokeR36S Companion

App de Android para cuidar tus partidas de Pokémon de Game Boy Advance de una **R36S con ArkOS**:
las respalda por el cable USB, las edita como PKHeX, intercambia Pokémon entre ellas y deja
seguir jugándolas en el teléfono, todo sin internet.

> Proyecto de fans, sin relación con Nintendo, Creatures Inc. ni GAME FREAK. Pokémon y sus
> nombres son marcas de sus dueños. **No incluye ROMs ni imágenes de los juegos**: usa los tuyos.

## Qué hace

- **Respaldos automáticos.** Al conectar la consola, guarda en el teléfono cada versión de tus
  partidas (`.srm`) y estados de RetroArch, con historial y "qué cambió" (medallas, equipo,
  Pokédex, dinero). Sirve para cualquier juego de GBA, no solo los de Pokémon.
- **Editor.** Pokémon (especie, nivel, movimientos, objeto, naturaleza, shiny, mote…), mochila,
  dinero y entrenador, con avisos de legalidad del propio PKHeX. Todo en español, con los nombres
  de las tablas de PKHeX.
- **Banco.** Una caja propia fuera de las partidas para guardar a tus favoritos y pasarlos de un
  juego a otro.
- **Pokédex global.** Las 386 especies de la 3.ª generación juntando todas tus partidas y el
  banco: cuáles tienes, cuáles viste y dónde atraparlas en tus juegos.
- **Intercambio rápido.** Elige qué Pokémon manda cada partida y se escriben las dos, con las
  evoluciones por intercambio como en el juego.
- **Intercambio real.** Las dos partidas corren a la vez en el teléfono con mGBA, **unidas por el
  cable link emulado**: vas al Club de Cable en los dos juegos e intercambias como en dos consolas.
  Al terminar ves qué cambió antes de escribirlas en la consola.
- **Seguir jugando en el teléfono.** Llévate una partida al teléfono, juega con controles táctiles
  o un mando Bluetooth, y regrésala a la consola.
- **Conexión guiada.** Detecta el cable y el anclaje USB, encuentra la consola sola (su IP cambia
  en cada conexión), fija su llave SSH la primera vez y trae una Introducción y un Diagnóstico para
  quien empieza.

## Lo que necesitas

- Una R36S (o clon) con **ArkOS** y "Enable Remote Services" encendido (SSH `ark`/`ark` de fábrica).
- Un teléfono Android 8 o más nuevo.
- Un cable **USB-A a C con adaptador OTG** del lado de la consola. Con un cable C a C el anclaje
  USB del teléfono suele salir en gris. La consola le da corriente al teléfono: tenla cargada.
- En el teléfono, prender el **anclaje USB** (la app te lleva a esa pantalla).

## Cómo cuida tus partidas

Antes de escribir algo en la consola, la app:

1. respalda lo que había;
2. revisa que la partida de la consola siga siendo la que se leyó (si jugaste mientras tanto, no
   escribe nada y te pregunta);
3. no escribe si el juego está abierto en RetroArch;
4. escribe a un temporal, lo verifica byte por byte, lo cambia por el original y fuerza la
   escritura a la tarjeta;
5. **aparta** (no borra) el `.state.auto` del juego, porque RetroArch lo carga al abrir el juego y
   pisaría lo escrito.

Un intercambio son dos escrituras: si la segunda falla, la primera se regresa a como estaba.

La app **no habla con internet**: solo con la consola, por la red privada del cable. El motor de
PKHeX corre dentro de la app, sin red.

## Cómo está hecha

- **Kotlin + Jetpack Compose** (Material 3), Room, WorkManager y JSch para SSH/SFTP.
- **Motor PKHeX**: [`@pkhex-everywhere/engine`](https://www.npmjs.com/package/@pkhex-everywhere/engine)
  (PKHeX.Core compilado a WebAssembly) corriendo en un WebView invisible, con un puente propio
  (`engine-bridge/`). La app solo deja cargar sus propios archivos.
- **Emulador**: [mGBA](https://mgba.io) 0.10.5 compilado con el NDK (módulo `android/emulador/`),
  con una capa en C propia: cada GBA en su hilo, sonido por AAudio, pantalla sin suavizar, y el
  cable link de mGBA con los callbacks de su versión de escritorio. La partida va en memoria del
  mismo tamaño que la del núcleo de RetroArch de la consola, para que lo que vuelve a la R36S sea
  exactamente lo que ese núcleo espera.
- El diseño, las decisiones y lo medido contra una consola real están en [`docs/plan.md`](docs/plan.md).
  `AGENTS.md` tiene las reglas con las que se construyó (con agentes de IA).

## Compilar

Se necesitan JDK 17, el SDK de Android con el NDK 27.1.12297006 y CMake 3.22.1, y Node 20+.

```sh
scripts/build-motor.sh     # el motor PKHeX (npm) → android/app/src/main/assets/motor/
scripts/bajar-mgba.sh      # mGBA 0.10.5, fijado por SHA-256 → third_party/mgba/
scripts/bajar-sprites.sh   # opcional: sprites de PokeAPI → android/app/src/main/assets/sprites/
cd android
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Nada de eso se versiona. Sin los sprites la app funciona igual, sin imágenes de los Pokémon.

### Pruebas

- `testDebugUnitTest`: más de 500 pruebas en la JVM (Robolectric), incluidas capturas de cada
  pantalla con Roborazzi y un servidor SSH/SFTP falso en proceso.
- `connectedDebugAndroidTest` (emulador o teléfono): el motor PKHeX de verdad y mGBA de verdad.
  Las del emulador usan una ROM mínima armada dentro de la propia prueba; las que usan partidas o
  juegos reales solo corren si pones los tuyos en las carpetas `assets/muestras/` de las pruebas
  (ignoradas por git).

## Licencia

**GPL-3.0-or-later** (ver [`LICENSE`](LICENSE)), porque incluye el motor de PKHeX.

Créditos:

- [PKHeX](https://github.com/kwsch/PKHeX) de Kurt (kwsch) y colaboradores, y
  [PKHeX.Everywhere](https://github.com/arleypadua/PKHeX.Everywhere) de Arley Pádua por el motor
  en WebAssembly (GPL-3.0).
  Las tablas de nombres en español salen de PKHeX (ver `android/app/src/main/assets/textos/ORIGEN.md`).
- [mGBA](https://mgba.io) de Vicki Pfau (endrift) y colaboradores (Mozilla Public License 2.0).
- [PokeAPI/sprites](https://github.com/PokeAPI/sprites), si los bajas con el script.
