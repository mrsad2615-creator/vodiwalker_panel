package ai.dana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneUtterancesTest {
    private fun action(raw: String) = (PhoneUtterances.interpret(raw) as? PhoneIntent.Propose)?.action

    @Test fun everydayPersianWordingAndMixedDurationsResolveExactly() {
        val timers = mapOf(
            "یه تایمر ده دقیقه‌ای بذار" to 600,
            "میشه لطفاً برام برای بیست و پنج دقیقه تایمر بگذاری؟" to 1_500,
            "دو دقیقه و سی ثانیه شمارش معکوس شروع کن" to 150,
            "تایمر رو برای یک ساعت و نیم بزار" to 5_400,
            "تایمر بذار برای یه ربع" to 900,
            "تایمر برای نیم ساعت" to 1_800,
            "timer ۹۰s" to 90,
            "تایمر دو و نیم دقیقه‌ای تنظیم کن" to 150,
            "اگه میشه یه تایمر ۱٫۵ دقیقه‌ای بذار" to 90,
        )
        for ((text, seconds) in timers) assertEquals(text, LocalAction.Timer(seconds), action(text))
        assertEquals(LocalAction.Battery, action("میشه بهم بگو چند درصد باتری مونده؟"))
        assertEquals(LocalAction.Note("گزارش ۲۵ شهریور"), action("یادداشت گزارش ۲۵ شهریور"))
    }

    @Test fun wifiRequestsDistinguishPanelFromLabelledReminder() {
        assertEquals(LocalAction.WifiChange(false), action("لطفاً وای‌فای رو خاموش کن"))
        assertEquals(LocalAction.WifiChange(true), action("روشن کردن وایفای"))
        assertEquals(LocalAction.WifiReminder(600, false), action("وای‌فای رو ده دقیقه دیگه خاموش کن"))
        assertEquals(LocalAction.WifiReminder(900, true), action("بعد از یک ربع، وای فای رو روشن کن"))
        assertEquals(LocalAction.WifiReminder(150, false), action("خاموش کردن وای فای بعد از ۲ دقیقه و ٣٠ ثانیه"))
        assertEquals(PhoneIntent.NeedDuration(DurationTarget.WIFI_OFF), PhoneUtterances.interpret("تایمر خاموش کردن وای فای"))
        assertEquals(PhoneIntent.NeedDuration(DurationTarget.TIMER), PhoneUtterances.interpret("یه تایمر بذار"))
        assertEquals(LocalAction.WifiReminder(125, false), DurationTarget.WIFI_OFF.action(125))
        assertEquals(LocalAction.WifiReminder(150, false), action("وای‌فای رو بعد دو و نیم دقیقه قطعش کن"))
        assertEquals(LocalAction.WifiChange(false), action("الان وای فای رو خاموش کن"))
        assertEquals(LocalAction.WifiReminder(120, false), action("یه تایمر برای خاموش کردن وای فای برای دو دقیقه بذار"))
        assertEquals(150, PhoneUtterances.duration("برای دو دقیقه و سی ثانیه"))
    }

    @Test fun noPartialExecutionOnUnknownNegativeAndAmbiguousRequests() {
        listOf(
            "وای فای رو برای ده دقیقه خاموش کن", "وای فای رو خاموش نکن",
            "چطور وای فای رو خاموش کنم؟", "ده دقیقه دیگه وای فای رو خاموش کن و پیامک بفرست",
            "تایمر ده دقیقه بذار و فایل‌ها رو حذف کن", "تایمر منفی دو دقیقه", "تایمر ده",
            "یک ربع دیگه", "https://example.test", "وای فای\u202E رو خاموش کن",
        ).forEach { assertNull(it, action(it)) }
        listOf("ده", "منفی دو دقیقه", "۰ ثانیه", "۲۵ ساعت", "5m and call", "دو دقیقه و یک ساعت", "دو ساعت و نیم و نیم", "۱٫۱ ثانیه", "۲٫۵ دقیقه و نیم", "۱۰ ثانیه\u202E")
            .forEach { assertNull(it, PhoneUtterances.duration(it)) }
    }
}
