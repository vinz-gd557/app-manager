package com.vinz.appmanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Menu : Screen()
    object Freeze : Screen()
    object ForceClose : Screen()
    object Uninstall : Screen()
    object InstallUrl : Screen()
    object Terminal : Screen()
    object Adb : Screen()
    object Fastboot : Screen()
}

data class MenuItem(
    val icon: String,
    val title: String,
    val subtitle: String,
    val screen: Screen
)

@Composable
fun MainMenuScreen(onNavigate: (Screen) -> Unit) {
    val items = remember {
        listOf(
            MenuItem("❄", "Freeze App", "Nonaktifkan sementara, data & login tetap aman", Screen.Freeze),
            MenuItem("⏹", "Force Close", "Kill app langsung tanpa nunggu", Screen.ForceClose),
            MenuItem("🗑", "Uninstall App", "Hapus app dari device", Screen.Uninstall),
            MenuItem("⬇", "Install via URL", "Download & install APK langsung dari link", Screen.InstallUrl),
            MenuItem("⌨", "Terminal", "Jalanin command shell manual", Screen.Terminal),
            MenuItem("🔌", "ADB", "Deteksi & sambungin device dalam mode ADB", Screen.Adb),
            MenuItem("⚡", "Fastboot", "Flash, format, unlock/lock bootloader", Screen.Fastboot)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text("App Manager", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pilih aksi yang mau dijalanin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items) { index, item ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 60L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                ) {
                    MenuCard(item) { onNavigate(item.screen) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCard(item: MenuItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionScreen(
    viewModel: AppListViewModel,
    title: String,
    actionIcon: String,
    onBack: () -> Unit,
    onAction: (AppInfo) -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) viewModel.loadApps()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 18.sp) } }
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.icon, Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            if (app.isFrozen) {
                                Text(
                                    "Frozen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { onAction(app) }) {
                            Text(actionIcon, fontSize = 18.sp)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun AppIcon(drawable: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Box(modifier)
        return
    }
    val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallUrlScreen(onBack: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Install via URL") },
            navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 18.sp) } }
        )
        Column(Modifier.padding(20.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link APK (https://...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (url.isNotBlank() && !working) {
                        working = true
                        status = "Menyiapkan..."
                        scope.launch {
                            ShizukuHelper.installFromUrl(url) { status = it }
                            working = false
                        }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (working) "Memproses..." else "Install")
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(status, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    var command by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Terminal") },
            navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 18.sp) } }
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(history) { entry ->
                Column {
                    Text(
                        "$ ${entry.first}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    if (entry.second.isNotBlank()) {
                        Text(
                            entry.second,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("pm list packages") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val cmdToRun = command.trim()
                    if (cmdToRun.isNotEmpty() && !running) {
                        command = ""
                        running = true
                        scope.launch {
                            val output = withContext(Dispatchers.IO) { ShizukuHelper.runRaw(cmdToRun) }
                            history.add(cmdToRun to output)
                            running = false
                            if (history.isNotEmpty()) {
                                listState.animateScrollToItem(history.size - 1)
                            }
                        }
                    }
                }
            ) {
                Text("▶", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
