# Project ES-DE Emulators Integration

## Get Started

Current focus:
- PS3 emulator `RPCSX`

## Phase 1

Fork repo `RPCSX`.

Start from the latest tag and create a local working branch.

Stop here and wait for user checkpoint before proceeding to the next phase.

## Phase 2

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

## Phase 3

Add the minimal change starting from `MainActivity` to accept an external ISO game file path.

Implementation notes:
- keep parsing/utils/helpers under a sub-package such as `something.esde`
- parse ISO path to game ID
- construct the launchable `path/to/gameID`
- have `MainActivity` launch the game the same way `RPCSX` already does internally

Debug, test & build release
```shell
$ ./gradlew :app:testDebugUnitTest --tests net.rpcsx.esde.Ps3EsdeIsoResolverTest

# compile
$ ./gradlew :app:compileDebugKotlin

# build release 
$ ./gradlew :app:compileDebugKotlin
# target file will be at: rpcsx-ui-android/app/build/outputs/apk/release/rpcsx-release.apk
```

## Phase 4

Manual testing.

Wait on user correspondence as we go together.

Stay in this phase until success criteria is met:
- a PS3 game launches correctly from `ES-DE`

## Phase 5

Visit the bug where a game does not exit cleanly and fix it as needed.

## Note

Keep this file as a reference point before and during implementation.

Let's have some fun.
