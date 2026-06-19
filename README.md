<div align="center">

# RPCSX-UI-Android

*An experimental Android native UI for RPCSX emulator*

[![](https://img.shields.io/discord/252023769500090368?color=5865F2&logo=discord&logoColor=white)](https://discord.gg/t6dzA4wUdG)

</div>

> **Warning**: Do not ask for link to games or system files. Piracy is not permitted on the GitHub nor in the Discord.


## Contributing

If you want to contribute as a developer, please contact us in the [Discord](https://discord.gg/t6dzA4wUdG)

## Requirements

Android 12+


## License

RPCSX-UI-Android is licensed under GPLv2 license except directories containing their own LICENSE file, or files containing their own license.


## Android Upgrade Note

For this fork, prefer in-place upgrades with:

```sh
adb install -r app/build/outputs/apk/release/rpcsx-release.apk
```

Why this matters:
- `adb install -r` replaces the APK while retaining the existing Android app identity, data
  directory, and already-processed assets.
- This avoids repeating costly initialization work such as firmware setup, imported game library
  population, and long first-run compilation/cache generation.

Important retained RPCSX state on the Droid lives under:
- `/storage/emulated/0/Android/data/net.rpcsx/files/`

Most important subpaths:
- `/storage/emulated/0/Android/data/net.rpcsx/files/config/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/config/dev_flash/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/config/games/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/config/dev_hdd0/game/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/cache/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/cache/cache/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/cache/ppu_progs/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/cache/spu_progs/`
- `/storage/emulated/0/Android/data/net.rpcsx/files/games.json`
- `/storage/emulated/0/Android/data/net.rpcsx/files/fw.json`

These retain, for example:
- firmware installation state
- imported game directories such as `.../config/games/BLUS31368`
- compiled shader / PPU / SPU cache artifacts under `cache/`
- emulator settings and library metadata

For riskier refactors, the simplest backup is the whole tree:

```sh
adb pull /storage/emulated/0/Android/data/net.rpcsx/files/ ./rpcsx-files-backup
```
