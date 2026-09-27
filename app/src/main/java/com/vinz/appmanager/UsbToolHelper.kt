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
import java.nio.charset.Charset

object UsbToolHelper {

    private const val ACTION_USB_PERMISSION = "com.vinz.appmanager.USB_PERMISSION"
    private const val TIMEOUT = 5000

    fun listDevices(context: Context): List<UsbDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.toList()
    }

    /** Device fastboot biasanya nongol dengan interface class 0xFF (vendor specific) + 2 bulk endpoint. */
    fun isLikelyFastboot(device: UsbDevice): Boolean = findFastbootInterface(device) != null

    suspend fun requestPermissionSuspend(context: Context, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            if (manager.hasPermission(device)) {
                cont.resume(true) {}
                return@suspendCancellableCoroutine
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
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

    /** Kirim satu command text fastboot (protokol resmi AOSP: kirim ASCII, baca sampai OKAY/FAIL). */
    fun sendFastbootCommand(context: Context, device: UsbDevice, command: String): String {
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
            return responses.toString().ifBlank { "Tidak ada respons (timeout)" }
        } finally {
            connection.releaseInterface(intf)
            connection.close()
        }
    }

    // === Command resmi AOSP fastboot ===
    fun getVar(context: Context, device: UsbDevice, variable: String) =
        sendFastbootCommand(context, device, "getvar:$variable")

    fun reboot(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "reboot")

    fun rebootBootloader(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "reboot-bootloader")

    /**
     * Unlock resmi lewat protokol fastboot AOSP. Cuma bakal sukses kalau
     * "OEM unlocking" udah di-ON dari Developer Options HP itu, dan vendor-nya
     * emang ngizinin (banyak merek BBK - Vivo/Oppo/Realme - butuh approval
     * resmi lewat akun mereka dulu). Kalau balikin FAIL, itu proteksi
     * anti-theft vendor yang emang gak dimaksudkan buat dilompatin.
     */
    fun flashingUnlock(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "flashing unlock")

    fun oemUnlock(context: Context, device: UsbDevice) =
        sendFastbootCommand(context, device, "oem unlock")
}
