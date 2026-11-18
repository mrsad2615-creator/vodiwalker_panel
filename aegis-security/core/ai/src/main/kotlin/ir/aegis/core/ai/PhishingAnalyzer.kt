package ir.aegis.core.ai

enum class PhishingVerdict {
    SAFE,
    SUSPICIOUS,
    MALICIOUS_PHISHING,
}

data class PhishingSignal(
    val titleFa: String,
    val titleEn: String,
    val points: Int,
)

data class PhishingReport(
    val verdict: PhishingVerdict,
    val riskScore: Int,
    val extractedUrls: List<String>,
    val signals: List<PhishingSignal>,
    val recommendationFa: String,
    val recommendationEn: String,
)

object PhishingAnalyzer {

    private val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"{}|\\^\[\]`]+""")
    private val ipv4HostRegex = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")

    private val highRiskTlds = setOf(
        "tk", "ml", "ga", "cf", "gq", "top", "xyz", "icu", "click", "vip", "buzz", "work", "cam", "cfd",
    )
    private val shortenerDomains = setOf(
        "bit.ly", "tinyurl.com", "is.gd", "cutt.ly", "t.co", "ow.ly", "b2n.ir", "yun.ir", "zil.ink", "rebrand.ly",
    )
    private val officialSafeDomains = setOf(
        "adliran.ir", "sana.adliran.ir", "shaparak.ir", "bmi.ir", "bankmellat.ir",
        "bsi.ir", "tejaratbank.ir", "sb24.ir", "bpi.ir", "parsian-bank.ir",
        "post.ir", "digikala.com", "google.com", "github.com",
    )
    private val impersonatedKeywords = listOf(
        "shaparak", "sadad", "mellat", "saderat", "melli", "tejarat", "saman", "pasargad", "parsian",
        "adliran", "sana", "eblagh", "dadgostari", "police", "fata", "yarane", "saham", "edalat",
        "telegram", "whatsapp", "google", "apple", "paypal", "binance", "post-ir",
    )
    private val socialEngineeringTriggers = listOf(
        "ابلاغیه", "ثنا", "شکواییه", "دادگستری", "جلب", "یارانه", "سهام عدالت", "کارت سوخت",
        "بسته پستی", "مسدودی حساب", "رفع مسدودی", "جایزه", "برنده", "کد تایید", "رمز دوم",
        "urgent", "suspended", "verify account", "click immediately", "password reset", "otp",
    )

    fun analyzeTextOrUrl(rawInput: String): PhishingReport {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return PhishingReport(
                verdict = PhishingVerdict.SAFE,
                riskScore = 0,
                extractedUrls = emptyList(),
                signals = emptyList(),
                recommendationFa = "لینک یا متن پیامک مشکوک را برای تحلیل آفلاین وارد کنید.",
                recommendationEn = "Paste a suspicious URL or SMS message to run offline analysis.",
            )
        }

        val normalizedLower = LocalAiEngine.normalize(trimmed)
        val urls = urlRegex.findAll(trimmed).map { it.value.trimEnd('.', ',', ';', ')') }.distinct().toList()
        val signals = mutableListOf<PhishingSignal>()

        for (url in urls) {
            val lowerUrl = url.lowercase()
            val afterProto = lowerUrl.substringAfter("://", lowerUrl)
            val host = afterProto.substringBefore('/').substringBefore(':').substringAfter('@')
            val tld = host.substringAfterLast('.', "")
            val isOfficialExact = officialSafeDomains.any { safe -> host == safe || host.endsWith(".$safe") }

            if (lowerUrl.startsWith("http://")) {
                signals += PhishingSignal(
                    "لینک ناامن بدون رمزنگاری HTTPS (ارسال رمز به‌صورت متن باز)",
                    "Insecure unencrypted HTTP protocol without TLS",
                    22,
                )
            }

            if (lowerUrl.contains("@") && afterProto.indexOf('@') < afterProto.indexOf('/').let { if (it < 0) Int.MAX_VALUE else it }) {
                signals += PhishingSignal(
                    "فریب‌دهی آدرس‌بار با کاراکتر @ در URL",
                    "Deceptive '@' credential spoofing in URL authority",
                    35,
                )
            }

            if (host.startsWith("xn--") || host.contains(".xn--")) {
                signals += PhishingSignal(
                    "دامنه جعلی Homograph (Punycode xn--) برای تقلید حروف مشابه",
                    "Internationalized Punycode (xn--) domain potential homograph spoofing",
                    42,
                )
            }

            // Detect mixed Unicode scripts (e.g., Cyrillic 'а'/'о'/'е' mixed into Latin host)
            val hasLatin = host.any { it in 'a'..'z' }
            val hasNonAsciiHost = host.any { it.code > 127 }
            if (hasLatin && hasNonAsciiHost) {
                signals += PhishingSignal(
                    "کاراکترهای غیرلاتین جعلی داخل نام دامنه (حمله هموگراف)",
                    "Mixed script non-ASCII lookalike characters in domain host",
                    48,
                )
            }

            if (ipv4HostRegex.matches(host)) {
                signals += PhishingSignal(
                    "استفاده از IP مستقیم به‌جای دامنه رسمی",
                    "Direct numeric IP address host instead of legitimate domain",
                    36,
                )
            }

            if (lowerUrl.endsWith(".apk") || lowerUrl.contains(".apk?") || lowerUrl.endsWith(".exe")) {
                signals += PhishingSignal(
                    "لینک دانلود مستقیم فایل نصبی مخرب (.apk / .exe)",
                    "Direct malicious installer payload download (.apk / .exe)",
                    52,
                )
            }

            if (shortenerDomains.any { host == it || host.endsWith(".$it") }) {
                signals += PhishingSignal(
                    "استفاده از کوتاه‌کننده لینک برای پنهان کردن مقصد واقعی",
                    "URL shortener obscuring true destination host",
                    20,
                )
            }

            if (tld in highRiskTlds) {
                signals += PhishingSignal(
                    "پسوند دامنه پرریسک و غیررسمی (.$tld)",
                    "High-abuse / unofficial top-level domain (.$tld)",
                    26,
                )
            }

            if (!isOfficialExact && impersonatedKeywords.any { kw -> host.contains(kw) }) {
                signals += PhishingSignal(
                    "جعل نام بانک یا سامانه رسمی داخل دامنه جعلی ($host)",
                    "Brand/portal impersonation detected inside unofficial host ($host)",
                    45,
                )
            }
        }

        // Check social-engineering urgency triggers in body text
        val matchedTriggers = socialEngineeringTriggers.filter { trig ->
            normalizedLower.contains(LocalAiEngine.normalize(trig))
        }
        if (matchedTriggers.isNotEmpty() && (urls.isNotEmpty() || matchedTriggers.size >= 2)) {
            signals += PhishingSignal(
                "عبارات مهندسی اجتماعی و فریب اضطراری: ${matchedTriggers.take(3).joinToString("، ")}",
                "Social-engineering urgency triggers: ${matchedTriggers.take(3).joinToString(", ")}",
                (matchedTriggers.size * 12).coerceAtMost(34),
            )
        }

        val score = signals.sumOf { it.points }.coerceIn(0, 100)
        val verdict = when {
            score >= 45 -> PhishingVerdict.MALICIOUS_PHISHING
            score >= 20 -> PhishingVerdict.SUSPICIOUS
            else -> PhishingVerdict.SAFE
        }

        val (recFa, recEn) = when (verdict) {
            PhishingVerdict.MALICIOUS_PHISHING ->
                "هشدار جدی! به هیچ عنوان روی این لینک کلیک نکنید، اطلاعات بانکی/کد ملی ندهید و فایل احتمالی آن را نصب نکنید." to
                    "HIGH RISK: Do NOT open this link, enter banking/ID details, or install any downloaded APK."
            PhishingVerdict.SUSPICIOUS ->
                "مورد مشکوک است؛ پیش از اقدام، آدرس رسمی سازمان را مستقیماً در مرورگر تایپ کنید و از لینک پیامکی وارد نشوید." to
                    "Suspicious indicators detected. Navigate directly to the official domain manually instead."
            PhishingVerdict.SAFE ->
                "نشانهٔ آشکار فیشینگ یافت نشد؛ با این حال همیشه نام دقیق دامنه (https) را پیش از ورود اطلاعات بررسی کنید." to
                    "No overt phishing indicators found; always verify the exact HTTPS domain before submitting credentials."
        }

        return PhishingReport(
            verdict = verdict,
            riskScore = score,
            extractedUrls = urls,
            signals = signals,
            recommendationFa = recFa,
            recommendationEn = recEn,
        )
    }
}
