package com.github.slavebluetooth.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.github.slavebluetooth.R
import java.io.File
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.StringBuilder
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    private lateinit var context: Context

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun setContext(context: Context) {
        this.context = context
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val errorSb = StringBuffer()
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        e.printStackTrace(printWriter)
        var cause = e.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            cause = cause.cause
        }
        printWriter.close()
        errorSb.append(writer.toString())
        Log.e("AndroidRuntime", errorSb.toString())
        Log.e("wtf", errorSb.toString())

        val result = StringBuffer()
        result.append(collectDeviceInfo(context))
        result.append(errorSb)
        saveResult(result.toString())


        object : Thread() {
            override fun run() {
                Looper.prepare()
                Toast.makeText(context, R.string.crash_hint, Toast.LENGTH_LONG).show();
                Looper.loop()
            }
        }.start()
        try {
            Thread.sleep(3000)
        } catch (e: Exception) {
        }
        exitProcess(0)
    }

    @Suppress("DEPRECATION")
    fun collectDeviceInfo(context: Context): String {
        val sb = StringBuilder()
        try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            if (packageInfo != null) {
                val versionName = if (packageInfo.versionName == null) "null" else packageInfo.versionName
                val versionCode = packageInfo.versionCode.toString() + ""
                sb.append("versionName = $versionName \n")
                sb.append("versionCode = $versionCode \n")
            }
        } catch (e: Exception) {
        }
        val fields: Array<Field> = Build::class.java.declaredFields
        for (field in fields) {
            try {
                field.isAccessible = true
                sb.append("${field.name} = ${field.get(null)} \n")
            } catch (e: Exception) {
            }
        }
        return sb.toString()
    }

    /**
     * 生成在/data/data/com.xxxx.xxxx/files/
     */
    private fun saveResult(result: String) {
        val time = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        try {
            val writer = PrintStream(File(context.filesDir, "CrashLog$time.txt").outputStream(), true)
            writer.use {
                writer.println(result)
            }
        } catch (e: Exception) {
            Log.e("AndroidRuntime", "save app crash log filed")
        }
    }
}