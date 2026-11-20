package com.mrsadhr.android

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reporting for a sideloaded app: if anything goes wrong, the user sees the reason on screen
 * instead of an app that closes itself, and the trace is kept for the next launch so it can be read,
 * copied or screenshotted.
 *
 * This is deliberate: without a store console there is no other channel back from a device.
 */
object CrashGuard {

    private const val FILE_NAME = "last_crash.txt"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Installs a handler that records the throwable and then lets the platform kill the process. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(app, error, thread.name)
            previous?.uncaughtException(thread, error)
        }
    }

    fun write(context: Context, error: Throwable, threadName: String = Thread.currentThread().name) {
        try {
            val stack = StringWriter()
            error.printStackTrace(PrintWriter(stack))
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file(context).writeText(
                "MrsadHR crash\n$stamp\nthread: $threadName\ndevice: ${android.os.Build.MANUFACTURER} " +
                    "${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} " +
                    "(API ${android.os.Build.VERSION.SDK_INT})\n\n$stack",
            )
        } catch (_: Throwable) {
            // Nothing sensible to do if even the crash file cannot be written.
        }
    }

    fun readLast(context: Context): String? = try {
        file(context).takeIf { it.isFile }?.readText()
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
    }

    /** Shows the recorded trace from the previous run, once. */
    fun showLastIfAny(context: Context) {
        val trace = readLast(context) ?: return
        clear(context)
        val text = TextView(context).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(12, 13, 17))
            setPadding(32, 32, 32, 32)
            text = trace
        }
        val scroll = ScrollView(context).apply { addView(text) }
        AlertDialog.Builder(context)
            .setTitle("Last run stopped with an error")
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("MrsadHR crash", trace))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** Full-screen, selectable error report used when startup itself fails. */
    fun showFatal(context: Context, error: Throwable) {
        write(context, error)
        val trace = readLast(context) ?: error.toString()
        val text = TextView(context).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(12, 13, 17))
            setPadding(36, 96, 36, 36)
            text = "MrsadHR could not start on this device.\n\nCopy this text and send it back:\n\n" + trace
        }
        val scroll = ScrollView(context).apply { addView(text) }
        val activity = context as? android.app.Activity
        activity?.setContentView(scroll) ?: Unit
    }

    fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics,
    )
}
