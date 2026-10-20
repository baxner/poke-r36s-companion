# PokeR36S Companion: reglas del proyecto

App Android (Kotlin + Compose) para respaldar, editar e intercambiar las partidas de Pokémon de
GBA de la R36S del usuario (ArkOS) por el cable USB con anclaje. Plan y decisiones: `docs/plan.md`.

## Siempre
- **Todo en español de México**: código, comentarios, commits, textos de la app y reportes. Sin
  spanglish. **El motor PKHeX da los nombres en inglés**: los de Pokémon, movimientos, objetos,
  habilidades, naturalezas y lugares se traducen por número con las tablas en español de PKHeX de
  `android/app/src/main/assets/textos/` (ver su `ORIGEN.md` y `data/textos/`). Nunca muestres en
  la app un nombre en inglés que venga del motor.
- **Cada subagente trabaja en su worktree** (`~/clawd/projects/poke-r36s-companion-wt/<tarea>`,
  rama `feature/<tarea>`). Tu shell arranca en otro directorio: haz `cd` a tu worktree **en
  cada comando**. Nunca hagas commit en el árbol principal ni en otra rama.
- Commits pequeños y frecuentes, con prefijo (`feat(consola):`, `fix(motor):`, `test(...)`,
  `docs:`).

## Nunca
- **Nunca toques la consola ni el teléfono del usuario**, ni el emulador si no te lo piden: las
  pruebas van contra servidores falsos en proceso (por ejemplo MINA SSHD para SSH/SFTP).
- **Nunca subas al repo las partidas reales** (`muestras/`, `android/app/src/androidTest/assets/muestras/`):
  son del usuario. Las pruebas versionadas usan partidas en blanco que crea el propio motor.
- Nada de push, de tocar `main`, de instalar cosas en el sistema ni de agregar dependencias que
  no estén en el encargo.
- La app **nunca habla con internet**: solo con la consola (direcciones privadas). El motor
  PKHeX corre sin red (ver `data/motor/MotorWebView.kt`, `PoliticaDeRed`).

## Compilar y probar
    cd android
    JAVA_HOME=/usr/local/opt/openjdk@17 perl -e 'alarm 1500; exec @ARGV' ./gradlew --no-daemon testDebugUnitTest assembleDebug

- `--no-daemon` siempre (no dejes daemons calentando la iMac) y **no** corras `./gradlew --stop`:
  otro subagente puede estar compilando.
- El motor PKHeX (`android/app/src/main/assets/motor/`, 28 MB) no se versiona: lo genera
  `scripts/build-motor.sh` (Node 20+). La app compila sin él; solo lo necesitan las pruebas en el
  emulador (`connectedDebugAndroidTest`).
- Pruebas: lo puro separado de la E/S y probado con JUnit; reloj fijo; capturas Roborazzi de cada
  pantalla nueva (revísalas como imagen).
- **Un motor falso no basta**: el `Json` de `Puente` manda los valores por defecto
  (`encodeDefaults`), porque sin eso una casilla `slot = 0` llegaba sin número y el motor de
  verdad la rechazaba, aunque el motor falso la aceptaba. Lo que cruce el puente lo vuelve a
  probar Opus con el motor real en el emulador (`connectedDebugAndroidTest`) antes de integrar.
- Corrutinas en pruebas: lo que corre en `backgroundScope` (observadores que nunca terminan) **no**
  avanza con `advanceUntilIdle()` si no queda nada en primer plano; usa `runCurrent()` (ver
  `data/conexion/ConexionConConsolaTest.kt`).

## Seguridad de las partidas (no negociable)
Ver `docs/plan.md`, "Reglas de seguridad para no perder una partida": respaldar antes de
escribir; no escribir si la partida de la consola cambió desde que se leyó; escritura
verificada byte por byte; nunca con el juego abierto; apartar (no borrar) los estados
automáticos (`.state.auto`) porque RetroArch del usuario retoma desde ahí al abrir el juego.
