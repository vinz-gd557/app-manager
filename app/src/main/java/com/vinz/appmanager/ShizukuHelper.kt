package com.vinz.appmanager

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Wrapper buat semua operasi yang butuh Shizuku.
 * Semua command dijalanin lewat shell process punya Shizuku,
 * jadi gak butuh root, cukup permission ADB/Shizuku sekali aja.
 */
object ShizukuHelper {

    private const val REQUEST_CODE = 1000

    fun isReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun runShell(vararg cmd: String): String {
        val process = Shizuku.newProcess(cmd, null, null)
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        return output
    }

    /** Force-stop langsung, kayak nge-swipe dari recent apps + kill proses. */
    fun forceStop(packageName: String) {
        runShell("am", "force-stop", packageName)
    }

    /**
     * Freeze app: app "dinonaktifkan" sementara tapi data & session TETAP ADA.
     * Beda sama `pm clear` yang bakal ngehapus data (bikin harus login ulang).
     */
    fun freeze(packageName: String) {
        runShell("pm", "disable-user", "--user", "0", packageName)
    }

    fun unfreeze(packageName: String) {
        runShell("pm", "enable", packageName)
    }

    fun isFrozen(pm: PackageManager, packageName: String): Boolean {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            !info.enabled
        } catch (e: Exception) {
            false
        }
    }

    /** Freeze sekarang, otomatis unfreeze sendiri setelah delayMs (detik/menit sesuai kebutuhan). */
    fun freezeTemporary(packageName: String, delayMs: Long) {
        freeze(packageName)
        Handler(Looper.getMainLooper()).postDelayed({
            unfreeze(packageName)
        }, delayMs)
    }
}
