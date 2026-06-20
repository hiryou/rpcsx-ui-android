# Fat Princess RPCSX Core Troubleshooting

## Scope

This note captures the troubleshooting session around the PS3 title:

- `Fat Princess`
- title ID: `NPUA80139`

Primary goals of the session:

- determine which `librpcsx` core the Android UI wrapper was actually loading
- verify whether the wrapper honored the selected `Release` / `Development` core channel
- add a fast path for manually loading arbitrary `librpcsx` `.so` builds
- test whether `Fat Princess` crashed the same way across multiple `librpcsx` revisions

## Key Findings

### 1. The Android UI was loading draft `librpcsx` cores from `rpcsx-build`

The active native core is `librpcsx`, not the Kotlin/Compose wrapper itself.

Relevant code:

- `app/src/main/cpp/native-lib.cpp`
- `app/src/main/java/net/rpcsx/RPCSX.kt`
- `app/src/main/java/net/rpcsx/utils/RpcsxUpdater.kt`

The wrapper loads a native `.so` and calls exported symbols such as:

- `_rpcsx_boot`
- `_rpcsx_surfaceEvent`
- `_rpcsx_settingsSet`

The loaded core reported versions such as:

- `20251011-e27926d Draft`
- `20251011-8cfb4e8 Draft`

This confirms that the real emulator core in use came from the draft/build feed, not just from the UI APK version.

### 2. The wrapper previously ignored the selected RPCSX core channel

Before the fix, `RpcsxUpdater` was effectively hardcoded to the development/build feed instead of honoring the stored `rpcsx_channel` preference.

This was independent from the separate UI self-update popup.

Important distinction:

- UI self-update popup:
  - about the Android wrapper APK itself
  - disabled separately in `GamesScreen`
- RPCSX core update path:
  - about `librpcsx`
  - handled by `RpcsxUpdater`

Fix applied:

- `RpcsxUpdater` now reads `app_prefs["rpcsx_channel"]`
- fallback remains `ReleaseRpcsxChannel` if unset

### 3. A diagnostics card was added to the library home screen

The RPCSX library screen now shows the current core state above the game tiles.

Displayed fields:

- loaded version
- raw core version
- selected channel
- configured arch
- device ABI
- actual library filename

This made it possible to verify which native core the app was really using on-device.

### 4. A direct URL core override was added for rapid regression testing

A minimal textbox + `Update Core` button was added to the RPCSX download-channel screen.

Purpose:

- paste a direct `.so` URL such as:
  - `https://github.com/RPCSX/rpcsx-build/releases/download/v20251011-e27926d/librpcsx-android-arm64-v8a-armv8-a.so`
- download it directly into app-private storage
- install it as the active `librpcsx`
- restart the app

Implementation intentionally skipped formal validation/renaming during this session to keep iteration speed high.

### 5. `Fat Princess` consistently crashed in the RSX render path

The repeated fatal signature was:

```text
Segfault reading location 0000000000000048 at 000000735c8e3158.
Emu Thread Name: 'RSX.W1'.
```

This was the same across all completed test cases in the sweep.

Interpretation:

- `0x48` strongly suggests a null/near-null pointer field dereference in native code
- `RSX` here is the PS3 GPU emulation/rendering subsystem
- `RSX.W1` / `RSX.W2` are native RSX worker threads

This does **not** look like:

- ES-DE handoff failure
- bad ISO/PKG path handling
- a one-off stale cache artifact
- a single bad draft build

It does look like:

- a repeatable native RSX-path crash for this title on the current Android/Mali stack

## Test Method

### Game-under-test flow

For each selected `librpcsx` build:

1. make the target core the active library
2. restart `net.rpcsx`
3. launch:
   - `RPCSXActivity`
   - path:
     - `/storage/emulated/0/Android/data/net.rpcsx/files/config/dev_hdd0/game/NPUA80139`
4. wait about one minute
5. inject the controller `A` press repeatedly to advance through the terms/accept screen
6. capture the first fatal log signature

### Controller `A` button replay

The active controller was:

- `8BitDo 8BitDo Pro 2`
- device:
  - `/dev/input/event5`

Observed raw `A` press/release:

```text
/dev/input/event5: 0004 0004 00090001
/dev/input/event5: 0001 0130 00000001
/dev/input/event5: 0000 0000 00000000
/dev/input/event5: 0004 0004 00090001
/dev/input/event5: 0001 0130 00000000
/dev/input/event5: 0000 0000 00000000
```

Equivalent replay:

```text
type=4 code=4 value=589825
type=1 code=304 value=1
type=0 code=0 value=0
type=4 code=4 value=589825
type=1 code=304 value=0
type=0 code=0 value=0
```

### Core swap method used during the sweep

For the large regression sweep, the session used the same effective outcome as the direct URL UI, but by writing the selected core directly into app-private storage and updating app prefs via ADB root.

This was done for reliability and speed.

Relevant on-device paths:

- core files:
  - `/data/user/0/net.rpcsx/files/`
- active core preference:
  - `/data/user/0/net.rpcsx/shared_prefs/app_prefs.xml`
- effective library path format:
  - `/data/data/net.rpcsx/files/<core-file>.so`

## Completed Sweep

The sweep was intentionally stopped before finishing page 1, but enough cases were completed to answer the main question.

Completed cases:

- `27` total core test cases
- `14` tags reached
- both `armv8-a` and `armv8.1-a` were tested for most reached tags

Tested tags:

- `v20251011-e27926d`
- `v20251011-8cfb4e8`
- `v20251001-089c388`
- `v20250923-fc4339d`
- `v20250923-b076d68`
- `v20250922-8799c76`
- `v20250922-0d0b75f`
- `v20250921-f7651d7`
- `v20250921-dea473b`
- `v20250921-c259bf4`
- `v20250921-b05479b`
- `v20250921-79d3f27`
- `v20250921-70fa577`
- `v20250921-36b9e96` (`armv8-a` only before the sweep was stopped)

Observed result:

- `27 / 27` completed cases crashed
- `27 / 27` had the same primary error
- `27 / 27` reported the same crash thread:
  - `RSX.W1`

Repeated primary error:

```text
Segfault reading location 0000000000000048 at 000000735c8e3158.
```

## Conclusion

The session strongly suggests:

- `Fat Princess` is **not** failing because of only one bad `librpcsx` draft build
- `Fat Princess` is **not** being rescued by switching between:
  - `armv8-a`
  - `armv8.1-a`
- the failure is stable across a meaningful rollback window of `rpcsx-build` page-1 releases

Most likely explanation:

- a broader native RSX-path bug for this title on the current Android/Mali runtime path

What this session ruled out with decent confidence:

- one-off wrapper UI bug
- one-off direct URL updater bug
- one-off core revision regression
- one-off selected-arch mismatch

What this session did **not** prove:

- whether a much older core outside page 1 behaves differently
- whether another OS image / Mali Vulkan stack changes the outcome
- whether a native RPCSX core fix would resolve the RSX null-dereference

## Suggested Next Steps

If this title is worth pursuing further, the next highest-signal options are:

1. continue the sweep further back than page 1
2. test the same title/core on a different GPU/driver platform
3. inspect the native RSX crash path in the RPCSX core itself
4. compare behavior on a different Orange Pi / Android image or newer Mali Vulkan stack

For wrapper-side work, the most useful additions already landed:

- visible loaded-core diagnostics
- proper channel selection handling
- direct URL core override for fast bisecting
