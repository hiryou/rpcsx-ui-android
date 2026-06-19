package net.rpcsx.esde

/**
 * External boot contract used by frontends such as ES-DE.
 *
 * The caller provides a source PS3 ISO file path in [PathExtra]. RPCSX resolves that ISO to the
 * already-imported game directory under its managed storage, then internally boots that path.
 */
object EsdeBootContract {
    const val BootIsoAction = "net.rpcsx.action.BOOT_ISO"
    const val PathExtra = "path"
    const val ErrorMessage = "Game must be preinstalled in RPCSX from an .iso file"
    const val StoragePermissionErrorMessage =
        "Grant All files access to RPCSX, then retry the game"
}
