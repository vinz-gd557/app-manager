package com.vinz.appmanager

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
        Shizuku.addRequestPermissionResultListener(permissionListener)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppManagerScreen(viewModel)
                }
            }
        }

        if (ShizukuHelper.isReady()) {
            viewModel.loadApps()
        } else {
            ShizukuHelper.requestPermission()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: AppListViewModel) {
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text("App Manager") })

            if (apps.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nunggu izin Shizuku / belum ada app...")
                }
            }

            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onForceStop = { ShizukuHelper.forceStop(app.packageName) },
                        onFreeze = {
                            ShizukuHelper.freeze(app.packageName)
                            viewModel.refreshAfterAction(app.packageName, true)
                        },
                        onUnfreeze = {
                            ShizukuHelper.unfreeze(app.packageName)
                            viewModel.refreshAfterAction(app.packageName, false)
                        },
                        onFreezeTemporary = { delayMs ->
                            ShizukuHelper.freezeTemporary(app.packageName, delayMs)
                            viewModel.refreshAfterAction(app.packageName, true)
                        }
                    )
                    Divider()
                }
            }
        }

        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AppRow(
    app: AppInfo,
    onForceStop: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onFreezeTemporary: (Long) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (app.isFrozen) "Frozen" else "Active",
                style = MaterialTheme.typography.labelSmall,
                color = if (app.isFrozen) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onForceStop) {
            Text("⏹")
        }
        if (app.isFrozen) {
            IconButton(onClick = onUnfreeze) { Text("▶") }
        } else {
            IconButton(onClick = { onFreezeTemporary(60_000L) }) { Text("❄") }
        }
    }
}

@Composable
fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Box(modifier)
        return
    }
    val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}
