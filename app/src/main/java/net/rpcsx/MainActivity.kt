package net.rpcsx

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.esde.EsdeBootContract
import net.rpcsx.esde.Ps3EsdeIsoResolver
import net.rpcsx.ui.navigation.AppNavHost
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GitHub
import net.rpcsx.utils.RpcsxUpdater
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var unregisterUsbEventListener: () -> Unit
    private var storageAccessPromptShown = false
    @Volatile
    private var esdeImportInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GeneralSettings.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!RPCSX.initialized) {
            Permission.PostNotifications.requestPermission(this)

            with(getSystemService(NOTIFICATION_SERVICE) as NotificationManager) {
                val channel = NotificationChannel(
                    "rpcsx-progress",
                    "Installation progress",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

                createNotificationChannel(channel)
            }

            RPCSX.rootDirectory = applicationContext.getExternalFilesDir(null).toString()
            if (!RPCSX.rootDirectory.endsWith("/")) {
                RPCSX.rootDirectory += "/"
            }

            lifecycleScope.launch {
                GameRepository.load()
            }

            FirmwareRepository.load()
            GitHub.initialize(this)

            var rpcsxLibrary = GeneralSettings["rpcsx_library"] as? String
            val rpcsxUpdateStatus = GeneralSettings["rpcsx_update_status"]
            val rpcsxPrevLibrary = GeneralSettings["rpcsx_prev_library"] as? String

            if (rpcsxLibrary != null) {
                if (rpcsxUpdateStatus == false && rpcsxPrevLibrary != null) {
                    GeneralSettings["rpcsx_library"] = rpcsxPrevLibrary
                    GeneralSettings["rpcsx_installed_arch"] = GeneralSettings["rpcsx_prev_installed_arch"]
                    GeneralSettings["rpcsx_prev_installed_arch"] = null
                    GeneralSettings["rpcsx_prev_library"] = null
                    GeneralSettings["rpcsx_bad_version"] = RpcsxUpdater.getFileVersion(File(rpcsxLibrary))
                    GeneralSettings.sync()

                    File(rpcsxLibrary).delete()
                    rpcsxLibrary = rpcsxPrevLibrary

                    AlertDialogQueue.showDialog("RPCSX Update Failed", "Failed to load new version, previous version was restored")
                } else if (rpcsxUpdateStatus == null) {
                    GeneralSettings["rpcsx_update_status"] = false
                    GeneralSettings.sync()
                }

                RPCSX.openLibrary(rpcsxLibrary)
            }

            val nativeLibraryDir =
                packageManager.getApplicationInfo(packageName, 0).nativeLibraryDir
            RPCSX.nativeLibDirectory = nativeLibraryDir

            if (RPCSX.activeLibrary.value != null) {
                RPCSX.instance.initialize(RPCSX.rootDirectory, UserRepository.getUserFromSettings())
                val gpuDriverPath = GeneralSettings["gpu_driver_path"] as? String
                val gpuDriverName = GeneralSettings["gpu_driver_name"] as? String

                if (gpuDriverPath != null && gpuDriverName != null) {
                    RPCSX.instance.setCustomDriver(gpuDriverPath, gpuDriverName, nativeLibraryDir)
                }

                lifecycleScope.launch {
                    UserRepository.load()
                }

                RPCSX.initialized = true
                RpcsxDefaultProfile.applyIfNeeded()

                thread {
                    RPCSX.instance.startMainThreadProcessor()
                }

                thread {
                    RPCSX.instance.processCompilationQueue()
                }

                GeneralSettings["rpcsx_update_status"] = true
                if (rpcsxPrevLibrary != null) {
                    if (rpcsxLibrary != rpcsxPrevLibrary) {
                        File(rpcsxPrevLibrary).delete()
                    }

                    GeneralSettings["rpcsx_prev_library"] = null
                    GeneralSettings["rpcsx_prev_installed_arch"] = null
                    GeneralSettings.sync()
                }
            }

            val updateFile = File(RPCSX.rootDirectory + "cache", "rpcsx-${BuildConfig.Version}.apk")
            if (updateFile.exists()) {
                updateFile.delete()
            }
        }

        maybePromptForExternalStorageAccess()

        if (tryBootEsdeIsoIntent(intent)) {
            unregisterUsbEventListener = {}
            finish()
            return
        }

        setContent {
            RPCSXTheme {
                AppNavHost()
            }
        }

        if (RPCSX.activeLibrary.value != null) {
            unregisterUsbEventListener = listenUsbEvents(this)
        } else {
            unregisterUsbEventListener = {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybePromptForExternalStorageAccess()
        if (tryBootEsdeIsoIntent(intent)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterUsbEventListener()
    }

    private fun tryBootEsdeIsoIntent(intent: Intent?): Boolean {
        val isoPath = extractEsdeIsoPath(intent) ?: return false

        if (!hasExternalStorageManagerAccess()) {
            Log.w("RPCSX", "Missing all files access for ES-DE ISO boot request: $isoPath")
            promptForExternalStorageAccess(bootRequest = true)
            return failEsdeBoot(EsdeBootContract.StoragePermissionErrorMessage)
        }

        if (!RPCSX.initialized || RPCSX.activeLibrary.value == null) {
            Log.w("RPCSX", "Ignoring ES-DE boot request before RPCSX initialization: $isoPath")
            return failEsdeBoot()
        }

        val resolved = runCatching { Ps3EsdeIsoResolver.resolve(isoPath) }.getOrElse { error ->
            Log.e("RPCSX", "Failed to resolve ES-DE ISO boot request: $isoPath", error)
            return failEsdeBoot()
        }

        val importedGamePath = resolved.importedGamePath
        if (!File(importedGamePath).exists()) {
            Log.i(
                "RPCSX",
                "Imported game directory missing for ES-DE launch, attempting ISO import: ${resolved.sourceIsoPath} -> $importedGamePath"
            )
            queueEsdeIsoImportAndBoot(resolved)
            return false
        }

        GameRepository.find(importedGamePath)?.let(GameRepository::onBoot)

        Log.i(
            "RPCSX",
            "Launching imported game for ES-DE ISO ${resolved.sourceIsoPath} -> $importedGamePath"
        )
        startActivity(Intent(this, RPCSXActivity::class.java).apply {
            putExtra(EsdeBootContract.PathExtra, importedGamePath)
        })
        return true
    }

    private fun queueEsdeIsoImportAndBoot(resolved: net.rpcsx.esde.Ps3EsdeIsoTarget) {
        if (esdeImportInFlight) {
            Log.i("RPCSX", "Ignoring duplicate ES-DE import request while another import is active")
            return
        }

        esdeImportInFlight = true
        val importedGamePath = resolved.importedGamePath
        val sourceIsoPath = resolved.sourceIsoPath
        val progressId = ProgressRepository.create(
            this,
            "ISO Import"
        )

        thread {
            try {
                val imported = runCatching {
                    ParcelFileDescriptor.open(
                        File(sourceIsoPath),
                        ParcelFileDescriptor.MODE_READ_ONLY
                    ).use { input ->
                        val fd = input.fd
                        if (!RPCSX.instance.isInstallableFile(fd)) {
                            Log.w("RPCSX", "ES-DE ISO is not installable by RPCSX: $sourceIsoPath")
                            return@runCatching false
                        }

                        RPCSX.instance.install(fd, progressId)
                    }
                }.getOrElse { error ->
                    Log.e("RPCSX", "Failed to import ES-DE ISO: $sourceIsoPath", error)
                    false
                }

                if (!imported) {
                    ProgressRepository.onProgressEvent(
                        progressId,
                        -1,
                        0,
                        EsdeBootContract.ErrorMessage
                    )
                    runOnUiThread {
                        failEsdeBoot()
                    }
                    return@thread
                }

                Log.i("RPCSX", "ISO import completed for ES-DE launch: $sourceIsoPath")
                RPCSX.instance.collectGameInfo(importedGamePath, -1L)
                Log.i("RPCSX", "Collected game info after ES-DE ISO import: $importedGamePath")

                if (!File(importedGamePath).exists()) {
                    Log.w(
                        "RPCSX",
                        "ISO import completed but imported game directory is still missing: $importedGamePath"
                    )
                    runOnUiThread {
                        failEsdeBoot()
                    }
                    return@thread
                }

                Handler(Looper.getMainLooper()).post {
                    GameRepository.find(importedGamePath)?.let(GameRepository::onBoot)
                    Log.i(
                        "RPCSX",
                        "Launching imported game after ES-DE ISO import: $sourceIsoPath -> $importedGamePath"
                    )
                    applicationContext.startActivity(Intent(applicationContext, RPCSXActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EsdeBootContract.PathExtra, importedGamePath)
                    })
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
            } finally {
                esdeImportInFlight = false
            }
        }
    }

    private fun extractEsdeIsoPath(intent: Intent?): String? {
        if (intent?.action != EsdeBootContract.BootIsoAction) {
            return null
        }
        return intent.getStringExtra(EsdeBootContract.PathExtra)?.takeIf { it.isNotBlank() }
    }

    private fun failEsdeBoot(message: String = EsdeBootContract.ErrorMessage): Boolean {
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(Intent.EXTRA_TEXT, message)
        )
        AlertDialogQueue.showDialog("RPCSX ES-DE Launch Failed", message)
        return false
    }

    private fun maybePromptForExternalStorageAccess() {
        if (!hasExternalStorageManagerAccess()) {
            promptForExternalStorageAccess(bootRequest = false)
        }
    }

    private fun hasExternalStorageManagerAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun promptForExternalStorageAccess(bootRequest: Boolean) {
        if (storageAccessPromptShown || hasExternalStorageManagerAccess()) {
            return
        }
        storageAccessPromptShown = true

        AlertDialog.Builder(this)
            .setTitle("Allow Files Access")
            .setMessage(
                if (bootRequest) {
                    "RPCSX needs All files access to open PS3 .iso files launched from ES-DE. Grant access in Android Settings, then retry the game."
                } else {
                    "RPCSX needs All files access to open PS3 .iso files from shared storage. Grant access in Android Settings."
                }
            )
            .setCancelable(true)
            .setNegativeButton("Later", null)
            .setPositiveButton("Open Settings") { _, _ ->
                openExternalStorageManagerSettings()
            }
            .show()
    }

    private fun openExternalStorageManagerSettings() {
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        runCatching { startActivity(intent) }
            .recoverCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            .onFailure { error ->
                Log.e("RPCSX", "Failed to open all files access settings", error)
            }
    }
}
