package com.neurox.common

import com.neurox.common.json.JsonParseException
import com.neurox.common.json.JsonParser
import com.neurox.common.json.JsonValue
import com.neurox.common.json.JsonWriter
import com.neurox.common.json.asIntOrDefault
import com.neurox.common.json.asObj
import com.neurox.common.json.asStringOrDefault
import com.neurox.common.json.jsonObject
import com.neurox.common.time.ManualClock
import com.neurox.common.time.TimingCalibration
import com.neurox.common.time.TimingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun writesAndReadsNestedDocument() {
        val doc = jsonObject {
            "id" string "session-1"
            "rounds" integer 12
            "accuracy" number 0.9375
            "active" bool true
            "nested" value jsonObject {
                "label" string "خواندن"
                "items" integers listOf(1, 2, 3)
            }
        }
        val text = JsonWriter.write(doc)
        val parsed = JsonParser.parse(text)
        assertEquals("session-1", parsed.asObj()["id"]?.asStringOrDefault(""))
        assertEquals(12, parsed.asObj()["rounds"]?.asIntOrDefault(0))
        val nested = parsed.asObj()["nested"].asObj()
        assertEquals("خواندن", nested["label"]?.asStringOrDefault(""))
    }

    @Test
    fun escapesQuotesBackslashesAndControlCharacters() {
        val original = "line1\nline2\t\"quoted\"\\back"
        val text = JsonWriter.write(JsonValue.Str(original))
        val roundTrip = JsonParser.parse(text)
        assertEquals(original, roundTrip.asStringOrDefault(""))
    }

    @Test
    fun persianTextSurvivesRoundTripWithoutEscaping() {
        val persian = "نوروکس: تمرین تمرکز و واکنش"
        val text = JsonWriter.write(JsonValue.Str(persian))
        // Persian letters are not escaped, which keeps files small and readable.
        assertTrue(text.contains("نوروکس"))
        assertEquals(persian, JsonParser.parse(text).asStringOrDefault(""))
    }

    @Test
    fun integralNumbersHaveNoFractionPart() {
        assertEquals("42", JsonWriter.numberToString(42.0))
        assertEquals("0.5", JsonWriter.numberToString(0.5))
        assertEquals("-7", JsonWriter.numberToString(-7.0))
    }

    @Test
    fun rejectsTrailingContent() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{} {}") }
    }

    @Test
    fun rejectsDuplicateKeys() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("""{"a":1,"a":2}""") }
    }

    @Test
    fun rejectsUnquotedKeysAndSingleQuotes() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{a:1}") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{'a':1}") }
    }

    @Test
    fun rejectsControlCharactersInsideStrings() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{\"a\":\"x\u0001y\"}") }
    }

    @Test
    fun rejectsDeepNestingToProtectTheStack() {
        val deep = "[".repeat(64) + "]".repeat(64)
        assertThrows(JsonParseException::class.java) { JsonParser.parse(deep) }
    }

    @Test
    fun rejectsNonFiniteNumbers() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("[NaN]") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse("[1e999]") }
        assertThrows(IllegalArgumentException::class.java) { JsonWriter.numberToString(Double.NaN) }
    }

    @Test
    fun parsesUnicodeEscapes() {
        val parsed = JsonParser.parse("\"\\u0646\\u0648\\u0631\"")
        assertEquals("نور", parsed.asStringOrDefault(""))
    }

    @Test
    fun rejectsLeadingZerosAndBareFragments() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("01") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse(".5") }
    }

    @Test
    fun acceptsEmptyContainers() {
        val parsed = JsonParser.parse("""{"a":{},"b":[],"c":null}""").asObj()
        assertTrue(parsed["a"].asObj().isEmpty())
        assertEquals(JsonValue.Null, parsed["c"])
    }
}

class TimingCalibrationTest {

    @Test
    fun visualModeSubtractsInputAndDisplayLatency() {
        val calibration = TimingCalibration(inputLatencyMillis = 10.0, displayLatencyMillis = 6.0)
        assertEquals(384.0, calibration.correctedMillis(400, TimingMode.VISUAL), 0.001)
    }

    @Test
    fun audioModeSubtractsInputLatencyOnly() {
        val calibration = TimingCalibration(inputLatencyMillis = 10.0, displayLatencyMillis = 6.0)
        assertEquals(240.0, calibration.correctedMillis(250, TimingMode.AUDIO), 0.001)
    }

    @Test
    fun correctionNeverGoesNegative() {
        val calibration = TimingCalibration(inputLatencyMillis = 50.0)
        assertEquals(0.0, calibration.correctedMillis(20, TimingMode.VISUAL), 0.0001)
    }

    @Test
    fun manualClockAdvancesBothSources() {
        val clock = ManualClock()
        val monoStart = clock.nanoTime()
        val wallStart = clock.wallClockMillis()
        clock.advanceMillis(1500)
        assertEquals(1_500_000_000L, clock.nanoTime() - monoStart)
        assertEquals(1500L, clock.wallClockMillis() - wallStart)
    }

    @Test
    fun manualClockCanSimulateWallClockJumpWithoutMovingMonotonicTime() {
        val clock = ManualClock()
        val monoStart = clock.nanoTime()
        clock.advanceNanosOnly(0)
        assertEquals(0L, clock.nanoTime() - monoStart)
        assertFalse(clock.nanoTime() < monoStart)
    }
}
