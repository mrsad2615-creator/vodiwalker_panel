package dev.mrsad.core.eye

import dev.mrsad.core.random.SplitMix64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure domain engine for MRSAD EYE — Visual Attention & Ocular Skill Training.
 *
 * Explicit scope contract:
 * Every exercise in this module is designed strictly as cognitive-visual
 * attention, reading-gaze agility, and peripheral awareness training. It is
 * NOT vision therapy, medical diagnosis, or a substitute for clinical eye care.
 */
object EyeTrainingEngine {

    /** Mandatory non-medical disclaimer displayed in the Eye Training module. */
    const val DISCLAIMER_FA: String =
        "این تمرین‌ها صرفاً برای تقویت مهارت‌های توجه دیداری، چابکی نگاه در مطالعه و گسترش میدان توجه طراحی شده‌اند و جایگزین معاینه یا مراقبت پزشکی بینایی نیستند."

    enum class DrillType(
        val id: String,
        val titleFa: String,
        val subtitleFa: String,
        val guideFa: String,
    ) {
        SACCADE(
            id = "saccade",
            titleFa = "پرش‌های سریع نگاه (Saccade)",
            subtitleFa = "افزایش سرعت انتقال چشم بین نقاط سطر",
            guideFa = "سر خود را ثابت نگه دارید و تنها با حرکت چشم، نقطهٔ فعال را به‌محض جابه‌جایی لمس یا دنبال کنید.",
        ),
        SMOOTH_PURSUIT(
            id = "pursuit",
            titleFa = "تعقیب پیوسته (Smooth Pursuit)",
            subtitleFa = "پایداری تمرکز روی هدف متحرک در مسیر منحنی",
            guideFa = "کانون نگاه را نرم و پیوسته روی هستهٔ متحرک نگه دارید و هنگام تغییر جهت، روی هدف ضربه بزنید.",
        ),
        PERIPHERAL_SPAN(
            id = "peripheral",
            titleFa = "گسترهٔ دید محیطی (Schulte Span)",
            subtitleFa = "تشخیص اعداد و نشانه‌ها بدون از دست دادن مرکز صفحه",
            guideFa = "نگاه خود را روی نقطهٔ مرکزی تثبیت کنید و با استفاده از دید پیرامونی، اعداد را به ترتیب از ۱ پیدا کنید.",
        ),
        GAZE_STABILITY(
            id = "stability",
            titleFa = "ثبات کانون و فیلتر حواس‌پرتی",
            subtitleFa = "حفظ توجه کانونی در برابر محرک‌های پیرامونی",
            guideFa = "فقط زمانی که نشانگر مرکزی تغییر شکل می‌دهد واکنش نشان دهید و محرک‌های اطراف را نادیده بگیرید.",
        );

        companion object {
            fun fromId(id: String): DrillType = entries.firstOrNull { it.id == id } ?: SACCADE
        }
    }

    /** Normalized 2D target point (`x`, `y` in `0.08f..0.92f`). */
    data class GazePoint(val x: Float, val y: Float, val stepIndex: Int)

    /** Schulte peripheral grid model. */
    data class SchulteBoard(
        val side: Int,
        val numbers: List<Int>,
    ) {
        val totalCells: Int get() = side * side
    }

    /** Summary of a completed visual-attention drill session. */
    data class EyeSessionReport(
        val drill: DrillType,
        val speedLevel: Int,
        val difficultyLevel: Int,
        val durationSeconds: Int,
        val hits: Int,
        val totalTargets: Int,
        val accuracyPercent: Int,
        val agilityScore: Int,
    )

    /**
     * Interval between saccade jumps in milliseconds for [speedLevel] (`1..5`)
     * and [difficultyLevel] (`1..5`).
     */
    fun targetIntervalMillis(speedLevel: Int, difficultyLevel: Int): Long {
        val s = speedLevel.coerceIn(1, 5)
        val d = difficultyLevel.coerceIn(1, 5)
        return (1350L - (s - 1) * 180L - (d - 1) * 70L).coerceIn(380L, 1400L)
    }

    /**
     * Generates deterministic saccade jump points that alternate left/right
     * reading spans and diagonal jumps according to [difficultyLevel].
     */
    fun generateSaccadeSequence(seed: Long, difficultyLevel: Int, count: Int = 24): List<GazePoint> {
        val rng = SplitMix64(seed)
        val d = difficultyLevel.coerceIn(1, 5)
        val margin = (0.20f - (d - 1) * 0.025f).coerceIn(0.10f, 0.22f)
        return List(count.coerceAtLeast(2)) { idx ->
            val leftSide = idx % 2 == 0
            val baseX = if (leftSide) margin else (1f - margin)
            val jitterX = (rng.nextInt(-35, 36) / 1000f)
            val rowBands = 3 + (d / 2)
            val band = idx % rowBands
            val baseY = 0.18f + (band.toFloat() / (rowBands - 1).coerceAtLeast(1)) * 0.64f
            GazePoint(
                x = (baseX + jitterX).coerceIn(0.08f, 0.92f),
                y = baseY.coerceIn(0.12f, 0.88f),
                stepIndex = idx,
            )
        }
    }

    /**
     * Smooth Lissajous figure-eight pursuit trajectory at normalized phase [tSeconds].
     */
    fun pursuitPosition(tSeconds: Float, speedLevel: Int, difficultyLevel: Int): GazePoint {
        val s = speedLevel.coerceIn(1, 5)
        val d = difficultyLevel.coerceIn(1, 5)
        val omega = (0.65f + s * 0.24f) * (PI.toFloat() / 2f)
        val freqRatio = if (d >= 4) 1.5f else 2.0f
        val ampX = (0.30f + d * 0.025f).coerceAtMost(0.42f)
        val ampY = (0.22f + d * 0.02f).coerceAtMost(0.34f)
        val x = 0.5f + ampX * sin(omega * tSeconds)
        val y = 0.5f + ampY * sin(omega * freqRatio * tSeconds)
        return GazePoint(
            x = x.coerceIn(0.08f, 0.92f),
            y = y.coerceIn(0.10f, 0.90f),
            stepIndex = (tSeconds * 10f).toInt(),
        )
    }

    /**
     * Builds a deterministic Schulte table for peripheral span training.
     * Difficulty 1..2 -> 3x3, Difficulty 3..4 -> 4x4, Difficulty 5 -> 5x5.
     */
    fun generateSchulteBoard(seed: Long, difficultyLevel: Int): SchulteBoard {
        val d = difficultyLevel.coerceIn(1, 5)
        val side = when {
            d <= 2 -> 3
            d <= 4 -> 4
            else -> 5
        }
        val rng = SplitMix64(seed)
        val numbers = rng.shuffle((1..(side * side)).toList())
        return SchulteBoard(side = side, numbers = numbers)
    }

    /**
     * Scores a completed session honestly from real user hits and elapsed time.
     */
    fun evaluateSession(
        drill: DrillType,
        speedLevel: Int,
        difficultyLevel: Int,
        durationSeconds: Int,
        hits: Int,
        totalTargets: Int,
    ): EyeSessionReport {
        val safeTotal = totalTargets.coerceAtLeast(1)
        val safeHits = hits.coerceIn(0, safeTotal)
        val accuracy = ((safeHits.toDouble() / safeTotal.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        val speedFactor = 0.75 + (speedLevel.coerceIn(1, 5) * 0.05) + (difficultyLevel.coerceIn(1, 5) * 0.04)
        val agility = (accuracy * speedFactor).roundToInt().coerceIn(0, 100)
        return EyeSessionReport(
            drill = drill,
            speedLevel = speedLevel.coerceIn(1, 5),
            difficultyLevel = difficultyLevel.coerceIn(1, 5),
            durationSeconds = durationSeconds.coerceAtLeast(1),
            hits = safeHits,
            totalTargets = safeTotal,
            accuracyPercent = accuracy,
            agilityScore = agility,
        )
    }
}
