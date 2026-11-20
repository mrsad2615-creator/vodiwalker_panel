package com.mrsadhr.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reporting for a sideloaded app.
 *
 * A self-installed build has no store console, so the only channel back from a device is the screen
 * itself. Everything here is built from the platform's own widgets and touches no app code: no
 * custom view, no font asset, no controller. If any part of the interface fails, this still renders.
 *
 * Three paths are covered:
 *  * a failure while starting up gets a full screen report,
 *  * a failure after the first frame is written to `filesDir/last_crash.txt` by the uncaught
 *    exception handler and replayed on the next launch,
 *  * a failure on the UI thread while drawing is caught in `TimerAppView` and painted.
 *
 * In every case the report can be copied or sent straight from the device with the system share
 * sheet, so a screenshot is never the only evidence.
 */
object CrashGuard {

    private const val FILE_NAME = "last_crash.txt"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Records throwables from any thread and then lets the platform kill the process as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            append(app, report(thread.name, error))
            previous?.uncaughtException(thread, error)
        }
    }

    /** The text a user would send us: what failed, where, and on which device. */
    fun report(where: String, error: Throwable): String {
        val stack = StringWriter()
        error.printStackTrace(PrintWriter(stack))
        val stamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        } catch (_: Throwable) {
            "?"
        }
        val device = try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android " +
                "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        } catch (_: Throwable) {
            "unknown device"
        }
        val version = "${AppVersion.NAME} (${AppVersion.CODE})"
        return buildString {
            appendLine("MrsadHR crash report")
            appendLine("version: $version")
            appendLine("time: $stamp")
            appendLine("where: $where")
            appendLine("device: $device")
            appendLine("safe mode: ${SafeMode.enabled}")
            appendLine()
            append(stack.toString())
        }
    }

    fun write(context: Context, error: Throwable, where: String) {
        append(context, report(where, error))
    }

    /** Keeps the newest report but never grows without bound: the last three are enough to see a loop. */
    private fun append(context: Context, text: String) {
        try {
            val target = file(context)
            val previous = if (target.isFile) target.readText() else ""
            val combined = if (previous.isEmpty()) text else "$text\n\n----------\n\n$previous"
            val trimmed = if (combined.length > 24_000) combined.substring(0, 24_000) else combined
            target.writeText(trimmed)
        } catch (_: Throwable) {
            // Nothing sensible to do if even the crash file cannot be written.
        }
    }

    fun readLast(context: Context): String? = try {
        file(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ transport

    fun copy(context: Context, report: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("MrsadHR report", report))
        } catch (_: Throwable) {
        }
    }

    /** System share sheet: WhatsApp, Telegram, Gmail — whatever the phone has. No file, no provider. */
    fun share(context: Context, report: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MrsadHR crash report")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            val chooser = Intent.createChooser(send, "Send the report").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
            copy(context, report)
        }
    }

    // ------------------------------------------------------------------ screens

    private fun px(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun monospace(context: Context, text: String, sizeSp: Float): TextView = TextView(context).apply {
        setTextIsSelectable(true)
        typeface = Typeface.MONOSPACE
        textSize = sizeSp
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(12, 13, 17))
        setText(text)
    }

    private fun button(context: Context, label: String, handler: () -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { handler() }
        }

    /**
     * A complete, self-contained error screen: the reason, plus the three actions that matter
     * (send it, copy it, try again). Used when the interface itself could not be built.
     */
    fun fatalView(context: Context, title: String, report: String, onRetry: (() -> Unit)?): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 13, 17))
            setPadding(px(context, 18f), px(context, 42f), px(context, 18f), px(context, 18f))
        }
        column.addView(
            TextView(context).apply {
                setTextColor(Color.rgb(240, 120, 120))
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setText(title)
                setPadding(0, 0, 0, px(context, 6f))
            },
        )
        column.addView(
            TextView(context).apply {
                setTextColor(Color.rgb(170, 178, 190))
                textSize = 13f
                setText(
                    "Send this text back so the fault can be fixed. " +
                        "Nothing is collected or uploaded automatically.",
                )
                setPadding(0, 0, 0, px(context, 10f))
            },
        )
        column.addView(
            ScrollView(context).apply {
                addView(monospace(context, report, 10.5f).apply { setPadding(px(context, 10f), px(context, 10f), px(context, 10f), px(context, 10f)) })
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            },
        )
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, px(context, 12f), 0, 0)
        }
        if (onRetry != null) actions.addView(button(context, "Try again") { onRetry() })
        actions.addView(button(context, "Copy") { copy(context, report) })
        actions.addView(button(context, "Send") { share(context, report) })
        column.addView(actions)
        return column
    }

    /** Full-screen report for a startup failure. Never throws; if it did there would be nothing left. */
    fun showFatal(activity: Activity, error: Throwable) {
        write(activity.applicationContext, error, "startup")
        val report = readLast(activity.applicationContext) ?: report("startup", error)
        try {
            activity.setContentView(
                fatalView(activity, "MrsadHR could not start on this device", report) {
                    activity.recreate()
                },
            )
        } catch (_: Throwable) {
        }
    }

    /**
     * Report from the previous run, shown as a dialog over the working interface. Cleared once
     * shown so a fixed build does not nag forever.
     */
    fun showLastIfAny(activity: Activity) {
        val report = readLast(activity) ?: return
        clear(activity)
        try {
            val text = monospace(activity, report, 11f).apply {
                setPadding(px(activity, 12f), px(activity, 12f), px(activity, 12f), px(activity, 12f))
            }
            val scroll = ScrollView(activity).apply { addView(text) }
            AlertDialog.Builder(activity)
                .setTitle("The previous run stopped with an error")
                .setView(scroll)
                .setPositiveButton("Send") { _, _ -> share(activity, report) }
                .setNeutralButton("Copy") { _, _ -> copy(activity, report) }
                .setNegativeButton("Close", null)
                .show()
        } catch (_: Throwable) {
        }
    }

    /** Used by the draw loop, which cannot show a dialog: it paints the text instead. */
    fun shortTrace(error: Throwable): String =
        error.toString() + "\n" + error.stackTrace.take(10).joinToString("\n") { "  at $it" }
}
