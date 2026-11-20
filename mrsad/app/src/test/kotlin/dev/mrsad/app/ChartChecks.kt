package dev.mrsad.app

import dev.mrsad.app.ui.components.SparkGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry checks for the real-data charts.
 *
 * A chart that quietly misrepresents the data is worse than no chart, so
 * the rules are pinned here: a day without a measurement breaks the line
 * instead of drawing a dip, and a flat series is drawn in the middle
 * instead of hugging the floor.
 */
class ChartChecks {

    @Test
    fun aGapBreaksTheLineInsteadOfBridgingIt() {
        val runs = SparkGeometry.runs(listOf(10.0, 20.0, null, 40.0, 50.0))
        assertEquals("a gap must split the series into two runs", 2, runs.size)
        assertEquals("the first run covers the days before the gap", listOf(0, 1), runs[0].map { it.index })
        assertEquals("the second run covers the days after the gap", listOf(3, 4), runs[1].map { it.index })
    }

    @Test
    fun aMissingDayIsNotDrawnAsZero() {
        val runs = SparkGeometry.runs(listOf(10.0, null, 20.0, 30.0))
        assertEquals("the isolated day cannot make a line", 1, runs.size)
        assertEquals("the line starts after the gap", listOf(2, 3), runs[0].map { it.index })
        // The lowest measured value must still sit above the floor, so a
        // gap can never be mistaken for a session that scored nothing.
        assertTrue("the lowest point must stay above the floor", runs[0].first().height > 0.0f)
    }

    @Test
    fun aFlatSeriesSitsInTheMiddleOfThePlot() {
        val runs = SparkGeometry.runs(listOf(30.0, 30.0, 30.0))
        assertEquals("a flat series is still one line", 1, runs.size)
        val height = runs[0].first().height
        assertTrue("a flat series must not hug the floor (was $height)", height > 0.4f && height < 0.6f)
    }

    @Test
    fun aSingleMeasurementDrawsNothing() {
        assertTrue("one point is not a line", SparkGeometry.runs(listOf(10.0)).isEmpty())
        assertTrue("no points is not a line", SparkGeometry.runs(emptyList()).isEmpty())
        assertTrue(
            "one point among gaps is not a line",
            SparkGeometry.runs(listOf(null, 10.0, null)).isEmpty(),
        )
    }

    @Test
    fun theTallestValueIsTheHighestPoint() {
        val runs = SparkGeometry.runs(listOf(5.0, 50.0, 20.0))
        val points = runs[0]
        val highest = points.maxBy { it.height }
        assertEquals("the maximum must be the top of the plot", 1, highest.index)
        assertTrue("the top must leave a margin", highest.height < 1f)
        assertTrue("the bottom must clear the floor", points.minOf { it.height } > 0f)
    }
}
