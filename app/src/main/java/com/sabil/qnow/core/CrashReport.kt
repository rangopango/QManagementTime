package com.sabil.qnow.core

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Saves the last crash so the next launch can show it (there's no USB debugging on the test
 * phone). Only the stack trace and device model are saved, never user data. The screen uses
 * plain Android views so it still works when the crash is in Compose.
 */
internal object CrashReport {
    private const val FILE_NAME = "last_crash.txt"

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Recorder) return
        Thread.setDefaultUncaughtExceptionHandler(Recorder(context.applicationContext, previous))
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private class Recorder(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching { file(context).writeText(describe(context, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun describe(context: Context, error: Throwable): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        return buildString {
            appendLine("App version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(error.stackTraceToString())
        }
    }

    fun show(activity: Activity, report: String) {
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(TextView(activity).apply {
                text = "QNow crashed last time"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = "Tap Copy report and send it to the developer, then tap Try again."
                setPadding(0, pad / 2, 0, pad / 2)
            })
            addView(Button(activity).apply {
                text = "Copy report"
                setOnClickListener {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Crash report", report))
                    Toast.makeText(activity, "Report copied", Toast.LENGTH_SHORT).show()
                }
            })
            addView(Button(activity).apply {
                text = "Try again"
                setOnClickListener {
                    clear(activity)
                    activity.recreate()
                }
            })
            addView(TextView(activity).apply {
                text = report
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(0, pad, 0, pad)
            })
        }
        activity.setContentView(ScrollView(activity).apply { addView(content) })
    }
}
