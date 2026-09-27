package com.vinz.appmanager

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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

    /** Jalanin command bebas lewat shell, buat fitur Terminal. */
    fun runRaw(command: String): String {
        return try {
            val process = newShizukuProcess(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
        } catch (e: Exception) {
            "Error: ${e.message}"
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

    fun uninstall(packageName: String) {
        runShell("pm", "uninstall", packageName)
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

    /**
     * Install APK dengan stream langsung ke stdin `pm install`,
     * gak perlu simpen file APK ke storage dulu (menghindari masalah
     * permission baca file antara app & proses shell Shizuku).
     */
    private fun installApk(input: InputStream, size: Long) {
        val process = newShizukuProcess(arrayOf("pm", "install", "-r", "-t", "-S", size.toString()))
        input.use { source ->
            process.outputStream.use { sink ->
                source.copyTo(sink)
            }
        }
        process.waitFor()
    }

    suspend fun installFromUrl(urlString: String, onStatus: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                onStatus("Mendownload APK...")
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    onStatus("Gagal download (HTTP ${connection.responseCode})")
                    return@withContext
                }

                onStatus("Menginstall...")
                val size = connection.contentLengthLong
                if (size > 0) {
                    installApk(connection.inputStream, size)
                } else {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    installApk(ByteArrayInputStream(bytes), bytes.size.toLong())
                }
                onStatus("Berhasil diinstall!")
            } catch (e: Exception) {
                onStatus("Error: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }
}
