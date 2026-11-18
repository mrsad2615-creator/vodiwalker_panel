package ir.aegis.core.ai

enum class ThreatRiskLevel {
    CLEAN,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class InstalledAppInput(
    val packageName: String,
    val appLabel: String,
    val installerPackage: String?,
    val isSystemApp: Boolean,
    val isDebuggable: Boolean,
    val isTestOnly: Boolean,
    val usesCleartextTraffic: Boolean,
    val signingCertSha256: String,
    val signerSubject: String,
    val requestedPermissions: Set<String>,
    val exportedComponentsCount: Int,
    val targetSdk: Int,
)

data class AppRiskFinding(
    val code: String,
    val titleFa: String,
    val titleEn: String,
    val detailFa: String,
    val detailEn: String,
    val mitreTag: String,
    val owaspTag: String,
    val points: Int,
)

data class AppScanResult(
    val packageName: String,
    val appLabel: String,
    val riskScore: Int,
    val riskLevel: ThreatRiskLevel,
    val isSideloaded: Boolean,
    val isDebugSigned: Boolean,
    val installerLabelFa: String,
    val installerLabelEn: String,
    val signingCertSha256: String,
    val findings: List<AppRiskFinding>,
    val sensitivePermCount: Int,
)

data class FileScanResult(
    val fileName: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val riskLevel: ThreatRiskLevel,
    val reasonFa: String,
    val reasonEn: String,
)

object ThreatSignatureEngine {

    private val officialInstallers = setOf(
        "com.android.vending", // Google Play
        "com.farsitel.bazaar", // Cafe Bazaar
        "ir.mservices.market", // Myket
        "com.sec.android.app.samsungapps", // Galaxy Store
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "org.fdroid.fdroid",
        "com.amazon.venezia",
    )

    private val knownMalwarePackageIndicators = listOf(
        "spyzie", "mspy", "flexispy", "hoverwatch", "kidsguard", "cerberus",
        "ahmyth", "spynote", "droidjack", "androrat", "metasploit", "rat.",
        "keylogger", "smsstealer", "banktrojan", "sana.eblagh", "yarane.app",
        "hidden.cam", "call.recorder.hidden",
    )

    // Well-known AOSP public testkey & Android Debug cert prefixes
    private val suspiciousCertHashes = setOf(
        "e89b158e4bcf988ebd09eb83f5378e87c4bd3e7b", // AOSP testkey SHA-1 prefix / legacy
        "a40da80a59d170caa950cf15c18c454d47a39b26", // AOSP platform testkey
        "61ed377e85d386a8dfee6b864bd85b0bfaa5af81", // AOSP shared testkey
    )

    private val knownBadPayloadHashes = setOf(
        // EICAR standard AV test string SHA-256
        "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
    )

    private val sensitivePerms = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.PACKAGE_USAGE_STATS",
    )

    fun analyzeInstalledApp(app: InstalledAppInput): AppScanResult {
        val findings = mutableListOf<AppRiskFinding>()
        val perms = app.requestedPermissions
        val pkgLower = app.packageName.lowercase()
        val labelLower = app.appLabel.lowercase()

        val installer = app.installerPackage
        val isSideloaded = !app.isSystemApp && (installer == null || installer !in officialInstallers)
        val isDebugSigned = app.signerSubject.contains("Android Debug", ignoreCase = true) ||
            suspiciousCertHashes.any { app.signingCertSha256.lowercase().startsWith(it) }

        // 1. Known Stalkerware / RAT / Phishing Dropper Indicator
        val matchedIoc = knownMalwarePackageIndicators.firstOrNull { ind ->
            pkgLower.contains(ind) || labelLower.contains(ind)
        }
        if (matchedIoc != null) {
            findings += AppRiskFinding(
                code = "IOC_SIGNATURE_MATCH",
                titleFa = "تطابق الگوی بدافزار / جاسوس‌افزار شناخته‌شده",
                titleEn = "Known Spyware / RAT Package Signature Match",
                detailFa = "نام بسته یا عنوان برنامه با الگوی خطرناک '$matchedIoc' مطابقت دارد.",
                detailEn = "Package or label matches malicious indicator '$matchedIoc'.",
                mitreTag = "MITRE T1406",
                owaspTag = "OWASP MASVS-RESILIENCE",
                points = 65,
            )
        }

        // 2. Debug / Test-Key Signed App in Production
        if (!app.isSystemApp && (app.isDebuggable || isDebugSigned || app.isTestOnly)) {
            findings += AppRiskFinding(
                code = "DEBUG_CERT_OR_FLAG",
                titleFa = "امضای تستی / حالت دیباگ فعال",
                titleEn = "Debuggable Binary or Debug/Test Signing Key",
                detailFa = "برنامه غیرسیستمی دارای فلگ Debuggable یا امضای عمومی تست است که امنیت حافظه و اعتبارسنجی را تضعیف می‌کند.",
                detailEn = "Non-system app ships with debuggable flag or public debug/test signing certificate.",
                mitreTag = "MITRE T1407",
                owaspTag = "OWASP MASVS-RESILIENCE",
                points = 32,
            )
        }

        // 3. Banking Trojan / Screen Injection Matrix: Accessibility + Overlay
        val hasAccessibility = perms.any { it.contains("BIND_ACCESSIBILITY_SERVICE") }
        val hasOverlay = "android.permission.SYSTEM_ALERT_WINDOW" in perms
        if (!app.isSystemApp && hasAccessibility && hasOverlay) {
            findings += AppRiskFinding(
                code = "ACCESSIBILITY_OVERLAY_COMBO",
                titleFa = "ترکیب پرخطر تروجان بانکی (Accessibility + Overlay)",
                titleEn = "High-Risk Banking Trojan Pattern (Accessibility + Overlay)",
                detailFa = "ترکیب دسترسی سرویس‌کمکی و نمایش روی صفحه امکان خواندن رمزها و تزریق صفحه جعلی را فراهم می‌کند.",
                detailEn = "Combining Accessibility control with overlay windows enables credential harvesting and tapjacking.",
                mitreTag = "MITRE T1453",
                owaspTag = "OWASP MASVS-PLATFORM",
                points = 44,
            )
        } else if (!app.isSystemApp && hasAccessibility) {
            findings += AppRiskFinding(
                code = "ACCESSIBILITY_REQUESTED",
                titleFa = "درخواست کنترل کامل صفحه (Accessibility)",
                titleEn = "Requests Accessibility Screen Control",
                detailFa = "این برنامه درخواست دسترسی خواندن محتوای تمام صفحات را ثبت کرده است.",
                detailEn = "App requests Accessibility privileges capable of reading on-screen content.",
                mitreTag = "MITRE T1453",
                owaspTag = "OWASP MASVS-PLATFORM",
                points = 22,
            )
        }

        // 4. Silent SMS / OTP Exfiltration
        val hasSms = ("android.permission.READ_SMS" in perms || "android.permission.RECEIVE_SMS" in perms)
        val hasNet = "android.permission.INTERNET" in perms
        if (!app.isSystemApp && hasSms && hasNet) {
            findings += AppRiskFinding(
                code = "SMS_OTP_EXFIL",
                titleFa = "دسترسی همزمان به پیامک‌های OTP و اینترنت",
                titleEn = "SMS/OTP Read Access Combined With Network",
                detailFa = "امکان خواندن پیامک‌های رمز یکبارمصرف بانکی و ارسال آن به بیرون از گوشی وجود دارد.",
                detailEn = "App holds SMS read/receive permissions alongside full network access.",
                mitreTag = "MITRE T1636.004",
                owaspTag = "OWASP MASVS-STORAGE",
                points = 34,
            )
        }

        // 5. Stalkerware Surveillance Combo (Mic/Cam + Background Location)
        val hasAudioOrCam = "android.permission.RECORD_AUDIO" in perms || "android.permission.CAMERA" in perms
        val hasBgLoc = "android.permission.ACCESS_BACKGROUND_LOCATION" in perms
        if (!app.isSystemApp && hasAudioOrCam && hasBgLoc && hasNet) {
            findings += AppRiskFinding(
                code = "STALKERWARE_SURVEILLANCE",
                titleFa = "دسترسی چندگانه ردیابی و شنود (مکان پس‌زمینه + صدا/دوربین)",
                titleEn = "Stalkerware Pattern (Background Location + Mic/Camera)",
                detailFa = "برنامه همزمان دسترسی مکان دائمی پس‌زمینه، ضبط صدا/تصویر و اینترنت دارد.",
                detailEn = "Combines background tracking with audio/camera capture and network egress.",
                mitreTag = "MITRE T1429 / T1430",
                owaspTag = "OWASP MASVS-PRIVACY",
                points = 30,
            )
        }

        // 6. Silent APK Dropper + Sideload
        val canInstallApks = "android.permission.REQUEST_INSTALL_PACKAGES" in perms
        if (isSideloaded && canInstallApks) {
            findings += AppRiskFinding(
                code = "SIDELOAD_DROPPER",
                titleFa = "نصب از منبع نامشخص + قابلیت نصب بدافزار دیگر",
                titleEn = "Sideloaded Dropper Capability (Unknown Source + Install Packages)",
                detailFa = "خارج از فروشگاه‌های معتبر نصب شده و می‌تواند برنامه‌های دیگری را روی گوشی نصب کند.",
                detailEn = "Installed from untrusted source and holds REQUEST_INSTALL_PACKAGES permission.",
                mitreTag = "MITRE T1407",
                owaspTag = "OWASP MASVS-RESILIENCE",
                points = 28,
            )
        } else if (isSideloaded) {
            findings += AppRiskFinding(
                code = "UNOFFICIAL_INSTALLER",
                titleFa = "نصب‌شده خارج از فروشگاه‌های رسمی",
                titleEn = "Installed Outside Verified App Stores",
                detailFa = "منبع نصب این برنامه رسمی نیست (نصب مستقیم APK / تلگرام / مرورگر).",
                detailEn = "App installer is missing or unofficial (sideloaded APK).",
                mitreTag = "MITRE T1476",
                owaspTag = "OWASP MASVS-RESILIENCE",
                points = 14,
            )
        }

        // 7. Device Admin Persistence
        if (!app.isSystemApp && perms.any { it.contains("BIND_DEVICE_ADMIN") }) {
            findings += AppRiskFinding(
                code = "DEVICE_ADMIN_RISK",
                titleFa = "درخواست مجوز سرپرست دستگاه (Device Admin)",
                titleEn = "Requests Device Administrator Persistence",
                detailFa = "دسترسی Device Admin حذف برنامه را دشوار کرده و امکان قفل کردن دستگاه را می‌دهد.",
                detailEn = "Device Admin privileges obstruct removal and allow device lock actions.",
                mitreTag = "MITRE T1401",
                owaspTag = "OWASP MASVS-PLATFORM",
                points = 22,
            )
        }

        // 8. Legacy Target SDK + Cleartext Traffic
        if (!app.isSystemApp && app.usesCleartextTraffic && hasNet) {
            findings += AppRiskFinding(
                code = "CLEARTEXT_HTTP",
                titleFa = "اجازه ارسال ترافیک بدون رمزنگاری (HTTP)",
                titleEn = "Permits Unencrypted Cleartext Network Traffic",
                detailFa = "پیکربندی برنامه ترافیک HTTP بدون TLS را مسدود نمی‌کند.",
                detailEn = "Manifest allows unencrypted cleartext HTTP traffic.",
                mitreTag = "MITRE T1557",
                owaspTag = "OWASP MASVS-NETWORK",
                points = 10,
            )
        }

        val score = findings.sumOf { it.points }.coerceIn(0, 100)
        val level = when {
            score >= 65 -> ThreatRiskLevel.CRITICAL
            score >= 42 -> ThreatRiskLevel.HIGH
            score >= 22 -> ThreatRiskLevel.MEDIUM
            score >= 8 -> ThreatRiskLevel.LOW
            else -> ThreatRiskLevel.CLEAN
        }

        val (instFa, instEn) = when (installer) {
            "com.android.vending" -> "گوگل‌پلی (Play Store)" to "Google Play Store"
            "com.farsitel.bazaar" -> "کافه‌بازار" to "Cafe Bazaar"
            "ir.mservices.market" -> "مایکت" to "Myket"
            "com.sec.android.app.samsungapps" -> "گلکسی استور" to "Galaxy Store"
            null -> if (app.isSystemApp) "سیستم‌عامل اندروید" to "Android System Image" else "نامشخص / نصب دستی APK" to "Unknown / Sideloaded APK"
            else -> installer to installer
        }

        return AppScanResult(
            packageName = app.packageName,
            appLabel = app.appLabel,
            riskScore = score,
            riskLevel = level,
            isSideloaded = isSideloaded,
            isDebugSigned = isDebugSigned,
            installerLabelFa = instFa,
            installerLabelEn = instEn,
            signingCertSha256 = app.signingCertSha256,
            findings = findings,
            sensitivePermCount = perms.count { it in sensitivePerms },
        )
    }

    fun scanLocalPayloadFile(
        fileName: String,
        sizeBytes: Long,
        sha256Hex: String,
    ): FileScanResult {
        val lower = fileName.lowercase()
        val hashLower = sha256Hex.lowercase()

        if (hashLower in knownBadPayloadHashes) {
            return FileScanResult(
                fileName = fileName,
                sizeBytes = sizeBytes,
                sha256Hex = sha256Hex,
                riskLevel = ThreatRiskLevel.CRITICAL,
                reasonFa = "تطابق قطعی امضای SHA-256 با پایگاه داده بدافزار (آلوده / EICAR).",
                reasonEn = "Exact SHA-256 signature match in local threat database.",
            )
        }

        val doubleExt = Regex("""\.(pdf|doc|docx|jpg|jpeg|png|mp4|txt)\.(apk|exe|bat|sh|dex|jar)$""")
        if (doubleExt.containsMatchIn(lower)) {
            return FileScanResult(
                fileName = fileName,
                sizeBytes = sizeBytes,
                sha256Hex = sha256Hex,
                riskLevel = ThreatRiskLevel.CRITICAL,
                reasonFa = "پسوند دوگانه فریبنده! فایل به‌ظاهر سند/عکس ولی در واقع فایل اجرایی یا APK است.",
                reasonEn = "Deceptive double extension masking an executable/APK payload.",
            )
        }

        if (lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".sh") || lower.endsWith(".elf")) {
            val looksScam = listOf("eblagh", "sana", "yarane", "bank", "shaparak", "hamrah", "crack", "hack", "mod").any { lower.contains(it) }
            return if (looksScam) {
                FileScanResult(
                    fileName = fileName,
                    sizeBytes = sizeBytes,
                    sha256Hex = sha256Hex,
                    riskLevel = ThreatRiskLevel.HIGH,
                    reasonFa = "فایل نصبی APK مشکوک با کلیدواژه پرخطر فیشینگ/تروجان در نام فایل.",
                    reasonEn = "Executable/APK file carrying high-risk phishing or trojan naming patterns.",
                )
            } else {
                FileScanResult(
                    fileName = fileName,
                    sizeBytes = sizeBytes,
                    sha256Hex = sha256Hex,
                    riskLevel = ThreatRiskLevel.MEDIUM,
                    reasonFa = "فایل اجرایی/نصبی خارج از فروشگاه (پیش از اجرا از اصالت آن مطمئن شوید).",
                    reasonEn = "Standalone executable/APK package detected in local storage.",
                )
            }
        }

        return FileScanResult(
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256Hex = sha256Hex,
            riskLevel = ThreatRiskLevel.CLEAN,
            reasonFa = "فایل عادی — امضای مخرب یا پسوند اجرایی پنهان مشاهده نشد.",
            reasonEn = "Clean — no suspicious payload signature or hidden executable extension.",
        )
    }
}
