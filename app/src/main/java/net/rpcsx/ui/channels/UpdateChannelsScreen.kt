package net.rpcsx.ui.channels

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import net.rpcsx.ui.settings.components.core.PreferenceSubtitle
import net.rpcsx.ui.settings.components.preference.RegularPreference
import net.rpcsx.utils.GitHub
import net.rpcsx.utils.RpcsxUpdater
import kotlinx.coroutines.launch
import java.io.File

const val DefaultGpuDriverChannel = "https://github.com/K11MCH1/AdrenoToolsDrivers"
const val ReleaseUiChannel = "https://github.com/RPCSX/rpcsx-ui-android"
const val DevUiChannel = "https://github.com/RPCSX/rpcsx-ui-android-build"
const val ReleaseRpcsxChannel = "https://github.com/RPCSX/rpcsx"
const val DevRpcsxChannel = "https://github.com/RPCSX/rpcsx-build"

fun channelToUiText(channel: String, releaseRepo: String, devRepo: String): String {
    if (channel == releaseRepo) return "Release"
    if (channel == devRepo) return "Development"
    return channel
}

fun uiTextToChannel(channel: String, releaseRepo: String, devRepo: String): String {
    if (channel == "Release") return releaseRepo
    if (channel == "Development") return devRepo
    return channel
}


fun channelsToUiText(list: List<String>, releaseRepo: String, devRepo: String): List<String> {
    return list.map { channelToUiText(it, releaseRepo, devRepo) }
}

fun uiTextToChannels(list: List<String>, releaseRepo: String, devRepo: String): List<String> {
    return list.map { uiTextToChannel(it, releaseRepo, devRepo) }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateChannelsScreen(
    navigateBack: () -> Unit,
    navigateTo: (id: String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var directCoreUrl by remember { mutableStateOf("") }
    var isUpdatingCore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, topBar = {
        TopAppBar(
            title = { Text(text = "Download Channels", fontWeight = FontWeight.Medium) },
            scrollBehavior = topBarScrollBehavior,
            navigationIcon = {
                IconButton(
                    onClick = navigateBack
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft, null)
                }
            })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            RegularPreference(
                title = "RPCSX UI Android Update Channel",
                leadingIcon = null,
                subtitle = { PreferenceSubtitle(text = prefs.getString("ui_channel", ReleaseUiChannel)!!) },
                onClick = {
                    navigateTo("ui_channels")
                })

            RegularPreference(
                title = "RPCSX Download Channel",
                leadingIcon = null,
                subtitle = { PreferenceSubtitle(text = prefs.getString("rpcsx_channel", ReleaseRpcsxChannel)!!) },
                onClick = {
                    navigateTo("rpcsx_channels")
                })

            RegularPreference(
                title = "GPU Driver Download Channel",
                leadingIcon = null,
                subtitle = { PreferenceSubtitle(text = prefs.getString("gpu_driver_channel", DefaultGpuDriverChannel)!!) },
                onClick = {
                    navigateTo("gpu_driver_channels")
                })

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = directCoreUrl,
                onValueChange = { directCoreUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Direct librpcsx URL") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (directCoreUrl.isBlank() || isUpdatingCore) {
                        return@Button
                    }

                    isUpdatingCore = true
                    scope.launch {
                        val target = File(context.filesDir.canonicalPath, "librpcsx-custom.tmp.so").apply {
                            if (exists()) {
                                delete()
                            }
                        }

                        when (val result = GitHub.downloadAsset(directCoreUrl, target, { _, _ -> })) {
                            is GitHub.DownloadStatus.Success -> {
                                RpcsxUpdater.installUpdate(context, target)
                            }
                            is GitHub.DownloadStatus.Error -> {
                                snackbarHostState.showSnackbar(result.message ?: "Core download failed")
                            }
                        }
                        isUpdatingCore = false
                    }
                }
            ) {
                Text(if (isUpdatingCore) "Updating..." else "Update Core")
            }
        }
    }
}
