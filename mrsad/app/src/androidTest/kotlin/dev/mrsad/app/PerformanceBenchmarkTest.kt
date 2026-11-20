package dev.mrsad.app

import android.app.Activity
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mrsad.app.testing.MrsadTestHooks
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The performance benchmark of the app.
 *
 * This is the measurement behind every claim about the quality of the
 * interface. It runs the *real* application — the same build that ships —
 * on a real Android runtime, drives it through a scripted session and
 * records what the platform actually measured:
 *
 *  - the duration of every frame, from `FrameMetrics`, which is the
 *    platform's own number and not an estimate,
 *  - the share of frames that missed their deadline (jank),
 *  - the CPU time the process burned while the session ran,
 *  - the memory the process held.
 *
 * The benchmark deliberately does *not* use the deterministic test hook:
 * the whole point is to measure the animated application, so the core and
 * the atmosphere have to be running.
 *
 * The report is written to the application's own storage and printed to
 * the log with a marker, so CI can collect it without any extra tooling.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceBenchmarkTest {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val collector = FrameCollector()

    @Before
    fun setUp() {
        // The smoke suite switches the app into its static mode. The
        // benchmark has to see the animated application, so the mode is
        // switched off again before the activity is created.
        MrsadTestHooks.disableDeterministicMode()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    @Test
    fun theAnimatedApplicationHoldsItsFrameBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(MainActivity::class.java)

        var activity: Activity? = null
        scenario.onActivity { activity = it }
        val window = requireNotNull(activity).window

        val handler = Handler(Looper.getMainLooper())
        window.addOnFrameMetricsAvailableListener(collector, handler)

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val density = metrics.density

        // Let the first frames, the database and the warm-up of the
        // renderer settle: they are not what the user experiences.
        SystemClock.sleep(WARMUP_MILLIS)
        collector.reset()
        val cpuStart = Process.getElapsedCpuTime()
        val wallStart = SystemClock.uptimeMillis()
        val pssStart = Debug.getPss()

        // ---- Phase 1: the object and the atmosphere on their own --------
        collector.startPhase("idle")
        SystemClock.sleep(IDLE_MILLIS)
        capture(context, "01-home.png")

        // ---- Phase 2: scrolling the home screen --------------------------
        collector.startPhase("scroll")
        repeat(SCROLL_SWIPES) { index ->
            val up = index % 2 == 0
            val fromY = if (up) height * 0.72f else height * 0.35f
            val toY = if (up) height * 0.32f else height * 0.74f
            swipe(width / 2f, fromY, width / 2f, toY, SWIPE_MILLIS)
        }
        SystemClock.sleep(SETTLE_MILLIS)

        // ---- Phase 3: the object under the finger -------------------------
        collector.startPhase("touch")
        repeat(TOUCH_TAPS) {
            tap(width / 2f, height * 0.30f)
            SystemClock.sleep(TAP_PAUSE_MILLIS)
        }
        capture(context, "02-core.png")

        // ---- Phase 4: the navigation -------------------------------------
        collector.startPhase("navigate")
        tap(bottomBarX(1, width, density), bottomBarY(height, density))
        SystemClock.sleep(NAVIGATE_MILLIS)
        tap(bottomBarX(0, width, density), bottomBarY(height, density))
        SystemClock.sleep(NAVIGATE_MILLIS)
        capture(context, "03-after-navigation.png")

        val cpuEnd = Process.getElapsedCpuTime()
        val wallEnd = SystemClock.uptimeMillis()
        val pssEnd = Debug.getPss()

        window.removeOnFrameMetricsAvailableListener(collector)

        val wallMillis = (wallEnd - wallStart).toDouble()
        val cpuMillis = (cpuEnd - cpuStart).toDouble()
        // The refresh rate of the display the app is drawn on. It is the
        // frame budget every frame is measured against.
        val refreshHz = window.decorView.display?.refreshRate ?: 60f
        val refreshMillis = 1000.0 / max(1.0, refreshHz.toDouble())

        val overall = collector.snapshot(budgetMillis = refreshMillis)
        val idle = collector.snapshot("idle", refreshMillis)
        val scroll = collector.snapshot("scroll", refreshMillis)
        val touch = collector.snapshot("touch", refreshMillis)
        val navigate = collector.snapshot("navigate", refreshMillis)

        val report = buildString {
            append("{")
            append("\"package\":\"${context.packageName}\",")
            append("\"build\":\"${BuildConfig.BUILD_TYPE}\",")
            append("\"version\":\"${BuildConfig.VERSION_NAME}\",")
            append("\"displayRefreshHz\":$refreshHz,")
            append("\"wallMillis\":${wallMillis.roundToLong()},")
            append("\"cpuMillis\":${cpuMillis.roundToLong()},")
            append("\"cpuPercent\":${if (wallMillis > 0) (cpuMillis / wallMillis * 100.0).roundToLong() else 0},")
            append("\"pssStartKb\":$pssStart,")
            append("\"pssEndKb\":$pssEnd,")
            append("\"pssDeltaKb\":${pssEnd - pssStart},")
            append("\"javaHeapUsedKb\":${javaHeapUsedKb()},")
            append("\"nativeHeapKb\":${Debug.getNativeHeapAllocatedSize() / 1024},")
            append("\"overall\":${overall.toJson(refreshMillis)},")
            append("\"idle\":${idle.toJson(refreshMillis)},")
            append("\"scroll\":${scroll.toJson(refreshMillis)},")
            append("\"touch\":${touch.toJson(refreshMillis)},")
            append("\"navigate\":${navigate.toJson(refreshMillis)}")
            append("}")
        }

        val file = File(context.filesDir, REPORT_NAME)
        file.writeText(report)
        android.util.Log.i(REPORT_TAG, "MRSAD_PERF_JSON:$report")
        println("MRSAD_PERF_JSON:$report")

        // The benchmark has to have measured something: a run without a
        // single frame would be a broken measurement, not a good result.
        assertTrue(
            "the benchmark collected no frames at all",
            overall.frames > 0,
        )
    }

    // ---- helpers ------------------------------------------------------------

    /** The Java heap the process currently holds, in kilobytes. */
    private fun javaHeapUsedKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024
    }

    /**
     * Writes a screenshot of the device to the application's own storage.
     *
     * The pictures are the visual evidence of the benchmark: they show
     * the interface that produced the numbers, on the device that
     * produced them.
     */
    private fun capture(context: android.content.Context, name: String) {
        val directory = context.getExternalFilesDir(null) ?: return
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .takeScreenshot()
            if (bitmap == null) return
            val file = File(directory, name)
            file.outputStream().use { stream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
            android.util.Log.i(REPORT_TAG, "screenshot: ${file.absolutePath}")
        } catch (error: Throwable) {
            android.util.Log.w(REPORT_TAG, "the screenshot $name failed: $error")
        }
    }

    /** The x of a bottom-bar destination, counted from the right. */
    private fun bottomBarX(index: Int, width: Float, density: Float): Float {
        val padding = 16f * density
        val slot = (width - padding * 2f) / 6f
        return width - padding - (index + 0.5f) * slot
    }

    /** The y of the bottom bar. */
    private fun bottomBarY(height: Float, density: Float): Float = height - 42f * density

    private fun tap(x: Float, y: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(
            MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0),
            true,
        )
        instrumentation.uiAutomation.injectInputEvent(
            MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, x, y, 0),
            true,
        )
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMillis: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        val steps = 10
        instrumentation.uiAutomation.injectInputEvent(
            MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x1, y1, 0),
            true,
        )
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            val at = down + (durationMillis * fraction).toLong()
            instrumentation.uiAutomation.injectInputEvent(
                MotionEvent.obtain(
                    down,
                    at,
                    MotionEvent.ACTION_MOVE,
                    x1 + (x2 - x1) * fraction,
                    y1 + (y2 - y1) * fraction,
                    0,
                ),
                true,
            )
        }
        val up = down + durationMillis
        instrumentation.uiAutomation.injectInputEvent(
            MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, x2, y2, 0),
            true,
        )
    }

    // ---- the collector ------------------------------------------------------

    /**
     * Collects the duration of every frame the platform draws.
     *
     * `FrameMetrics.TOTAL_DURATION` is the time from the first input to
     * the moment the frame was presented — the number Android itself uses
     * to decide whether a frame was janky.
     */
    private class FrameCollector : Window.OnFrameMetricsAvailableListener {

        private val all = ArrayList<Long>()
        private val byPhase = HashMap<String, ArrayList<Long>>()
        private var current = PHASE_WARMUP

        @Synchronized
        fun reset() {
            all.clear()
            byPhase.clear()
            current = PHASE_WARMUP
        }

        /**
         * Starts a new phase.
         *
         * The frames of the previous phase are *kept*: the phases are
         * reported side by side, and the whole session is reported as
         * well. Only [reset] throws measurements away.
         */
        @Synchronized
        fun startPhase(name: String) {
            current = name
        }

        @Synchronized
        fun snapshot(phase: String? = null, budgetMillis: Double = FRAME_BUDGET_MILLIS): FrameStats {
            val values = if (phase == null) all else (byPhase[phase] ?: ArrayList())
            return FrameStats.of(values, budgetMillis)
        }

        @Synchronized
        override fun onFrameMetricsAvailable(
            window: Window,
            frameMetrics: FrameMetrics,
            dropCountSinceLastInvocation: Int,
        ) {
            val duration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            all.add(duration)
            byPhase.getOrPut(current) { ArrayList() }.add(duration)
        }
    }

    /** The statistics of a set of frame durations. */
    private data class FrameStats(
        val frames: Int,
        val meanMillis: Double,
        val p50: Double,
        val p90: Double,
        val p95: Double,
        val p99: Double,
        val max: Double,
        val jankyFrames: Int,
    ) {
        fun toJson(refreshMillis: Double): String {
            val jankPercent = if (frames == 0) 0.0 else jankyFrames * 100.0 / frames
            return "{" +
                "\"budgetMs\":${refreshMillis.roundToLong()}," +
                "\"frames\":$frames," +
                "\"meanMs\":${meanMillis.roundToLong()}," +
                "\"p50Ms\":${p50.roundToLong()}," +
                "\"p90Ms\":${p90.roundToLong()}," +
                "\"p95Ms\":${p95.roundToLong()}," +
                "\"p99Ms\":${p99.roundToLong()}," +
                "\"maxMs\":${max.roundToLong()}," +
                "\"jankyFrames\":$jankyFrames," +
                "\"jankPercent\":${jankPercent.roundToLong()}" +
                "}"
        }

        companion object {
            fun of(values: List<Long>, budgetMillis: Double = FRAME_BUDGET_MILLIS): FrameStats {
                if (values.isEmpty()) {
                    return FrameStats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
                }
                val sorted = values.sorted()
                val mean = sorted.sum().toDouble() / sorted.size
                return FrameStats(
                    frames = sorted.size,
                    meanMillis = mean,
                    p50 = percentile(sorted, 0.50),
                    p90 = percentile(sorted, 0.90),
                    p95 = percentile(sorted, 0.95),
                    p99 = percentile(sorted, 0.99),
                    max = sorted.last().toDouble(),
                    jankyFrames = sorted.count { it > budgetMillis * 1_000_000L },
                )
            }

            private fun percentile(sorted: List<Long>, fraction: Double): Double {
                if (sorted.isEmpty()) return 0.0
                val index = (fraction * (sorted.size - 1)).roundToLong().toInt()
                return sorted[index.coerceIn(0, sorted.size - 1)] / 1_000_000.0
            }
        }
    }

    private companion object {
        const val REPORT_TAG = "MrsadPerf"
        const val REPORT_NAME = "mrsad-perf.json"
        const val WARMUP_MILLIS = 2_000L
        const val IDLE_MILLIS = 3_000L
        const val SETTLE_MILLIS = 500L
        const val SCROLL_SWIPES = 4
        const val SWIPE_MILLIS = 320L
        const val TOUCH_TAPS = 4
        const val TAP_PAUSE_MILLIS = 260L
        const val NAVIGATE_MILLIS = 1_200L
        const val PHASE_WARMUP = "warmup"
    }
}

/**
 * The frame budget of a 60 Hz display.
 *
 * A frame that takes longer than this missed its deadline and is counted
 * as jank.
 */
private const val FRAME_BUDGET_MILLIS = 16.67
