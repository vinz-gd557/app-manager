package com.vinz.appmanager

import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== Halaman ADB ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(UsbToolHelper.listDevices(context)) }
    var selected by remember { mutableStateOf<UsbDevice?>(null) }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("ADB") },
            navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 18.sp) } },
            actions = {
                IconButton(onClick = { devices = UsbToolHelper.listDevices(context) }) {
                    Text("⟳", fontSize = 18.sp)
                }
            }
        )

        Column(Modifier.padding(20.dp)) {
            Text(
                "Colokin HP lewat kabel OTG (mode USB debugging), terus tekan refresh",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))

            if (devices.isEmpty()) {
                Text("Belum ada device kedetect.")
            } else {
                devices.forEach { dev ->
                    Card(
                        onClick = {
                            connecting = true
                            scope.launch {
                                val granted = UsbToolHelper.requestPermissionSuspend(context, dev)
                                connecting = false
                                if (granted) selected = dev
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "VID: ${dev.vendorId}  PID: ${dev.productId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (connecting) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
            }

            selected?.let { dev ->
                Spacer(Modifier.height(20.dp))
                Divider()
                Spacer(Modifier.height(20.dp))
                Text("Terhubung: ${dev.deviceName}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Izin USB udah didapat. Ngirim command shell (adb shell) butuh " +
                        "implementasi protokol ADB penuh (RSA key exchange + approval " +
                        "\"Allow USB debugging?\" di layar HP itu) yang belum ditambahin di sini. " +
                        "Kalau butuh, kabarin lagi ya, saya lanjutin bagian itu terpisah.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== Halaman Fastboot ====================

private val COMMON_PARTITIONS = listOf(
    "system", "boot", "recovery", "vendor", "vbmeta", "dtbo",
    "userdata", "cache", "product", "super", "misc"
)
private val SLOT_OPTIONS = listOf("---", "_a", "_b")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastbootScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(UsbToolHelper.listDevices(context)) }
    var selectedDevice by remember { mutableStateOf<UsbDevice?>(null) }

    var partition by remember { mutableStateOf(COMMON_PARTITIONS.first()) }
    var slot by remember { mutableStateOf(SLOT_OPTIONS.first()) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }

    var rawChecked by remember { mutableStateOf(false) }
    var disableVerityChecked by remember { mutableStateOf(false) }
    var disableVerificationChecked by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            pickedName = queryFileName(context, uri)
        }
    }

    fun targetPartition() = if (slot == "---") partition else "$partition$slot"

    fun runAction(label: String, block: () -> String) {
        if (selectedDevice == null) {
            log.add(0, "$label\nError: belum ada device yang disambungin")
            return
        }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { block() }
            log.add(0, "$label\n$result")
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Fastboot") },
            navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 18.sp) } },
            actions = {
                IconButton(onClick = {
                    devices = UsbToolHelper.listDevices(context)
                    if (selectedDevice != null && devices.none { it.deviceId == selectedDevice!!.deviceId }) {
                        selectedDevice = null
                    }
                }) { Text("⟳", fontSize = 18.sp) }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Device", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                DeviceDropdown(
                    devices = devices,
                    selected = selectedDevice,
                    onSelect = { dev ->
                        scope.launch {
                            val granted = UsbToolHelper.requestPermissionSuspend(context, dev)
                            if (granted) selectedDevice = dev
                        }
                    }
                )
            }

            item {
                Button(
                    onClick = { runAction("Reboot") { UsbToolHelper.reboot(context, selectedDevice!!) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reboot") }
            }

            item {
                Button(
                    onClick = { runAction("All variables") { UsbToolHelper.getVar(context, selectedDevice!!, "all") } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("All variables list") }
            }

            item {
                Button(
                    onClick = {
                        runAction("Partitions info") {
                            val all = UsbToolHelper.getVar(context, selectedDevice!!, "all")
                            all.lineSequence().filter { it.contains("partition", ignoreCase = true) }
                                .joinToString("\n").ifBlank { "Gak ada info partisi di respons" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Partitions info") }
            }

            item {
                Text("Partition", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                SimpleDropdown(options = COMMON_PARTITIONS, selected = partition, onSelect = { partition = it })
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(110.dp)) {
                        SimpleDropdown(options = SLOT_OPTIONS, selected = slot, onSelect = { slot = it })
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { runAction("Format ${targetPartition()}") { UsbToolHelper.erase(context, selectedDevice!!, targetPartition()) } },
                        modifier = Modifier.weight(1f)
                    ) { Text("Format") }
                }
            }

            item {
                Text("Flasher", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pickedName,
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Belum ada file dipilih") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { filePicker.launch("*/*") }) { Text("Browse...") }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rawChecked, onCheckedChange = { rawChecked = it })
                    Text("raw", fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    Checkbox(checked = disableVerityChecked, onCheckedChange = { disableVerityChecked = it })
                    Text("disable-verity", fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    Checkbox(checked = disableVerificationChecked, onCheckedChange = { disableVerificationChecked = it })
                    Text("disable-verification", fontSize = 13.sp)
                }
            }

            item {
                Button(
                    onClick = {
                        val uri = pickedUri
                        val dev = selectedDevice
                        if (uri == null || dev == null) {
                            log.add(0, "Flash\nError: pilih device & file dulu")
                            return@Button
                        }
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val size = queryFileSize(context, uri)
                                if (size <= 0) return@withContext "Error: gagal baca ukuran file"
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    UsbToolHelper.flashPartition(context, dev, targetPartition(), input, size) {}
                                } ?: "Error: gagal buka file"
                            }
                            log.add(0, "Flash ${targetPartition()}\n$result")

                            if (result.startsWith("OKAY")) {
                                if (disableVerityChecked) {
                                    val r = withContext(Dispatchers.IO) { UsbToolHelper.disableVerity(context, dev) }
                                    log.add(0, "disable-verity\n$r")
                                }
                                if (disableVerificationChecked) {
                                    val r = withContext(Dispatchers.IO) { UsbToolHelper.disableVerification(context, dev) }
                                    log.add(0, "disable-verification\n$r")
                                }
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Flash") }
            }

            item {
                Text("Bootloader unlocker", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { runAction("Flashing unlock") { UsbToolHelper.flashingUnlock(context, selectedDevice!!) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Unlock") }
                    Button(
                        onClick = { runAction("Flashing lock") { UsbToolHelper.flashingLock(context, selectedDevice!!) } },
                        modifier = Modifier.weight(1f)
                    ) { Text("Lock") }
                }
            }

            if (busy) {
                item {
                    CircularProgressIndicator(Modifier.padding(top = 8.dp))
                }
            }

            item {
                Divider(Modifier.padding(vertical = 8.dp))
                Text("Log", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }

            items(log) { entry ->
                Text(
                    entry,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(devices: List<UsbDevice>, selected: UsbDevice?, onSelect: (UsbDevice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected?.deviceName ?: "Pilih device",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(text = { Text("Gak ada device") }, onClick = { expanded = false })
            }
            devices.forEach { dev ->
                DropdownMenuItem(
                    text = { Text("${dev.deviceName} (${dev.vendorId}:${dev.productId})") },
                    onClick = {
                        expanded = false
                        onSelect(dev)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "file"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
    }
    return name
}

private fun queryFileSize(context: android.content.Context, uri: Uri): Long {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (e: Exception) {
        -1L
    }
}
