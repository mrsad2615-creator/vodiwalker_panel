package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerPolicyTest {

    // ---------------------------------------------------------------- timecode

    @Test
    fun timecodesReadLikeATimecode() {
        assertEquals("0:00", PlayerPolicy.timecode(0))
        assertEquals("0:07", PlayerPolicy.timecode(7_400))
        assertEquals("1:00", PlayerPolicy.timecode(60_000))
        assertEquals("12:34", PlayerPolicy.timecode(754_000))
    }

    @Test
    fun anHourLongVideoGetsAnHoursField() {
        assertEquals("1:00:00", PlayerPolicy.timecode(3_600_000))
        assertEquals("2:05:09", PlayerPolicy.timecode(7_509_000))
    }

    @Test
    fun aNegativePositionNeverRendersAsMinusZero() {
        assertEquals("0:00", PlayerPolicy.timecode(-5_000))
    }

    // ------------------------------------------------------------- speed label

    @Test
    fun wholeSpeedsDropTheirDecimal() {
        assertEquals("1x", PlayerPolicy.speedLabel(1.0f))
        assertEquals("2x", PlayerPolicy.speedLabel(2.0f))
    }

    @Test
    fun fractionalSpeedsKeepOneDecimal() {
        assertEquals("1.5x", PlayerPolicy.speedLabel(1.5f))
        assertEquals("0.25x", PlayerPolicy.speedLabel(0.25f))
        assertEquals("0.75x", PlayerPolicy.speedLabel(0.75f))
    }

    @Test
    fun floatNoiseDoesNotLeakIntoTheLabel() {
        // 0.1f + 0.2f != 0.3f in binary floating point.
        assertEquals("0.3x", PlayerPolicy.speedLabel(0.1f + 0.2f))
    }

    @Test
    fun theOfferedSpeedsAreOrderedAndIncludeNormal() {
        assertEquals(PlayerPolicy.SPEEDS.sorted(), PlayerPolicy.SPEEDS)
        assertTrue(1.0f in PlayerPolicy.SPEEDS)
    }

    // ------------------------------------------------------------------ chrome

    @Test
    fun theScrubPositionWinsOverThePlayersPosition() {
        val state = PlayerState(isPlaying = false)
        val scrub = Scrub(positionMs = 90_000, durationMs = 100_000, wasPlayingBefore = true)
        // The player is still reporting 10 s while the finger is at 90 s.
        val chrome = PlayerPolicy.chromeFor(
            state = state,
            positionMs = 10_000,
            durationMs = 100_000,
            scrub = scrub,
            visible = true,
            reachedEnd = false,
        )
        assertEquals(90_000, chrome.displayPositionMs)
        assertEquals(0.9f, chrome.displayFraction)
        assertTrue(chrome.scrubbing)
    }

    @Test
    fun theBarStaysVisibleWhileScrubbingEvenAfterTheTimerFired() {
        val scrub = Scrub(positionMs = 1_000, durationMs = 10_000, wasPlayingBefore = true)
        val chrome = PlayerPolicy.chromeFor(
            state = PlayerState(isPlaying = true),
            positionMs = 1_000,
            durationMs = 10_000,
            scrub = scrub,
            visible = false, // the auto-hide timer already ran
            reachedEnd = false,
        )
        assertTrue(chrome.visible)
    }

    @Test
    fun aZeroDurationDoesNotDivideByZero() {
        val chrome = PlayerPolicy.chromeFor(
            state = PlayerState(),
            positionMs = 0,
            durationMs = 0,
            scrub = null,
            visible = true,
            reachedEnd = false,
        )
        assertEquals(0f, chrome.displayFraction)
        assertEquals(0L, chrome.displayPositionMs)
    }

    @Test
    fun aPositionBeyondTheDurationIsClampedToTheEnd() {
        val chrome = PlayerPolicy.chromeFor(
            state = PlayerState(isPlaying = true),
            positionMs = 500_000,
            durationMs = 100_000,
            scrub = null,
            visible = true,
            reachedEnd = false,
        )
        assertEquals(100_000, chrome.displayPositionMs)
        assertEquals(1f, chrome.displayFraction)
    }

    @Test
    fun replayIsShownAtTheEndAndNotMerelyWhenPaused() {
        val atEnd = PlayerPolicy.chromeFor(
            state = PlayerState(isPlaying = false), positionMs = 100_000, durationMs = 100_000,
            scrub = null, visible = true, reachedEnd = true,
        )
        assertTrue(atEnd.showReplay)

        val merelyPaused = PlayerPolicy.chromeFor(
            state = PlayerState(isPlaying = false), positionMs = 4_000, durationMs = 100_000,
            scrub = null, visible = true, reachedEnd = false,
        )
        assertFalse(merelyPaused.showReplay)
    }

    @Test
    fun theSpinnerShowsWhileBufferingButNotOnError() {
        assertTrue(
            PlayerPolicy.chromeFor(PlayerState(isBuffering = true), 0, 1_000, null, true, false).showSpinner,
        )
        assertFalse(
            PlayerPolicy.chromeFor(
                PlayerState(isBuffering = true, hasError = true), 0, 1_000, null, true, false,
            ).showSpinner,
        )
    }

    // ---------------------------------------------------------------- auto-hide

    @Test
    fun chromeHidesOnlyDuringUninterruptedPlayback() {
        assertTrue(PlayerPolicy.shouldAutoHide(PlayerState(isPlaying = true), false, false, false))
    }

    @Test
    fun chromeNeverHidesWhilePaused() {
        assertFalse(PlayerPolicy.shouldAutoHide(PlayerState(isPlaying = false), false, false, false))
    }

    @Test
    fun chromeNeverHidesWhileScrubbingOrWithAMenuOpen() {
        val playing = PlayerState(isPlaying = true)
        assertFalse(PlayerPolicy.shouldAutoHide(playing, scrubbing = true, chromePinned = false, menuOpen = false))
        assertFalse(PlayerPolicy.shouldAutoHide(playing, scrubbing = false, chromePinned = false, menuOpen = true))
        assertFalse(PlayerPolicy.shouldAutoHide(playing, scrubbing = false, chromePinned = true, menuOpen = false))
    }

    @Test
    fun chromeNeverHidesWhileBufferingOrAfterAnError() {
        assertFalse(PlayerPolicy.shouldAutoHide(PlayerState(isPlaying = true, isBuffering = true), false, false, false))
        assertFalse(PlayerPolicy.shouldAutoHide(PlayerState(isPlaying = true, hasError = true), false, false, false))
    }

    // ------------------------------------------------------------------ scrub

    @Test
    fun releasingAScrubResumesOnlyIfItWasPlaying() {
        assertTrue(PlayerPolicy.resumeAfterScrub(Scrub(1_000, 10_000, wasPlayingBefore = true)))
        assertFalse(PlayerPolicy.resumeAfterScrub(Scrub(1_000, 10_000, wasPlayingBefore = false)))
    }

    @Test
    fun theScrubFractionIsGuarded() {
        assertEquals(0f, Scrub(0, 0, false).fraction)
        assertEquals(0.5f, Scrub(50, 100, false).fraction)
        assertEquals(1f, Scrub(200, 100, false).fraction)
    }

    // ------------------------------------------------------------------- seek

    @Test
    fun aShortDragIsATapNotASeek() {
        assertNull(PlayerPolicy.seekByDrag(1_000, deltaDp = 1f, durationMs = 60_000))
    }

    @Test
    fun aDragSeekIsClampedToTheMedia() {
        assertEquals(60_000L, PlayerPolicy.seekByDrag(50_000, deltaDp = 100f, durationMs = 60_000))
        assertEquals(0L, PlayerPolicy.seekByDrag(5_000, deltaDp = -100f, durationMs = 60_000))
    }

    @Test
    fun aDragSeekCannotBeAppliedWithoutADuration() {
        assertNull(PlayerPolicy.seekByDrag(0, deltaDp = 50f, durationMs = 0))
    }
}
