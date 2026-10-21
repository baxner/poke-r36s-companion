# PokeR36S Companion

[Español](README.md) · **English**

An Android app that looks after your Game Boy Advance Pokémon saves on an **R36S running ArkOS**:
it backs them up over the USB cable, edits them like PKHeX, trades Pokémon between them and lets
you keep playing them on your phone, all without internet.

> **The app's interface is in Spanish (Mexico)** for now. The code, comments and design notes are
> in Spanish too.

> Fan project, not affiliated with Nintendo, Creatures Inc. or GAME FREAK. Pokémon and its names
> are trademarks of their respective owners. **No ROMs or game images are included**: use your own.

## What it does

- **Automatic backups.** When you connect the console, it keeps every version of your saves
  (`.srm`) and RetroArch states on the phone, with history and "what changed" (badges, team,
  Pokédex, money). Works for any GBA game, not only Pokémon.
- **Editor.** Pokémon (species, level, moves, held item, nature, shiny, nickname…), bag, money and
  trainer, with PKHeX's own legality warnings. Names come from PKHeX's Spanish tables.
- **Bank.** A box of your own outside the saves, to keep your favorites and move them from one game
  to another.
- **Global Pokédex.** All 386 Gen 3 species across all your saves and the bank: which ones you
  have, which ones you've seen and where to catch them in your games.
- **Quick trade.** Pick which Pokémon each save sends and both saves are written, with trade
  evolutions just like in the games.
- **Real trade.** Both saves run at the same time on the phone with mGBA, **joined by the emulated
  link cable**: go to the Cable Club in both games and trade as you would on two consoles. When
  you're done you see what changed before writing them back to the console.
- **Keep playing on your phone.** Take a save to your phone, play with touch controls or a
  Bluetooth gamepad, and bring it back to the console.
- **Guided connection.** It detects the cable and USB tethering, finds the console on its own (its
  IP changes on every connection), pins its SSH key the first time, and includes an Introduction
  and a Diagnostics screen for first-time users.

## What you need

- An R36S (or clone) with **ArkOS** and "Enable Remote Services" turned on (factory SSH login
  `ark`/`ark`).
- An Android 8+ phone.
- A **USB-A to C cable with an OTG adapter** on the console side. With a C-to-C cable the phone's
  USB tethering option is usually greyed out. The console powers the phone: keep it charged.
- **USB tethering** turned on in the phone (the app takes you to that screen).

## How it keeps your saves safe

Before writing anything to the console, the app:

1. backs up what was there;
2. checks that the console's save is still the one it read (if you played in the meantime, it
   writes nothing and asks you);
3. refuses to write while the game is open in RetroArch;
4. writes to a temporary file, verifies it byte by byte, swaps it with the original and forces the
   write to the SD card;
5. **moves aside** (never deletes) the game's `.state.auto`, because RetroArch loads it when the
   game starts and it would overwrite what was written.

A trade is two writes: if the second one fails, the first one is put back as it was.

The app **never talks to the internet**: only to the console, over the cable's private network.
The PKHeX engine runs inside the app, offline.

## How it's built

- **Kotlin + Jetpack Compose** (Material 3), Room, WorkManager and JSch for SSH/SFTP.
- **PKHeX engine**: [`@pkhex-everywhere/engine`](https://www.npmjs.com/package/@pkhex-everywhere/engine)
  (PKHeX.Core compiled to WebAssembly) running in a hidden WebView, behind a small bridge
  (`engine-bridge/`). The WebView may only load the app's own files.
- **Emulator**: [mGBA](https://mgba.io) 0.10.5 built with the NDK (module `android/emulador/`), with
  its own C layer: each GBA on its own thread, AAudio sound, unsmoothed pixels, and mGBA's link cable
  driven by callbacks ported from its desktop frontend. The save is kept in memory at the same size
  as the console's RetroArch core uses, so what goes back to the R36S is exactly what that core
  expects.
- The design, decisions and measurements against a real console are in [`docs/plan.md`](docs/plan.md)
  (Spanish). `AGENTS.md` holds the rules it was built with (with AI agents).

## Building

You need JDK 17, the Android SDK with NDK 27.1.12297006 and CMake 3.22.1, and Node 20+.

```sh
scripts/build-motor.sh     # the PKHeX engine (npm) → android/app/src/main/assets/motor/
scripts/bajar-mgba.sh      # mGBA 0.10.5, pinned by SHA-256 → third_party/mgba/
scripts/bajar-sprites.sh   # optional: PokeAPI sprites → android/app/src/main/assets/sprites/
cd android
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

None of these are committed. Without the sprites the app works the same, just without Pokémon
pictures.

### Tests

- `testDebugUnitTest`: 500+ JVM tests (Robolectric), including Roborazzi screenshots of every
  screen and an in-process fake SSH/SFTP server.
- `connectedDebugAndroidTest` (emulator or phone): the real PKHeX engine and the real mGBA. The
  emulator tests use a tiny ROM assembled inside the test itself; the ones that use real saves or
  games only run if you put your own in the tests' `assets/muestras/` folders (ignored by git).

## License

**GPL-3.0-or-later** (see [`LICENSE`](LICENSE)), because it includes the PKHeX engine.

Credits:

- [PKHeX](https://github.com/kwsch/PKHeX) by Kurt (kwsch) and contributors, and
  [PKHeX.Everywhere](https://github.com/arleypadua/PKHeX.Everywhere) by Arley Pádua for the
  WebAssembly engine (GPL-3.0). The Spanish name tables come from PKHeX (see
  `android/app/src/main/assets/textos/ORIGEN.md`).
- [mGBA](https://mgba.io) by Vicki Pfau (endrift) and contributors (Mozilla Public License 2.0).
- [PokeAPI/sprites](https://github.com/PokeAPI/sprites), if you download them with the script.
