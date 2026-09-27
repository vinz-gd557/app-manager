package com.vinz.appmanager

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

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

    private fun newShizukuProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    private fun runShell(vararg cmd: String): String {
        return try {
            val process = newShizukuProcess(arrayOf(*cmd))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun forceStop(packageName: String) {
        runShell("am", "force-stop", packageName)
    }

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

    fun freezeTemporary(packageName: String, delayMs: Long) {
        freeze(packageName)
        Handler(Looper.getMainLooper()).postDelayed({
            unfreeze(packageName)
        }, delayMs)
    }
}
