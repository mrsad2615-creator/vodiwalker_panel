package com.mrsadhr.test

import com.mrsadhr.core.format.Numbers
import com.mrsadhr.core.format.TimeFormat
import com.mrsadhr.core.model.Lap
import com.mrsadhr.core.time.ManualTimeSource
import com.mrsadhr.sounds.SoundCatalog
import com.mrsadhr.sounds.WavDecoder
import com.mrsadhr.stopwatch.StopwatchEngine
import com.mrsadhr.timer.CountdownEngine
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Timer maths, monotonic accuracy, pause/resume/reset, zero time and restoration. */
object EngineTests {

    fun register() {
        Suite.test("CountdownEngine", "starts at the configured duration") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(90_000)
            expectEq(90_000L, e.remainingMs(), "remaining")
            expect(!e.isRunning, "must not run before start")
        }

        Suite.test("CountdownEngine", "counts down from the monotonic clock, not from frames") {
            val time = ManualTimeSource(mono = 5_000)
            val e = CountdownEngine(time)
            e.setDuration(60_000)
            e.start()
            time.advance(12_345)
            expectEq(47_655L, e.remainingMs(), "remaining after 12.345s")
            // A long gap (screen off, doze, GC pause) must be exact: no accumulated drift.
            time.advance(30_000)
            expectEq(17_655L, e.remainingMs(), "remaining after 42.345s")
        }

        Suite.test("CountdownEngine", "pause freezes and resume continues exactly") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(120_000)
            e.start()
            time.advance(20_000)
            expect(e.pause(), "pause must report a change")
            val frozen = e.remainingMs()
            expectEq(100_000L, frozen, "frozen remaining")
            time.advance(60_000)
            expectEq(100_000L, e.remainingMs(), "paused time must not run")
            expect(e.start(), "resume must report a change")
            time.advance(5_000)
            expectEq(95_000L, e.remainingMs(), "remaining after resume")
            expect(!e.pause().not(), "second pause must work")
        }

        Suite.test("CountdownEngine", "reset restores the full duration") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(45_000)
            e.start()
            time.advance(30_000)
            e.reset()
            expectEq(45_000L, e.remainingMs(), "remaining after reset")
            expect(!e.isRunning, "reset must stop the engine")
            expect(!e.snapshot().completed, "reset must clear completion")
        }

        Suite.test("CountdownEngine", "expires exactly at zero and reports completion") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(1_000)
            e.start()
            time.advance(999)
            expect(!e.hasExpired(), "must not expire 1ms early")
            time.advance(1)
            expect(e.hasExpired(), "must expire at exactly zero")
            expectEq(0L, e.remainingMs(), "remaining at zero")
            e.finish()
            expect(e.snapshot().completed, "finish marks completion")
            expect(!e.isRunning, "finish stops the engine")
        }

        Suite.test("CountdownEngine", "zero duration timer can never start") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(0)
            expect(!e.start(), "a zero length timer must refuse to start")
            expect(!e.isRunning, "engine stays stopped")
            expectEq(0L, e.remainingMs(), "remaining stays zero")
        }

        Suite.test("CountdownEngine", "start after completion replays the full duration") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(2_000)
            e.start()
            time.advance(2_500)
            e.finish()
            expect(e.start(), "restart must be accepted")
            expectEq(2_000L, e.remainingMs(), "restart from the full duration")
            time.advance(500)
            expectEq(1_500L, e.remainingMs(), "runs again from the top")
        }

        Suite.test("CountdownEngine", "duration is clamped to the supported maximum") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(CountdownEngine.MAX_DURATION_MS * 4)
            expectEq(CountdownEngine.MAX_DURATION_MS, e.durationMs, "clamped duration")
            e.setDuration(-5_000)
            expectEq(0L, e.durationMs, "negative durations clamp to zero")
        }

        Suite.test("CountdownEngine", "restores a running timer on the same boot") {
            val time = ManualTimeSource(mono = 10_000)
            val e = CountdownEngine(time)
            e.setDuration(600_000)
            e.start()
            val saved = e.persist()
            time.advance(90_000)
            val restored = CountdownEngine(time)
            restored.restore(saved)
            expect(restored.isRunning, "still running after restore")
            expectEq(510_000L, restored.remainingMs(), "remaining accounts for the gap")
        }

        Suite.test("CountdownEngine", "restore after reboot falls back to the wall clock") {
            val time = ManualTimeSource(mono = 900_000, wall = 1_700_000_000_000)
            val e = CountdownEngine(time)
            e.setDuration(300_000)
            e.start()
            val saved = e.persist()
            // Simulate a reboot: the monotonic clock restarts near zero, the wall clock keeps going.
            time.mono = 1_500
            time.wall += 60_000
            val restored = CountdownEngine(time)
            restored.restore(saved)
            expect(restored.isRunning, "running after reboot restore")
            expectEq(240_000L, restored.remainingMs(), "wall clock gap is applied")
        }

        Suite.test("CountdownEngine", "restore after the deadline marks completion, never negative time") {
            val time = ManualTimeSource(mono = 1_000)
            val e = CountdownEngine(time)
            e.setDuration(10_000)
            e.start()
            val saved = e.persist()
            time.advance(60_000)
            val restored = CountdownEngine(time)
            restored.restore(saved)
            expect(!restored.isRunning, "expired timer is not running")
            expectEq(0L, restored.remainingMs(), "remaining clamps to zero")
            expect(restored.snapshot().completed, "expired timer completes")
        }

        Suite.test("CountdownEngine", "restore of a paused timer keeps its frozen remaining") {
            val time = ManualTimeSource()
            val e = CountdownEngine(time)
            e.setDuration(50_000)
            e.start()
            time.advance(10_000)
            e.pause()
            val saved = e.persist()
            time.advance(120_000)
            val restored = CountdownEngine(time)
            restored.restore(saved)
            expect(!restored.isRunning, "paused restore stays paused")
            expectEq(40_000L, restored.remainingMs(), "frozen remaining is preserved")
        }

        Suite.test("StopwatchEngine", "counts with milliseconds precision") {
            val time = ManualTimeSource()
            val s = StopwatchEngine(time)
            s.start()
            time.advance(1_234)
            expectEq(1_234L, s.elapsedMs(), "elapsed ms")
            time.advance(7_766)
            expectEq(9_000L, s.elapsedMs(), "elapsed after 9s")
        }

        Suite.test("StopwatchEngine", "pause accumulates and resume continues") {
            val time = ManualTimeSource()
            val s = StopwatchEngine(time)
            s.start()
            time.advance(500)
            s.pause()
            expectEq(500L, s.elapsedMs(), "elapsed after pause")
            time.advance(10_000)
            expectEq(500L, s.elapsedMs(), "paused stopwatch does not move")
            s.start()
            time.advance(250)
            expectEq(750L, s.elapsedMs(), "elapsed after resume")
        }

        Suite.test("StopwatchEngine", "laps record index, total and split") {
            val time = ManualTimeSource()
            val s = StopwatchEngine(time)
            s.start()
            time.advance(1_000)
            s.lap()
            time.advance(1_500)
            s.lap()
            time.advance(500)
            s.lap()
            val laps = s.snapshot().laps
            expectEq(3, laps.size, "lap count")
            expectEq(Lap(1, 1_000, 1_000), laps[0], "first lap")
            expectEq(Lap(2, 2_500, 1_500), laps[1], "second lap split")
            expectEq(Lap(3, 3_000, 500), laps[2], "third lap split")
        }

        Suite.test("StopwatchEngine", "reset clears elapsed time and laps") {
            val time = ManualTimeSource()
            val s = StopwatchEngine(time)
            s.start()
            time.advance(4_000)
            s.lap()
            s.reset()
            expectEq(0L, s.elapsedMs(), "elapsed after reset")
            expectEq(0, s.snapshot().laps.size, "laps after reset")
            expect(!s.snapshot().running, "reset stops the stopwatch")
        }

        Suite.test("StopwatchEngine", "restore keeps counting through the gap while running") {
            val time = ManualTimeSource()
            val s = StopwatchEngine(time)
            s.start()
            time.advance(2_000)
            s.lap()
            val saved = s.persist()
            time.advance(3_000)
            val restored = StopwatchEngine(time)
            restored.restore(saved)
            expectEq(5_000L, restored.elapsedMs(), "elapsed includes the background gap")
            expectEq(1, restored.snapshot().laps.size, "laps survive restore")
        }

        Suite.test("TimeFormat", "splits and formats durations") {
            expectEq(Triple(0, 5, 0), TimeFormat.parts(300_000), "5 minute parts")
            expectEq(Triple(1, 2, 3), TimeFormat.parts(3_723_000), "1:02:03 parts")
            expectEq("05:00", TimeFormat.hms(300_000), "mm:ss")
            expectEq("1:02:03", TimeFormat.hms(3_723_000), "h:mm:ss")
            expectEq("00:00", TimeFormat.hms(0), "zero")
            expectEq("01:02:03", TimeFormat.hmsAlways(3_723_000), "always hh:mm:ss")
            expectEq("00:02:00", TimeFormat.hmsAlways(120_000), "padded always format")
        }

        Suite.test("TimeFormat", "stopwatch format keeps centiseconds") {
            expectEq("00:00.00", TimeFormat.stopwatch(0), "zero stopwatch")
            expectEq("00:01.23", TimeFormat.stopwatch(1_234), "1.23s")
            expectEq("01:00.00", TimeFormat.stopwatch(60_000), "one minute")
            expectEq("1:00:00.00", TimeFormat.stopwatch(3_600_000), "one hour")
        }

        Suite.test("TimeFormat", "preset labels and spoken duration") {
            expectEq("45s", TimeFormat.presetLabel(45_000), "seconds label")
            expectEq("5m", TimeFormat.presetLabel(300_000), "minutes label")
            expectEq("12:30", TimeFormat.presetLabel(750_000), "mm:ss label")
            expectEq("1:05:00", TimeFormat.presetLabel(3_900_000), "h:mm:ss label")
            expect(TimeFormat.spokenDuration(90_000, false).contains("1"), "spoken duration mentions minutes")
        }

        Suite.test("TimeFormat", "localises digits for Persian") {
            expectEq("۱۲:۰۰", Numbers.localize("12:00", true), "Persian digits")
            expectEq("12:00", Numbers.localize("12:00", false), "Latin digits")
            expectEq("۰۵:۴۵", Numbers.localize("05:45", true), "Persian padded digits")
        }

        Suite.test("SoundCatalog", "every selected sound has a shipped asset and a duration") {
            expectEq(12, SoundCatalog.all.size, "catalog size")
            for (sound in SoundCatalog.all) {
                expect(sound.durationMs > 500, "${sound.id} needs a duration")
                expect(sound.gain in 0.1f..1f, "${sound.id} gain out of range")
                val file = File("app/src/main/assets/${sound.asset}")
                expect(file.isFile, "missing asset ${sound.asset}")
                expect(file.length() > 10_000, "${sound.asset} looks empty")
            }
            expectEq(4, SoundCatalog.categories.size, "four categories")
        }

        Suite.test("WavDecoder", "decodes every shipped clip as real 16 bit audio") {
            var decodedCount = 0
            for (sound in SoundCatalog.all) {
                val bytes = File("app/src/main/assets/${sound.asset}").readBytes()
                val pcm = WavDecoder.decode(bytes) ?: throw AssertionFailed("could not decode ${sound.asset}")
                val expectedFrames = (sound.durationMs / 1000.0 * WavDecoder.SAMPLE_RATE).toInt()
                expect(abs(pcm.size - expectedFrames) < WavDecoder.SAMPLE_RATE / 20,
                    "${sound.asset}: ${pcm.size} frames vs ${sound.durationMs}ms")
                val peak = pcm.maxOf { abs(it.toInt()) }
                expect(peak in 20_000..32_767, "${sound.asset} peak $peak should be normalised")
                val rms = kotlin.math.sqrt(pcm.sumOf { (it.toDouble() * it) } / pcm.size)
                expect(rms > 300, "${sound.asset} is silent (rms $rms)")
                decodedCount++
            }
            expectEq(12, decodedCount, "all clips decoded")
        }

        Suite.test("WavDecoder", "downmixes stereo and rejects junk") {
            val stereo = wav(stereo = true, samples = shortArrayOf(1000, 3000, -2000, 2000))
            val pcm = WavDecoder.decode(stereo) ?: throw AssertionFailed("stereo decode failed")
            expectEq(2, pcm.size, "frame count")
            expectEq(2000.toShort(), pcm[0], "first mixed frame")
            expectEq(0.toShort(), pcm[1], "second mixed frame")
            expectEq(null, WavDecoder.decode(ByteArray(64)), "garbage must not decode")
            expectEq(null, WavDecoder.decode(ByteArray(4)), "tiny buffers must not decode")
        }
    }

    /** Builds a tiny PCM WAV in memory for the decoder tests. */
    private fun wav(stereo: Boolean, samples: ShortArray): ByteArray {
        val channels = if (stereo) 2 else 1
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) = out.write(byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        ))
        fun le16(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
        ascii("RIFF"); le32(36 + dataSize); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(channels); le32(44100)
        le32(44100 * channels * 2); le16(channels * 2); le16(16)
        ascii("data"); le32(dataSize)
        for (s in samples) le16(s.toInt())
        return out.toByteArray()
    }
}
