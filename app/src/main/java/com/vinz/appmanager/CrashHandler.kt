package com.vinz.appmanager

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(appContext.filesDir, FILE_NAME).writeText(sw.toString())
            } catch (e: Exception) {
                // biarin, jangan sampe crash handler sendiri yang crash
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clearLastCrash(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
