package com.vinz.appmanager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.nio.charset.Charset

object UsbToolHelper {

    private const val ACTION_USB_PERMISSION = "com.vinz.appmanager.USB_PERMISSION"
    private const val TIMEOUT = 5000
    private const val FLASH_TIMEOUT = 30000

    fun listDevices(context: Context): List<UsbDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.toList()
    }

    fun isLikelyFastboot(device: UsbDevice): Boolean = findFastbootInterface(device) != null

    suspend fun requestPermissionSuspend(context: Context, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            try {
                val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                if (manager.hasPermission(device)) {
                    cont.resume(true) {}
                    return@suspendCancellableCoroutine
                }

                // PENTING: Intent harus eksplisit (setPackage) - Android 14+ (targetSdk 34)
                // melarang PendingIntent MUTABLE dari Intent implisit, bakal crash kalau nggak.
                val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, i: Intent) {
                        if (i.action == ACTION_USB_PERMISSION) {
                            val granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            if (cont.isActive) cont.resume(granted) {}
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context, receiver, IntentFilter(ACTION_USB_PERMISSION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                manager.requestPermission(device, pendingIntent)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false) {}
            }
        }

    private fun findFastbootInterface(device: UsbDevice): Pair<UsbInterface, Pair<UsbEndpoint, UsbEndpoint>>? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == 0xFF) {
                var inEp: UsbEndpoint? = null
                var outEp: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep
                        if (ep.direction == UsbConstants.USB_DIR_OUT) outEp = ep
                    }
                }
                if (inEp != null && outEp != null) return intf to (inEp to outEp)
            }
        }
        return null
    }

    fun sendFastbootCommand(context: Context, device: UsbDevice, command: String): String {
        return try {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val found = findFastbootInterface(device)
                ?: return "Error: interface fastboot gak ketemu (device belum masuk mode fastboot?)"
            val (intf, endpoints) = found
            val (inEp, outEp) = endpoints
            val connection: UsbDeviceConnection = manager.openDevice(device)
                ?: return "Error: gagal buka koneksi ke device"

            connection.claimInterface(intf, true)
            try {
                val cmdBytes = command.toByteArray(Charset.forName("UTF-8"))
                connection.bulkTransfer(outEp, cmdBytes, cmdBytes.size, TIMEOUT)

                val buffer = ByteArray(4096)
                val responses = StringBuilder()
                var guard = 0
                while (guard < 20) {
                    val len = connection.bulkTransfer(inEp, buffer, buffer.size, TIMEOUT)
                    if (len <= 0) break
                    val resp = String(buffer, 0, len, Charset.forName("UTF-8"))
                    responses.append(resp).append("\n")
                    if (resp.startsWith("OKAY") || resp.startsWith("FAIL")) break
                    guard++
                }
                responses.toString().ifBlank { "Tidak ada respons (timeout)" }
            } finally {
                connection.releaseInterface(intf)
                connection.close()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun parseMaxDownloadSize(resp: String): Long? {
        val cleaned = resp.removePrefix("OKAY").trim().lowercase()
        return try {
            if (cleaned.startsWith("0x")) cleaned.removePrefix("0x").toLong(16)
            else cleaned.toLongOrNull() ?: cleaned.toLong(16)
        } catch (e: Exception) {
            null
        }
    }

    fun flashPartition(
        context: Context,
        device: UsbDevice,
        partition: String,
        input: InputStream,
        size: Long,
        onProgress: (String) -> Unit
    ): String {
        return try {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val found = findFastbootInterface(device) ?: return "Error: interface fastboot gak ketemu"
            val (intf, endpoints) = found
            val (inEp, outEp) = endpoints
            val connection = manager.openDevice(device) ?: return "Error: gagal buka koneksi"
            connection.claimInterface(intf, true)

            try {
                val maxCmd = "getvar:max-download-size".toByteArray()
                connection.bulkTransfer(outEp, maxCmd, maxCmd.size, TIMEOUT)
                val maxBuf = ByteArray(256)
                val maxLen = connection.bulkTransfer(inEp, maxBuf, maxBuf.size, TIMEOUT)
                if (maxLen > 0) {
                    val maxResp = String(maxBuf, 0, maxLen)
                    val maxSize = parseMaxDownloadSize(maxResp)
                    if (maxSize != null && size > maxSize) {
                        return "Error: file (${size / 1024 / 1024} MB) lebih gede dari max-download-size device (${maxSize / 1024 / 1024} MB)"
                    }
                }

                onProgress("Menyiapkan transfer...")
                val sizeHex = String.format("%08x", size)
                val downloadCmd = "download:$sizeHex".toByteArray()
                connection.bulkTransfer(outEp, downloadCmd, downloadCmd.size, TIMEOUT)

                val buffer = ByteArray(4096)
                var len = connection.bulkTransfer(inEp, buffer, buffer.size, TIMEOUT)
                if (len <= 0) return "Error: device gak respon ke download command"
                val resp = String(buffer, 0, len)
                if (!resp.startsWith("DATA")) return "Error: device nolak download ($resp)"

                onProgress("Mengirim data (${size / 1024} KB)...")
                val chunk = ByteArray(16384)
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    connection.bulkTransfer(outEp, chunk, read, FLASH_TIMEOUT)
                }

                len = connection.bulkTransfer(inEp, buffer, buffer.size, FLASH_TIMEOUT)
                val finalResp = if (len > 0) String(buffer, 0, len) else ""
                if (!finalResp.startsWith("OKAY")) return "Error saat transfer: $finalResp"

                onProgress("Flashing ke $partition...")
                val flashCmd = "flash:$partition".toByteArray()
                connection.bulkTransfer(outEp, flashCmd, flashCmd.size, TIMEOUT)
                val responses = StringBuilder()
                var guard = 0
                while (guard < 20) {
                    len = connection.bulkTransfer(inEp, buffer, buffer.size, FLASH_TIMEOUT)
                    if (len <= 0) break
                    val r = String(buffer, 0, len)
                    responses.append(r).append("\n")
                    if (r.startsWith("OKAY") || r.startsWith("FAIL")) break
                    guard++
                }
                responses.toString().ifBlank { "Tidak ada respons flash" }
            } finally {
                connection.releaseInterface(intf)
                connection.close()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun getVar(context: Context, device: UsbDevice, variable: String) =
        sendFastbootCommand(context, device, "getvar:$variable")

    fun reboot(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "reboot")

    fun rebootBootloader(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "reboot-bootloader")

    fun erase(context: Context, device: UsbDevice, partition: String) =
        sendFastbootCommand(context, device, "erase:$partition")

    fun flashingUnlock(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "flashing unlock")

    fun flashingLock(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "flashing lock")

    fun disableVerity(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "flashing disable_verity")

    fun disableVerification(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "flashing disable_verification")
}
