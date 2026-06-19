# Project ES-DE Emulators Integration

## Get Started

Current focus:
- PS3 emulator `RPCSX`

---

| **PHASE 1** |
| --- |

Fork repo `RPCSX`.

Start from the latest tag and create a local working branch.

Stop here and wait for user checkpoint before proceeding to the next phase.

---

| **PHASE 2** |
| --- |

Manually package the `.apk` from this fork as-is.

Technical notes from the first successful local build on macOS:
- initialize git submodules before building:
  - `git submodule update --init --recursive`
- use the local Gradle wrapper from the fork:
  - `./gradlew assembleRelease`
- Android SDK root used on this machine:
  - `/opt/homebrew/share/android-commandlinetools`
- additional SDK components that had to be present or auto-installed:
  - `NDK (Side by side) 29.0.13113456`
  - `Android SDK Build-Tools 35.0.0`
  - `Android SDK Platform 36`
  - `CMake 3.31.6`
- if building from a shell, set:
  - `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`
  - `ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools`
- release build output path:
  - `app/build/outputs/apk/release/rpcsx-release.apk`
- expected difference versus stock build if no release env vars are provided:
  - built `versionName` becomes `local`
  - APK hash differs from stock
  - fallback debug-style signing is used if no custom keystore env vars are set

## Phase 2b

Install the built `.apk` to the Droid and verify it still works the same way the stock `RPCSX` did.

At the droid go through the initialization on RPCSX GUI again

Tail firmware installation process
```shell
$ adb -s "$DROID_ADB_SERIAL" logcat -v brief | rg --line-buffered 'W/RPCS3|I/net\.rpcsx|Firmware Installation|Progress: file|Progress: module|Compiling PPU|Scudo|OOM|Fatal signal|ANR'
```

---

| **PHASE 3** |
| --- |

Add the minimal change starting from `MainActivity` to accept an external ISO game file path.

Implementation notes:
- keep parsing/utils/helpers under a sub-package such as `something.esde`
- parse ISO path to game ID
- construct the launchable `path/to/gameID`
- have `MainActivity` launch the game the same way `RPCSX` already does internally

## Phase 3b

Side improvements added around the main ISO-path launch feature:
- added an ISO-only ES-DE helper under `net.rpcsx.esde` to parse `PARAM.SFO`, extract
  `TITLE_ID`, and resolve the imported RPCSX game directory
- added unit tests for the ISO resolver with a synthetic minimal PS3 ISO fixture
- added an explicit external boot contract for ES-DE:
    - action: `net.rpcsx.action.BOOT_ISO`
    - extra: `path`
- added a normalized external-launch failure path with the message:
    - `Game must be preinstalled in RPCSX from an .iso file`
- added a one-time default performance profile seeding step so fresh installs inherit the
  currently known-good RK3588 / DuckTales baseline
- changed the on-screen controller to start hidden by default and remember the user's OSC
  visibility toggle preference
- changed Android Back handling inside `RPCSXActivity` so pressing Back while in a game opens
  the in-game RPCSX home menu instead of bouncing to the main RPCSX library UI first
- added a top-level `Makefile` convenience wrapper:
    - `make test`
    - `make release`

Debug, test & build release
```shell
$ ./gradlew :app:testDebugUnitTest --tests net.rpcsx.esde.Ps3EsdeIsoResolverTest

# compile
$ ./gradlew :app:compileDebugKotlin

# build release 
$ make release
# target file will be at: rpcsx-ui-android/app/build/outputs/apk/release/rpcsx-release.apk
```

---

| **PHASE 4** |
| --- |

Manual testing.

Wait on user correspondence as we go together.

Stay in this phase until success criteria is met:
- a PS3 game launches correctly from `ES-DE`

---

| **PHASE 5** |
| --- |

Visit the bug where a game does not exit cleanly and fix it as needed.

Additional Phase 5 changes completed around ES-DE / day-to-day usability:
- disabled the automatic RPCSX UI self-update popup on launch
- rationale:
  - local fork builds intentionally use `versionName=local`
  - the stock update check kept surfacing a distracting `UI Update Available` dialog
  - that popup was noisy during startup and could interfere with ES-DE-driven launch testing
- implementation shape:
  - keep the rest of the game/library flow intact
  - suppress only the automatic UI update prompt path in `GamesScreen`

- added first-run / ES-DE-launch prompting for Android `All files access`
- rationale:
  - ES-DE integration currently passes a raw `%ROM%` filesystem path for PS3 `.iso` content
  - RPCSX needs broader external-storage access to reopen that ISO path during ES-DE launch
  - without this, the app fails with `EACCES (Permission denied)` even if the game was already
    imported inside RPCSX
- implementation shape:
  - declare `android.permission.MANAGE_EXTERNAL_STORAGE`
  - on startup and on ES-DE boot attempts, detect whether `All files access` is missing
  - if missing, show a clear dialog and deep-link to the app-specific Android settings screen
  - use a more accurate ES-DE launch failure message:
    - `Grant All files access to RPCSX, then retry the game`

## Phase 5b

Deferred follow-up around Android `Back` behavior while the in-game RPCSX home menu is already
open.

Findings and conclusions:
- `Back` while actively in-game should open the RPCSX home menu
- `Back` again while that home menu is already open should ideally close the menu and return to
  the live game view
- this is the desired UX because it matches the behavior of other emulators more closely

Current state of the fork:
- `RPCSXActivity.onBackPressed()` currently routes `Back` into `RPCSX.instance.openHomeMenu()`
  whenever the emulator is in an active state
- this means the Android/Kotlin layer can open the menu, but it does not know whether the native
  menu is already open
- the currently exposed Android/JNI surface does not provide:
  - `isHomeMenuOpen()`
  - `closeHomeMenu()`
  - `toggleHomeMenu()`

Implementation conclusion:
- do not try to fake this purely from Android activity state
- the cleaner future fix is to inspect the native RPCSX side and expose one minimal JNI method for
  menu state or menu toggling

Best candidate future API shapes:
- `isHomeMenuOpen()`
- `toggleHomeMenu()`
- `closeHomeMenu()`

If we revisit this later, the next step should be:
- inspect the native RPCSX Android binding / C++ menu implementation
- determine whether menu open/close state already exists internally
- expose the smallest JNI surface needed so Android `Back` can:
  1. open the menu if gameplay is active
  2. close the menu if it is already open

---

## Note

Keep this file as a reference point before and during implementation.

Let's have some fun.
