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
 */
class MainActivity : android.app.Activity() {

    private lateinit var graph: AppGraph
    private lateinit var view: TimerAppView
    private lateinit var stage: Stage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        graph = AppGraph.get(this)
        stage = Stage(graph.controller, Typefaces(AndroidFontLoader(assets)))

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

        view = TimerAppView(this).apply {
            onThemeChanged = { light -> applySystemBarIcons(light) }
            bind(stage)
        }
        graph.bridge.screenOnListener = { enabled -> view.keepScreenOn = enabled }
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

        requestNotificationPermissionIfNeeded()
        applySystemBarIcons(stage.state.settings.theme == com.mrsadhr.themes.ThemeId.Pure)
    }

    override fun onResume() {
        super.onResume()
        // Recompute the viewport, then let the stage catch up with whatever happened off screen.
        stage.setViewport(view.width.toFloat(), view.height.toFloat(), resources.displayMetrics.density)
        stage.update(SystemClock.elapsedRealtime())
        view.requestFrame()
    }

    override fun onPause() {
        super.onPause()
        // Timers live in the engine and the foreground service, so nothing needs to keep running
        // here; we only make sure the last action is on disk.
        graph.flush()
    }

    override fun onDestroy() {
        if (isFinishing) {
            graph.bridge.screenOnListener = null
        }
        super.onDestroy()
    }

    @Deprecated("Handled here because the app has no fragment back stack")
    override fun onBackPressed() {
        val consumed = stage.back(SystemClock.elapsedRealtime())
        if (consumed) {
            view.invalidate()
            view.requestFrame()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
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
            graph.bridge.notificationsAllowed = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            view.requestFrame()
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
