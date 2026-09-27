package com.vinz.appmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: AppListViewModel by viewModels()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            viewModel.loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        if (ShizukuHelper.isReady()) {
            viewModel.loadApps()
        } else {
            ShizukuHelper.requestPermission()
        }

        val lastCrash = CrashHandler.readLastCrash(this)

        setContent {
            AppManagerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
                    var crashText by remember { mutableStateOf(lastCrash) }

                    Crossfade(targetState = screen, animationSpec = tween(280), label = "screen") { target ->
                        when (target) {
                            Screen.Menu -> MainMenuScreen(onNavigate = { screen = it })

                            Screen.Freeze -> AppActionScreen(
                                viewModel = viewModel,
                                title = "Freeze App",
                                actionIcon = "❄",
                                onBack = { screen = Screen.Menu }
                            ) { app ->
                                ShizukuHelper.freeze(app.packageName)
                                viewModel.refreshAfterAction(app.packageName, true)
                            }

                            Screen.ForceClose -> AppActionScreen(
                                viewModel = viewModel,
                                title = "Force Close",
                                actionIcon = "⏹",
                                onBack = { screen = Screen.Menu }
                            ) { app ->
                                ShizukuHelper.forceStop(app.packageName)
                            }

                            Screen.Uninstall -> AppActionScreen(
                                viewModel = viewModel,
                                title = "Uninstall App",
                                actionIcon = "🗑",
                                onBack = { screen = Screen.Menu }
                            ) { app ->
                                ShizukuHelper.uninstall(app.packageName)
                                viewModel.removeApp(app.packageName)
                            }

                            Screen.InstallUrl -> InstallUrlScreen(onBack = { screen = Screen.Menu })

                            Screen.Terminal -> TerminalScreen(onBack = { screen = Screen.Menu })

                            Screen.Adb -> AdbScreen(onBack = { screen = Screen.Menu })

                            Screen.Fastboot -> FastbootScreen(onBack = { screen = Screen.Menu })
                        }
                    }

                    if (crashText != null) {
                        AlertDialog(
                            onDismissRequest = {
                                CrashHandler.clearLastCrash(this@MainActivity)
                                crashText = null
                            },
                            title = { Text("Crash terakhir") },
                            text = {
                                Text(
                                    crashText ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    CrashHandler.clearLastCrash(this@MainActivity)
                                    crashText = null
                                }) { Text("Tutup") }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
