package com.mrsadhr.preview

import com.mrsadhr.app.Action
import com.mrsadhr.app.AppController
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.model.SoundCategory
import com.mrsadhr.core.time.ManualTimeSource
import com.mrsadhr.data.MemoryStore
import com.mrsadhr.platform.NullPlatformBridge
import com.mrsadhr.platform.NoopSoundEngine
import com.mrsadhr.themes.ThemeId
import com.mrsadhr.ui.ScreenId
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.Typefaces
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class AwtFontLoader(private val dir: File) : com.mrsadhr.ui.FontLoader {
    private val cache = HashMap<String, com.mrsadhr.core.render.Typeface>()
    override fun load(fileName: String): com.mrsadhr.core.render.Typeface = cache.getOrPut(fileName) {
        AwtTypeface(Font.createFont(Font.TRUETYPE_FONT, File(dir, fileName)))
    }
}

/**
 * Design preview harness.
 *
 * Drives the real [Stage] — the same code that renders on device — and writes PNGs so the visual
 * language can be reviewed (and regression checked) without an emulator.
 */
object PreviewMain {

    private const val W = 1080
    private const val H = 2280
    private const val DENSITY = 2.75f

    private lateinit var typefaces: Typefaces

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = File(args.getOrElse(0) { "docs/previews" })
        val fontDir = File(args.getOrElse(1) { "app/src/main/assets/fonts" })
        outDir.mkdirs()
        typefaces = Typefaces(AwtFontLoader(fontDir))
        val only = args.getOrElse(2) { "" }

        fun shot(name: String, block: (Stage, ManualTimeSource) -> Unit) {
            if (only.isNotEmpty() && !name.contains(only)) return
            val time = ManualTimeSource(mono = 10_000L)
            val controller = AppController(time, MemoryStore(), NullPlatformBridge, NoopSoundEngine)
            val stage = Stage(controller, typefaces)
            stage.setViewport(W.toFloat(), H.toFloat(), DENSITY)
            stage.safeTop = 74f
            stage.safeBottom = 60f
            block(stage, time)
            val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
            val g: Graphics2D = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.BLACK
            g.fillRect(0, 0, W, H)
            val painter = AwtPainter(g, W.toFloat(), H.toFloat())
            stage.update(time.monoMs())
            stage.draw(painter)
            g.dispose()
            val out = File(outDir, "$name.png")
            ImageIO.write(img, "png", out)
            println("wrote ${out.path}")
        }

        shot("01-timer-obsidian") { stage, time -> settle(stage, time) }

        shot("02-timer-running-obsidian") { stage, time ->
            stage.controller.dispatch(Action.SetDuration(7 * 60_000L + 30_000L))
            stage.controller.dispatch(Action.TimerStart)
            time.advance(102_000L)
            settle(stage, time)
        }

        shot("03-timer-pure") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Pure))
            settle(stage, time)
        }

        shot("04-timer-aurora-running") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Aurora))
            stage.controller.dispatch(Action.SetDuration(25 * 60_000L))
            stage.controller.dispatch(Action.TimerStart)
            time.advance(600_000L)
            settle(stage, time)
        }

        shot("05-stopwatch") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Aurora))
            stage.controller.dispatch(Action.SetMode(Mode.Stopwatch))
            stage.controller.dispatch(Action.StopwatchToggle)
            time.advance(43_000L)
            stage.controller.dispatch(Action.StopwatchLap)
            time.advance(21_500L)
            stage.controller.dispatch(Action.StopwatchLap)
            time.advance(9_300L)
            settle(stage, time)
        }

        shot("06-themes") { stage, time ->
            stage.navigate(ScreenId.Themes, time.monoMs())
            settle(stage, time)
        }

        shot("07-sounds") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Obsidian))
            stage.navigate(ScreenId.Sounds, time.monoMs())
            settle(stage, time)
        }

        shot("08-settings") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Pure))
            stage.navigate(ScreenId.Settings, time.monoMs())
            settle(stage, time)
        }

        shot("09-completion") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Obsidian))
            stage.controller.dispatch(Action.SetDuration(2 * 60_000L))
            stage.controller.dispatch(Action.TimerStart)
            time.advance(125_000L)
            repeat(60) {
                time.advance(16L)
                stage.update(time.monoMs())
            }
        }

        shot("10-timer-persian") { stage, time ->
            stage.controller.dispatch(Action.SetLanguage(Language.Persian))
            stage.controller.dispatch(Action.SetDuration(12 * 60_000L))
            settle(stage, time)
        }

        shot("11-focus-aurora") { stage, time ->
            stage.controller.dispatch(Action.SetTheme(ThemeId.Aurora))
            stage.controller.dispatch(Action.SetDuration(45 * 60_000L))
            stage.controller.dispatch(Action.TimerStart)
            time.advance(120_000L)
            settle(stage, time)
        }
    }

    /** Runs enough frames for every transition to land on its resting value. */
    private fun settle(stage: Stage, time: ManualTimeSource, frames: Int = 140) {
        repeat(frames) {
            time.advance(16L)
            stage.update(time.monoMs())
        }
    }
}
