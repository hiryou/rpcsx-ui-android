package net.rpcsx

import android.util.Log
import net.rpcsx.utils.GeneralSettings

/**
 * Seeds the app with a conservative default profile for weaker Android devices.
 *
 * The values here mirror the current DuckTales-tuned profile that is running smoothly on the
 * target RK3588 Droid. We apply them once per profile version through RPCSX's own settings API so
 * future fresh installs inherit the same baseline without requiring manual GUI changes.
 */
object RpcsxDefaultProfile {
    private const val ProfileVersion = 1
    private const val ProfileVersionKey = "rpcsx_default_profile_version"
    private const val ShowOscKey = "show_osc"

    private val settings = linkedMapOf(
        "@@Core@@PPU Decoder" to "\"LLVM Recompiler (Legacy)\"",
        "@@Core@@PPU Threads" to "2",
        "@@Core@@Max LLVM Compile Threads" to "1",
        "@@Core@@LLVM Precompilation" to "false",
        "@@Core@@Preferred SPU Threads" to "1",
        "@@Core@@Max SPURS Threads" to "1",
        "@@Core@@Accurate RSX reservation access" to "false",
        "@@Video@@Renderer" to "\"Vulkan\"",
        "@@Video@@Resolution" to "\"1280x720\"",
        "@@Video@@MSAA" to "\"Disabled\"",
        "@@Video@@Shader Mode" to "\"Async Shader Recompiler\"",
        "@@Video@@Shader Precision" to "\"Low\"",
        "@@Video@@Multithreaded RSX" to "false",
        "@@Video@@Accurate ZCULL stats" to "false",
        "@@Video@@Resolution Scale" to "50",
        "@@Video@@Shader Compiler Threads" to "2",
        "@@Video@@Vblank NTSC Fixup" to "false",
        "@@Input/Output@@Background input enabled" to "true",
    )

    /**
     * Applies the default emulator profile and overlay visibility preference once.
     */
    fun applyIfNeeded() {
        if ((GeneralSettings[ProfileVersionKey] as Int?) == ProfileVersion) {
            return
        }

        val failedPaths = mutableListOf<String>()
        settings.forEach { (path, value) ->
            if (!RPCSX.instance.settingsSet(path, value)) {
                failedPaths += path
            }
        }

        if (GeneralSettings[ShowOscKey] == null) {
            GeneralSettings[ShowOscKey] = false
        }

        if (failedPaths.isEmpty()) {
            GeneralSettings[ProfileVersionKey] = ProfileVersion
            GeneralSettings.sync()
        } else {
            Log.w("RPCSX", "Failed to apply default profile paths: ${failedPaths.joinToString()}")
        }
    }
}
