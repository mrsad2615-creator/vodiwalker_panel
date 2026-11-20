package com.mrsadhr.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsetsController
import android.widget.FrameLayout
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.Typefaces

/**
 * Single activity. The UI is one custom surface, so there is no fragment, no navigation graph and
 * nothing to inflate at runtime: cold start is a layout pass and the first frame.
 *
 * Every entry point the platform can call is wrapped in [guard]. A sideloaded app has no crash
 * console, so a failure has to become text on the screen instead of a process that closes: that is
 * the difference between "it keeps stopping" and a report the user can send in two taps.
 */
class MainActivity : android.app.Activity() {

    private var graph: AppGraph? = null
    private var view: TimerAppView? = null
    private var stage: Stage? = null

    /** Set once the error screen owns the window: nothing may replace it afterwards. */
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything else: record a death from any thread, and decide whether the previous
        // launch ever reached its first frame.
        CrashGuard.install(applicationContext)

        // Before anything risky: if the previous launch died, its report is on screen before the
        // interface is even built. That is what makes an otherwise silent crash loop readable.
        guarded("report") { CrashGuard.showLastIfAny(this) }

        guard("onCreate") { startUi() }
        if (failed) return

        if (SafeMode.enabled) announceSafeMode()
    }

    private fun startUi() {
        val graph = AppGraph.get(this)
        this.graph = graph
        val stage = Stage(graph.controller, Typefaces(AndroidFontLoader(assets, SafeMode.enabled)))
        this.stage = stage

        // Edge to edge: the design paints its own backdrop and respects the insets itself.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }

        val view = TimerAppView(this).apply {
            onThemeChanged = { light -> guarded("theme") { applySystemBarIcons(light) } }
            // The first drawn frame is the proof that this launch is healthy.
            onFirstFrame = { SafeMode.markBooted(applicationContext) }
            bind(stage)
        }
        this.view = view
        graph.bridge.screenOnListener = { enabled -> guarded("screenOn") { view.keepScreenOn = enabled } }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )
        view.setFitsSystemWindows(false)
        view.requestApplyInsets()

        guarded("notifications") { requestNotificationPermissionIfNeeded() }
        guarded("systemBars") {
            applySystemBarIcons(stage.state.settings.theme == com.mrsadhr.themes.ThemeId.Pure)
        }
    }

    // ------------------------------------------------------------------ failure handling

    private fun fail(where: String, error: Throwable) {
        if (failed) return
        failed = true
        try {
            view?.stopLoop()
        } catch (_: Throwable) {
        }
        try {
            CrashGuard.write(applicationContext, error, where)
        } catch (_: Throwable) {
        }
        val report = try {
            CrashGuard.readLast(applicationContext) ?: CrashGuard.report(where, error)
        } catch (_: Throwable) {
            error.toString()
        }
        if (report.isNotEmpty() && SafeMode.notesText.isNotEmpty()) {
            // Keep the degraded-subsystem notes with the trace: they are usually the answer.
            CrashGuard.write(applicationContext, IllegalStateException(SafeMode.notesText), "notes")
        }
        try {
            setContentView(
                CrashGuard.fatalView(
                    this,
                    "MrsadHR stopped while running",
                    report,
                ) { recreate() },
            )
        } catch (_: Throwable) {
            // If even a plain TextView cannot be shown there is nothing left to try.
        }
    }

    /** Runs [block] and converts any failure into the on-screen report. */
    private inline fun guard(where: String, block: () -> Unit) {
        if (failed) return
        try {
            block()
        } catch (error: Throwable) {
            fail(where, error)
        }
    }

    private inline fun guarded(where: String, block: () -> Unit) {
        if (failed) return
        try {
            block()
        } catch (error: Throwable) {
            CrashGuard.write(applicationContext, error, where)
        }
    }

    private fun announceSafeMode() {
        guarded("safeMode") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Safe mode")
                .setMessage(
                    "The previous launch stopped before the first frame, so this one runs with " +
                        "audio, notifications, background running, haptics and the bundled fonts " +
                        "switched off. Everything else works, and the next launch is normal again.",
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        guard("onResume") {
            val stage = stage ?: return@guard
            val view = view ?: return@guard
            // Recompute the viewport, then let the stage catch up with whatever happened off screen.
            stage.setViewport(view.width.toFloat(), view.height.toFloat(), resources.displayMetrics.density)
            stage.update(SystemClock.elapsedRealtime())
            view.requestFrame()
        }
    }

    override fun onPause() {
        super.onPause()
        // Timers live in the engine and the foreground service, so nothing needs to keep running
        // here; we only make sure the last action is on disk.
        guarded("onPause") { graph?.flush() }
    }

    override fun onDestroy() {
        if (isFinishing) {
            guarded("onDestroy") { graph?.bridge?.screenOnListener = null }
        }
        super.onDestroy()
    }

    @Deprecated("Handled here because the app has no fragment back stack")
    override fun onBackPressed() {
        var consumed = false
        guard("onBackPressed") {
            consumed = stage?.back(SystemClock.elapsedRealtime()) == true
        }
        if (consumed) {
            guarded("back-redraw") {
                view?.invalidate()
                view?.requestFrame()
            }
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val stage = stage ?: return
        if (SafeMode.enabled) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && stage.state.settings.notifications) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            guarded("permissionResult") {
                graph?.bridge?.notificationsAllowed = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                view?.requestFrame()
            }
        }
    }

    /** Light themes need dark status bar glyphs, dark themes need light ones. */
    private fun applySystemBarIcons(lightBackground: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            controller.setSystemBarsAppearance(if (lightBackground) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            flags = if (lightBackground) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 41
    }
}
