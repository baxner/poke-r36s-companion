# Plan: "PokeR36S Companion", respaldar y editar tus partidas de Pokémon de la R36S

Borrador del 2026-10-18, actualizado el 2026-10-19 con las decisiones del usuario. Nombre propuesto
por el usuario: **PokeR36S Companion** (el que muestra la app; ver "Nombre" abajo).

## Qué quiere el usuario

Una app en el S24 que:
1. **Respalde** las partidas de Pokémon de GBA de su R36S (Rubí/Zafiro, Esmeralda, Rojo
   Fuego/Verde Hoja), pasando por el cable USB con anclaje.
2. Permita **editarlas** como PKHeX web: Pokémon, objetos, dinero y entrenador.
3. **Mueva Pokémon entre partidas**, como un intercambio.
4. **Avise si algo es ilegal**, es decir, imposible en el juego real.

Firmware: **ArkOS R3XS V2.0 (02092025)**, la versión comunitaria de AeolusUX para la R36S
original (confirmado con una foto del menú el 2026-10-19). Está hecha sobre la ArkOS de
christianhaitian: trae SFTP (`ark`/`ark`), FileBrowser (administrador de archivos web) y
RetroArch. Hay una versión más nueva (11/07/2025, con ArkManager, que menciona "USB modem"),
pero no hace falta actualizar para este proyecto.

## Lo que ya se sabe (investigado el 2026-10-18)

- **PKHeX web** (pkhex-web.github.io, proyecto PKHeX.Everywhere) corre el motor real de
  PKHeX (C#, PKHeX.Core) compilado a WebAssembly, con una interfaz en React. La edición pasa
  en el navegador, pero la página **no es 100% local**: carga Google Analytics
  (`G-BV586KEZM9`), anuncios de AdSense (con anuncios personalizados por defecto fuera de
  Europa), reporte de errores a Sentry, el motor desde jsDelivr y los plugins desde GitHub.
- **El motor se publica aparte:** `@pkhex-everywhere/engine` en npm (v0.26.0, del
  2026-10-14, GPL-3.0-or-later, ~29 MB sin comprimir). Trae el runtime de .NET con
  PKHeX.Core. Su API cubre cargar y exportar partidas, equipo, cajas, editar Pokémon, mochila,
  tarjeta de entrenador, legalidad y regalos de evento. No trae analíticas ni anuncios.
- **La licencia GPL** no es problema para uso personal. Si algún día se publica la app, tiene
  que ser de código abierto.
- **Las partidas en ArkOS:** los `.srm` (la partida del juego) y los `.state` (estados
  guardados del emulador) quedan junto a las ROMs, en `roms/gba/`. Los `.state` no se pueden
  editar: son una foto de la memoria del emulador.
- **El reloj de mGBA:** las versiones nuevas de mGBA agregan 16 bytes al final (131 088 en vez
  de 131 072) con el reloj del juego, que mueve las bayas y la marea en Rubí, Zafiro y
  Esmeralda. PKHeX los acepta desde 2022 (issue #3604), pero hay que **comprobar con una
  partida real** que el reloj se conserve al exportar. Si no, la app lo vuelve a pegar.
- **El cable** (documento de investigación del usuario): que la R36S reciba red del teléfono por
  anclaje USB en el puerto OTG está confirmado en ArkOS. Que el teléfono le pueda pasar
  archivos por ese enlace (SFTP, usuario `ark`/`ark`, puerto 22) es lógico, pero **nadie lo ha
  probado**. Los servicios remotos de ArkOS se apagan en cada reinicio (Options → Enable
  Remote Services). Conviene usar un cable USB-A con un adaptador OTG del lado de la consola,
  y tener la consola cargada: le da corriente al teléfono.

## Decisiones del usuario (2026-10-19)

- **Sí:** Pokédex global; sección de **Introducción** para usuarios nuevos; que la app
  **gestione el anclaje USB y encuentre la consola sola**.
- **Por explicar y decidir:** el banco de Pokémon y seguir jugando en el teléfono.

### Introducción y diagnóstico

Una sección para la primera vez, que también sirve como "¿por qué no conecta?":
1. **Qué necesitas:** cable USB-A a C + adaptador OTG del lado de la consola (con C a C el
   anclaje sale en gris); la consola cargada, porque le da corriente al teléfono.
2. **En la consola:** puerto OTG (no DC); Options → Enable Remote Services en cada encendido.
3. **En el teléfono:** activar el anclaje de USB (la app abre esa pantalla directo).
4. **Lo que conviene saber de tu RetroArch:** que retoma desde el estado automático, y que
   conviene **guardar dentro del juego** (si una partida está vacía pero tiene estado
   automático, la app lo detecta y lo explica, como pasó con Verde Hoja).
5. **Diagnóstico en vivo:** cada paso con su ✅/❌ (cable, anclaje, consola encontrada,
   servicios remotos, partidas encontradas, estados automáticos) y qué hacer si falla.

Opcional, con permiso, porque cambia la consola:
- **Que tus juegos de Pokémon arranquen desde la partida y no desde el estado automático:**
  un ajuste de RetroArch solo para esos juegos (`savestate_auto_load = "false"` en un
  override por juego). Así una edición se nota siempre. Reversible.
- **Dejar los servicios remotos prendidos al encender**, para no tener que activarlos cada
  vez. Por verificar en ArkOS R3XS. Si se hace, conviene cambiar la contraseña `ark` (si la
  consola entra al Wi-Fi de una casa, el SSH con la contraseña de fábrica quedaría expuesto
  en esa red).

### Anclaje USB y consola automática

- **Android no deja que una app normal prenda el anclaje:** `TetheringManager.startTethering`
  necesita `TETHER_PRIVILEGED`, que solo tienen las apps del sistema.
- **Lo que sí hace la app:**
  - Detecta el cable y el anclaje: la interfaz `rndis0`/`ncm0` aparece al activarlo.
  - Si conectas el cable y el anclaje está apagado, un botón abre **directo** la pantalla
    de anclaje del S24: un toque y regresas.
  - Con el anclaje activo, **busca la consola sola** en esa subred (cambia en cada
    conexión), confirma que es ella (SSH de ArkOS y su carpeta `roms`) y **fija su huella
    SSH** la primera vez, como Vigía con su certificado. Así nunca le manda la contraseña a
    otro equipo.
  - Opcional: **respaldo automático al conectar**, con aviso al terminar.
  - El mismo buscador sirve si algún día usas un dongle Wi-Fi.

### Pokédex global

- Las 386 especies, juntando todas tus partidas (y el banco, si se hace): cuáles tienes,
  cuáles viste, en qué partida y caja está cada una.
- Para las que te faltan: **dónde atraparlas en tus juegos** (el motor tiene los encuentros
  de cada versión) o si solo salen por intercambio o evento.

## Arquitectura propuesta

**App Android nativa** (Kotlin + Jetpack Compose, Material 3), el mismo stack que Vigía, para
el S24. Tres piezas:

| Pieza | Qué hace | Cómo |
|---|---|---|
| **Conexión con la consola** | Encuentra la R36S en la red del anclaje y lee y escribe archivos | SFTP con una librería Java (mwiede/jsch o sshj). Busca el puerto 22 en la subred de la interfaz del anclaje (`rndis0`/`ncm0`) o usa la IP que pongas a mano |
| **Motor PKHeX** | Lee, edita, revisa la legalidad y exporta la partida | `@pkhex-everywhere/engine` empaquetado **dentro de la app** y corriendo en un WebView invisible (servido con `WebViewAssetLoader`, sin internet). La app le habla por un puente JavaScript |
| **Respaldos** | Historial de versiones de cada partida | Copias en el almacenamiento de la app, sin duplicados (por hash SHA-256), con lo que el motor lee de cada una: juego, entrenador, tiempo de juego, medallas, Pokédex y equipo |

**Toda la interfaz es nativa:** el WebView solo corre el motor y no se ve. Así la app se ve y se
siente como una app normal, y nada sale de tu teléfono: sin analíticas, sin anuncios, sin
nube. La app solo se conecta a direcciones privadas (la consola).

### Reglas de seguridad para no perder una partida

1. **Siempre se respalda antes de escribir.** Nunca se edita la única copia.
2. **Se revisa que nada haya cambiado:** antes de escribir en la consola se compara el hash
   del archivo de la consola con el que se editó. Si jugaste mientras tanto, la app avisa y no
   escribe a ciegas.
3. **Escritura atómica y comprobada:** se sube a un archivo temporal, se renombra encima, y se
   vuelve a bajar para comparar byte por byte.
4. **El juego tiene que estar cerrado en la consola.** RetroArch escribe la partida al cerrar
   el juego: si está abierto, su copia en memoria pisaría la edición. La app lo explica y lo
   pide confirmar.
5. **Los estados guardados (`.state`) devuelven la partida vieja:** si cargas uno después de
   editar, se pierde la edición. Al escribir una partida editada, la app ofrece apartar
   (no borrar) los `.state` de ese juego. Ojo con el **Quick Mode** de ArkOS: guarda un estado
   automático al apagar dentro de un juego y lo carga al volver a prender, así que también
   revertiría la edición. La app detecta esos estados automáticos y los aparta igual.
6. **El reloj de 16 bytes se conserva tal cual.**
7. **Restaurar cualquier versión** con un toque.

## Fases

| Fase | Qué trae | Quién |
|---|---|---|
| **0. Probar el cable** | Ver la guía de abajo. El usuario conecta el S24 a la R36S, activa el anclaje y los servicios remotos, y abre `http://<IP de la consola>` en el navegador del S24. Si carga, el camino funciona. Con eso mismo se baja una copia de sus `.srm` para las pruebas | el usuario, 10 min, guiado |
| **1. Prueba técnica** | Lo más riesgoso primero: el motor de PKHeX corriendo dentro de la app sin internet, leyendo una partida real del usuario, editando algo y exportando idéntico byte por byte (reloj incluido); y SFTP con la consola por el cable. Si algo de esto no funciona, se cambia el plan antes de construir encima | Opus |
| **2. Respaldos** | Conectar (detecta la consola sola), lista de partidas con su resumen, respaldar todo con un toque, historial con "qué cambió" (Pokémon nuevos, medallas, horas), restaurar una versión, exportar en .zip. **Ya sirve sola** | Sonnet + revisión de Opus |
| **3. Ver y editar** | Equipo y cajas con sprites; editor de Pokémon (especie, nivel, shiny, naturaleza, IVs/EVs, movimientos, objeto); mochila, dinero, medallas y entrenador; insignia de legalidad en cada Pokémon con la explicación; guardar en la consola con las reglas de seguridad | Sonnet + revisión de Opus |
| **4. Intercambios** | Mover Pokémon entre tus partidas como un intercambio de verdad (avisa si el juego real no lo permitiría, por ejemplo Rojo Fuego sin la Pokédex Nacional); evoluciones por intercambio (Kadabra, Machoke, Graveler, Haunter), que jugando solo no puedes conseguir | Sonnet + revisión de Opus |
| **5. Intercambio real** (pedido por el usuario el 2026-10-19) | Las dos partidas corren a la vez en el teléfono, en pantalla dividida, unidas por un cable link emulado (mGBA, código abierto, MPL 2.0), y el intercambio se hace dentro de los juegos con su animación. Al guardar en los dos, la app regresa ambas partidas a la consola con las reglas de seguridad. Empieza con una prueba técnica: dos núcleos de mGBA enlazados dentro de la app | Opus (prueba técnica) + Sonnet |
| **6. Extras** (si el usuario quiere) | Ver las sugerencias de abajo | Por decidir |

### Guía de la fase 0

1. Consola cargada (o con el cargador en el puerto **DC**).
2. Cable del S24 al puerto **OTG** de la R36S (no al DC). Mejor un cable USB-A con un
   adaptador OTG del lado de la consola; un cable C a C de datos suele funcionar.
3. En el S24: Ajustes → Conexiones → Zona Wi-Fi y Anclaje a red → **Anclaje de USB**. Si sale
   en gris: desconectar y volver a conectar, o reiniciar la consola con el cable puesto.
4. En la R36S, en el sistema **Options** del carrusel: **Enable Remote Services** (se apaga en
   cada reinicio) y después **Network Info**, para ver la IP.
5. En Chrome del S24: `http://<IP>`, usuario y contraseña `ark`. Si aparece FileBrowser, el
   camino funciona. Bajar los `.srm` de `roms/gba/` a Descargas para las pruebas.
6. Si algo falla, anotar en qué paso y qué se ve.

### Resultado de la fase 0 (2026-10-19)

- **El camino por cable funciona.** Con un cable C a C el anclaje de USB salía en gris (el S24
  no tomaba el papel de dispositivo). Con un **cable USB-A a C y un adaptador OTG** del lado de
  la consola, el anclaje se activó y Chrome del S24 abrió el FileBrowser de ArkOS en
  `http://10.101.33.254`.
- La subred del anclaje del S24 es **10.101.33.x** (no la 192.168.42.x de los Android viejos):
  la app tiene que descubrir la IP de la consola, no suponerla.
- La consola carga al teléfono mientras están conectados (el S24 mostraba "cargando").
- **La IP cambia en cada conexión** (`.254` y dos minutos después `.156`).
- **Partidas encontradas en `roms/gba/`** (11 `.srm`): de Pokémon, *Pokemon Edicion Rojo
  Fuego*, *Pokemon Edicion Verde Hoja*, *Pokemon - Version Rouge Feu (F)* (Rojo Fuego en
  francés) y dos hacks, *Pokemon Lets Go Eevee* y *Pokemon Mega Hoenn Adventure*, que
  probablemente PKHeX no pueda editar (hay que comprobarlo). Ninguna de Esmeralda ni de
  Rubí/Zafiro todavía. Las otras 6 son de otros juegos (007, Avatar, Power Rangers, A Sound
  of Thunder, Uno 52, Yu-Gi-Oh! GX).

### Primeras pruebas con las partidas reales (2026-10-19)

- **SFTP por el cable, confirmado:** desde la iMac, por un túnel adb → S24 (`rndis0`,
  10.101.33.64) → consola (`ark@rg351mp`, SSH abierto, también web y Samba). Se copiaron las
  11 `.srm` con su fecha original; están en `~/clawd/projects/cartucho/muestras/2026-10-19/`
  con su `SHA256SUMS`. Para copiar con contraseña hay que usar `scp` (`sftp -b` la desactiva).
- **RetroArch de la consola:** núcleo `mgba_rumble`; partidas y estados junto a las ROMs;
  `autosave_interval = 0` (la partida se escribe al cerrar el juego) y **`savestate_auto_save`
  y `savestate_auto_load` encendidos**: cada juego tiene su `.state.auto` y al abrirlo retoma
  desde ahí, no desde la partida. Escribir una partida editada sin apartar el `.state.auto`
  no serviría de nada.
- **Motor PKHeX en Node, sin internet** (runtime del paquete con `wasmHost({ dotnetUrl })`; sin
  eso intenta bajarlo de jsDelivr): arranca en ~0.3 s. **Rojo Fuego del usuario abre completo**
  (Rojo, 34 h, 5 medallas, 78 atrapados; equipo Kadabra, Dragonair, Nidoking, Charizard,
  Machoke, Seadra) y **exportar sin cambios da el archivo idéntico byte por byte**.
- **Verde Hoja, Rouge Feu (F) y Mega Hoenn Adventure están vacías** (todo `0xFF`): nunca se
  guardó dentro del juego. Su avance vive solo en los `.state.auto`.
- **Lets Go Eevee (hack):** no lo reconoce; con el formato de Radical Red "abre" pero lee
  basura (un Pokémon "None" Nv100). Regla: solo se edita lo que el motor reconoce con
  certeza; lo demás solo se respalda.
- **Consecuencia para la fase 2:** respaldar también los estados (`.state`, `.state1`,
  `.state.auto`), porque para algunos juegos son la única copia del avance.

### Fase 1: el motor dentro de Android (2026-10-19)

- **Funciona.** En el emulador (Android 14, WebView 113, sin GPU real) el motor arranca en 6-9 s,
  lee el Rojo Fuego real del usuario completo y **lo exporta idéntico byte por byte**; un archivo
  que no es partida da `invalid-save`. Pruebas: `MotorWebViewTest` (en el emulador) y
  `PoliticaDeRedTest` (JVM).
- **Trampa encontrada:** Android descarta del APK las carpetas de `assets/` que empiezan con `_`
  (patrón de AAPT `<dir>_*`), así que el runtime va en `assets/motor/runtime/` y no en
  `_framework/` como en el paquete.
- **El motor 0.26.0 no exporta partidas en blanco de la 3.ª generación**
  (`Arg_ArgumentOutOfRangeException` en `game.export`, también en Node). Las pruebas versionadas
  usan la partida en blanco solo en memoria (`conPartidaEnBlanco`); las de exportar usan las
  muestras locales.
- APK debug: 31.5 MB con el motor (comprimido).

### Diseño técnico de las fases 3 a 5 (revisado contra el motor real, 2026-10-19)

- **Editor (fase 3):** el motor edita por borrador: `pokemon.edit(at)` → `pokemon.update(at, patch)`
  (especie, nivel, naturaleza, shiny, IVs/EVs, movimientos, objeto, Poké Ball, mote…) →
  `pokemon.commit()`. `pokemon.details(at)` trae todo el Pokémon con su `legality`
  (`valid` + mensajes) y `pokemon.options(at)` las opciones válidas para ese juego. Mochila:
  `inventory.get`/`setItem`. Entrenador: `trainer.setMoney`, `setName`, `setBadges`.
- **Intercambio rápido (fase 4):** el motor trae intercambio entre dos partidas:
  `transfer.open(otra)` → `transfer.preview(oferta)` (qué cambia en cada Pokémon y en cada
  partida, cuáles rechaza el juego y la legalidad en el juego de destino) → `transfer.commit`,
  que devuelve **las dos partidas** exportadas. Las llegadas aceptan un `patch`: así se hace la
  **evolución por intercambio** (Kadabra → Alakazam; Seadra con Escama Dragón → Kingdra, gastando
  el objeto), con una tabla propia de evoluciones por intercambio y el motor juzgando la legalidad.
  Las dos partidas se escriben con el `EscritorDePartidas` (cada una con su respaldo y su estado
  automático apartado).
- **Banco (fase 4):** sacar un Pokémon = `pokemon.export(at)` (sus bytes exactos, `.pk3`) y vaciar
  la casilla. El motor no tiene "soltar"; la casilla se vacía con `PartidaGen3.conCasillaVacia`
  (sus 80 bytes en cero y las sumas recalculadas). **Probado con el Rojo Fuego real:** al vaciar
  la caja 1 casilla 1 cambian 79 bytes y, según el motor, solo falta ese Spearow; los otros 76
  Pokémon de las cajas, el equipo, el entrenador y la mochila quedan idénticos. Siempre se
  vuelve a cargar la partida en el motor para comprobarlo antes de escribirla. Meter un
  Pokémon = `box.previewFile` (legalidad y conversión) → `box.addFromFile`. En la v1 solo se
  deposita desde las cajas (no desde el equipo).
- **Pokédex global (fase 4):** `PartidaGen3.pokedex()` da las especies atrapadas y vistas de
  cada partida (coincide con los conteos del motor); "dónde está cada una" sale de `box.get` y
  `party.get`; "dónde atraparla" de `encounters.search(versión, especie)`.
- **Eventos (extra):** `events.get().gen3` dice qué boletos faltan y `events.giveTickets` los
  agrega (el Mapa Viejo solo se repartió en Japón: el motor pide confirmarlo aparte).
- **Intercambio real (fase 5):** mGBA (MPL 2.0) trae el cable link entre varios núcleos en un
  mismo proceso (`GBASIOLockstepCoordinator` en la rama principal; la 0.10.5 estable usa la API
  anterior `GBASIOLockstep`). Se compila con el NDK y una capa JNI propia: dos núcleos, cada
  uno en su hilo, unidos por el coordinador.

Reparto igual que en Vigía: Opus diseña, revisa e integra; Sonnet implementa módulos bien
especificados; Haiku solo tareas mecánicas. Máximo dos subagentes a la vez, cada uno en su
worktree. Los nombres de Pokémon, movimientos y objetos en español salen del propio PKHeX, que
ya los trae traducidos.

## Sugerencias de mejora (para elegir)

1. **Tu banco de Pokémon**: una caja propia fuera de las partidas, como Pokémon HOME, para
   guardar a tus favoritos y pasarlos de un juego a otro cuando quieras.
2. **Pokédex global:** cuántas especies tienes entre todas tus partidas, y cuáles faltan para
   completar las 386.
3. **Eventos legales:** activar los boletos de evento que en su día solo se daban en tiendas
   (Boleto Eón, Boleto Misterioso y Boleto Aurora, Mapa Viejo), para ir a la Isla Faraway, la
   Isla Origen y el Ombligo del Mar y atrapar a Latios/Latias, Mew, Deoxys, Lugia y Ho-Oh
   jugando de verdad. PKHeX tiene esos datos.
4. **Copia de seguridad en la iMac:** mandar los respaldos a la iMac por Tailscale, para no
   depender solo del teléfono.
5. **Otras generaciones:** la R36S también corre GB/GBC (Gen 1 y 2) y DS (Gen 4 y 5, con
   DraStic), y PKHeX las soporta todas. Se agregarían después.
6. **Respaldar todas las partidas, no solo las de Pokémon:** la consola tiene también 007,
   Avatar, Yu-Gi-Oh! y otras. El respaldo funciona igual para cualquier juego; la edición
   queda como la parte especial de Pokémon.
7. **Seguir jugando en el teléfono:** con el emulador de la fase 5 adentro, llevarte una
   partida de la consola al teléfono y luego regresarla.
8. **Plan B sin cable:** abrir partidas desde la microSD con un lector, o desde cualquier
   archivo (compartir desde otra app), por si el cable falla un día.

## Riesgos

| Riesgo | Qué tan probable | Qué se hace |
|---|---|---|
| El cable da red a la consola, pero el teléfono no alcanza su SFTP | Bajo-medio: nadie lo ha probado | La fase 0 lo resuelve en 10 min. Plan B: lector de microSD o dongle Wi-Fi |
| El motor de .NET no arranca bien dentro del WebView de Android | Bajo: Chrome soporta WebAssembly, pero no está probado | Es lo primero de la fase 1. Plan B: la interfaz completa de PKHeX.Everywhere, compilada sin analíticas, dentro de la app |
| El motor está en versión 0.x y su API puede cambiar | Medio | Se fija una versión y se actualiza a propósito |
| El teléfono se carga con la batería de la consola | Seguro | La app lo avisa; consola cargada o cargador en el puerto DC |
| Editar con el juego abierto en la consola | Medio | Reglas 2, 4 y 5 de seguridad |
| Los hacks no se pueden editar | Alto para estos dos | Se respaldan igual; la edición solo para los juegos que PKHeX soporte |
| El cable link emulado entre dos juegos dentro de la app no funciona en Android | Medio: en mGBA de escritorio funciona, en Android no está probado | Prueba técnica al inicio de la fase 5; si falla, queda el intercambio rápido de la fase 4 |

## Lo que se necesita del usuario

1. Aprobar el plan (o decir qué cambiar) y elegir de las sugerencias.
2. La prueba de la fase 0, cuando tenga la consola y el cable a la mano.
3. Confirmar el nombre (PokeR36S Companion) y decidir el banco y seguir jugando en el teléfono.
