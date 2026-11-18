package ir.aegis.feature.emergency

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.aegis.core.ai.CyberKnowledgeBase
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.security.KeyStore

private enum class AuditStatus { SECURE, WARNING, DANGER }

private data class OsSecurityCheck(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val detailFa: String,
    val detailEn: String,
    val status: AuditStatus,
    val standardRef: String,
    val settingsAction: String? = null,
)

@Composable
fun EmergencyResponseScreen(
    repository: AegisRepository,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val contacts by repository.observeTrustedContacts().collectAsState(initial = emptyList())
    val checklists by repository.observeChecklists().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Anti-Hack Inspector, 1=Cyber Playbooks, 2=Lockdown & CERT
    var checks by remember { mutableStateOf<List<OsSecurityCheck>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf<Long?>(null) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    suspend fun runRealSystemAudit() {
        scanning = true
        val results = withContext(Dispatchers.IO) { inspectAndroidOsSecurity(context) }
        checks = results
        val now = System.currentTimeMillis()
        lastScanTime = now
        scanning = false
        val warnings = results.count { it.status != AuditStatus.SECURE }
        repository.logActivity(
            "OS_ANTI_HACK_AUDIT",
            "Anti-Hack Inspector",
            "Checks=${results.size}, NonSecure=$warnings",
        )
    }

    LaunchedEffect(Unit) {
        runRealSystemAudit()
    }

    val secureCount = checks.count { it.status == AuditStatus.SECURE }
    val totalChecks = checks.size.coerceAtLeast(1)
    val osScore = ((secureCount.toFloat() / totalChecks) * 100f).toInt().coerceIn(0, 100)

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.EMERGENCY,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = tr("OS Score $osScore%", "امتیاز سیستم $osScore٪"),
            showBackButton = showBackButton,
        )

        GlassTabRow(
            tabs = listOf(
                tr("OS Anti-Hack Audit", "بازرسی ضد هک سیستم"),
                tr("Defense Playbooks", "پلی‌بوک‌های ضد نفوذ"),
                tr("Lockdown & CERT", "قرنطینه و واکنش اضطراری"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BentoMetricTile(
                                value = "$secureCount",
                                label = tr("Passed Checks", "موارد امن"),
                                icon = Icons.Rounded.VerifiedUser,
                                accent = colors.accentEmerald,
                                modifier = Modifier.weight(1f),
                            )
                            BentoMetricTile(
                                value = "${checks.count { it.status != AuditStatus.SECURE }}",
                                label = tr("Need Action", "نیازمند بررسی"),
                                icon = Icons.Rounded.WarningAmber,
                                accent = if (checks.any { it.status == AuditStatus.DANGER }) colors.accentCrimson else colors.accentAmber,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = if (osScore >= 80) colors.accentEmerald else colors.accentAmber) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Hero3DSecurityShield(
                                    readinessScore = osScore,
                                    activeAlertsCount = checks.count { it.status == AuditStatus.DANGER },
                                    modifier = Modifier.size(88.dp),
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = tr("Real-Time Hardware & OS Hardening Audit", "ممیزی زنده سخت‌افزار و هسته اندروید"),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = tr(
                                            "$secureCount of ${checks.size} OWASP MASVS / CIS checks passed offline.",
                                            "$secureCount از ${checks.size} بررسی امنیتی استاندارد با موفقیت تایید شد.",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                    ChipFlowGroup {
                                        GlassButton(
                                            label = if (scanning) tr("Inspecting...", "در حال بازرسی...") else tr("Re-Run Deep Audit", "بازرسی مجدد سیستم"),
                                            onClick = { scope.launch { runRealSystemAudit() } },
                                            accent = colors.accentCyan,
                                            enabled = !scanning,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(checks, key = { it.id }) { chk ->
                        val tone = when (chk.status) {
                            AuditStatus.SECURE -> colors.accentEmerald
                            AuditStatus.WARNING -> colors.accentAmber
                            AuditStatus.DANGER -> colors.accentCrimson
                        }
                        val statusBadge = when (chk.status) {
                            AuditStatus.SECURE -> tr("SECURE", "امن")
                            AuditStatus.WARNING -> tr("REVIEW", "بررسی")
                            AuditStatus.DANGER -> tr("RISK", "خطر")
                        }
                        ObsidianGlassCard(accentBorderColor = tone) {
                            SectionHeader(
                                title = if (lang == AppLanguage.FA) chk.titleFa else chk.titleEn,
                                badge = statusBadge,
                            )
                            Text(
                                text = if (lang == AppLanguage.FA) chk.detailFa else chk.detailEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = chk.standardRef,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                            if (chk.settingsAction != null && chk.status != AuditStatus.SECURE) {
                                Spacer(modifier = Modifier.height(6.dp))
                                GlassButton(
                                    label = tr("Open System Setting to Fix", "باز کردن تنظیمات سیستم برای اصلاح"),
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(chk.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }.onFailure {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            }
                                        }
                                    },
                                    accent = tone,
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SectionHeader(
                            title = tr("Cyber Defense Playbooks", "راهنمای حرفه‌ای مقابله با هک و بدافزار"),
                            badge = "OWASP · MITRE · NIST",
                        )
                    }
                    items(CyberKnowledgeBase.playbooks, key = { it.id }) { pb ->
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                            Text(
                                text = if (lang == AppLanguage.FA) pb.titleFa else pb.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Text(pb.standardRef, style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == AppLanguage.FA) pb.summaryFa else pb.summaryEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val steps = if (lang == AppLanguage.FA) pb.stepsFa else pb.stepsEn
                            steps.forEachIndexed { idx, s ->
                                Text("${idx + 1}. $s", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tr("Indicators: ", "نشانه‌های هشدار: ") +
                                    (if (lang == AppLanguage.FA) pb.indicatorsFa else pb.indicatorsEn).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentAmber,
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCrimson) {
                            SectionHeader(
                                title = tr("Emergency Compromise Containment", "پروتکل قرنطینه فوری هنگام مشکوک شدن به هک"),
                                badge = "NIST SP 800-61r2",
                            )
                            Text(
                                text = tr(
                                    "1. Enable Airplane Mode immediately to block C2 exfiltration.\n2. Open Wireless / Settings below to disable Wi-Fi & Bluetooth.\n3. Run App Malware Scanner in AegisGuard.\n4. Copy offline audit report before rotating credentials on a clean device.",
                                    "۱. فوراً گوشی را روی حالت پرواز (Airplane Mode) بگذارید تا ارتباط مهاجم قطع شود.\n۲. وای‌فای و بلوتوث را از تنظیمات ببندید.\n۳. اسکنر بدافزار برنامه و بازرسی ضد هک را اجرا کنید.\n۴. پیش از پاک‌سازی، گزارش شواهد را ذخیره و رمز حساب‌های خود را با دستگاه امن دیگر تغییر دهید.",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Network & Airplane Settings", "تنظیمات حالت پرواز و شبکه"),
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    },
                                    accent = colors.accentCrimson,
                                )
                                GlassButton(
                                    label = tr("Copy Audit Summary", "کپی خلاصه وضعیت امنیتی"),
                                    onClick = {
                                        val report = buildString {
                                            appendLine("AegisGuard OS Security Audit — Score: $osScore%")
                                            checks.forEach { appendLine("[${it.status}] ${it.titleEn}: ${it.detailEn}") }
                                        }
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cb?.setPrimaryClip(ClipData.newPlainText("AegisGuard Audit", report))
                                        feedbackMsg = trText(lang, "Security audit summary copied locally.", "گزارش ممیزی امنیتی در کلیپ‌بورد کپی شد.")
                                    },
                                    accent = colors.accentCyan,
                                )
                            }
                            feedbackMsg?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald)
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            SectionHeader(title = tr("Cyber Police & Emergency Hotlines", "شماره‌های اضطراری و پلیس فتا"))
                            ChipFlowGroup {
                                listOf(
                                    "096380" to tr("Cyber Police FATA (096380)", "پلیس فتا ۰۹۶۳۸۰"),
                                    "110" to tr("Police Emergency (110)", "پلیس ۱۱۰"),
                                    "112" to tr("Intl Emergency (112)", "اورژانس بین‌المللی ۱۱۲"),
                                ).forEach { (num, lbl) ->
                                    GlassButton(
                                        label = lbl,
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }
                                        },
                                        accent = colors.accentTeal,
                                    )
                                }
                            }
                            if (contacts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(tr("Trusted Security Contacts:", "مخاطبین امنیتی مورد اعتماد:"), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                                contacts.take(4).forEach { c ->
                                    Text("• ${c.name} (${c.relation}) — ${c.phone}", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                                }
                            }
                        }
                    }

                    if (checklists.isNotEmpty()) {
                        items(checklists.take(3), key = { it.checklist.id }) { cl ->
                            ObsidianGlassCard {
                                Text(cl.checklist.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                                Text(
                                    tr("${cl.items.count { it.isChecked }}/${cl.items.size} steps checked", "${cl.items.count { it.isChecked }} از ${cl.items.size} گام انجام شده"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun inspectAndroidOsSecurity(context: Context): List<OsSecurityCheck> {
    val list = mutableListOf<OsSecurityCheck>()
    val cr = context.contentResolver

    // 1. Root & Binary Integrity Check
    val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/su/bin/su", "/data/local/su", "/data/local/bin/su",
        "/data/adb/magisk", "/data/adb/ksu",
    )
    val foundSu = suPaths.filter { runCatching { File(it).exists() }.getOrDefault(false) }
    val testKeys = Build.TAGS?.contains("test-keys", ignoreCase = true) == true
    val rootStatus = when {
        foundSu.isNotEmpty() -> AuditStatus.DANGER
        testKeys -> AuditStatus.WARNING
        else -> AuditStatus.SECURE
    }
    list += OsSecurityCheck(
        id = "chk-root",
        titleFa = "یکپارچگی هسته و عدم وجود Root / Magisk",
        titleEn = "OS Kernel Integrity & Root Detection",
        detailFa = when (rootStatus) {
            AuditStatus.DANGER -> "فایل‌های دسترسی روت (${foundSu.joinToString()}) شناسایی شد؛ سندباکس سیستم‌عامل آسیب‌پذیر است."
            AuditStatus.WARNING -> "رام با امضای test-keys ساخته شده است؛ اصالت سیستم‌عامل بررسی شود."
            AuditStatus.SECURE -> "هیچ نشانهٔ روت، باینری su یا Magisk در مسیرهای سیستمی یافت نشد."
        },
        detailEn = when (rootStatus) {
            AuditStatus.DANGER -> "Root binaries found (${foundSu.joinToString()}). App sandbox isolation is weakened."
            AuditStatus.WARNING -> "System build signed with test-keys instead of release-keys."
            AuditStatus.SECURE -> "No su binaries, Magisk, or KernelSU paths detected."
        },
        status = rootStatus,
        standardRef = "OWASP MASVS-RESILIENCE · MITRE T1404",
    )

    // 2. USB Debugging (ADB) & Developer Mode
    val adbEnabled = runCatching { Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
    val devEnabled = runCatching { Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)
    list += OsSecurityCheck(
        id = "chk-adb",
        titleFa = "اشکال‌زدایی USB (ADB) و گزینه‌های توسعه‌دهنده",
        titleEn = "USB Debugging (ADB) & Developer Options",
        detailFa = when {
            adbEnabled -> "هشدار: USB Debugging (ADB) فعال است! هر سیستم متصل با کابل می‌تواند برنامه نصب یا داده استخراج کند."
            devEnabled -> "Developer Options روشن است اما ADB خاموش می‌باشد."
            else -> "گزینه USB Debugging و حالت توسعه‌دهنده خاموش و امن است."
        },
        detailEn = when {
            adbEnabled -> "USB Debugging (ADB) is enabled! Connected USB hosts can pull data or sideload packages."
            devEnabled -> "Developer Options are active (ADB is disabled)."
            else -> "USB Debugging and Developer Options are both disabled."
        },
        status = if (adbEnabled) AuditStatus.DANGER else if (devEnabled) AuditStatus.WARNING else AuditStatus.SECURE,
        standardRef = "CIS Android Benchmark 1.x · MITRE T1458",
        settingsAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
    )

    // 3. Active Accessibility Services (Trojan Screen-Capture Vector)
    val enabledAcc = runCatching {
        Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    }.getOrDefault("")
    val accServices = enabledAcc.split(':').filter { it.isNotBlank() }
    val nonGoogleAcc = accServices.filterNot {
        it.startsWith("com.google.android.") || it.startsWith("com.android.") || it.startsWith("com.samsung.")
    }
    list += OsSecurityCheck(
        id = "chk-acc",
        titleFa = "سرویس‌های کنترل صفحه (Accessibility Services)",
        titleEn = "Active Accessibility Services Audit",
        detailFa = when {
            nonGoogleAcc.isNotEmpty() -> "${nonGoogleAcc.size} سرویس غیرسیستمی فعال است: ${nonGoogleAcc.joinToString(", ")}"
            accServices.isNotEmpty() -> "${accServices.size} سرویس سیستمی استاندارد فعال است."
            else -> "هیچ سرویس Accessibility فعالی وجود ندارد (ایده‌آل در برابر تروجان بانکی)."
        },
        detailEn = when {
            nonGoogleAcc.isNotEmpty() -> "${nonGoogleAcc.size} third-party Accessibility service(s) active: ${nonGoogleAcc.joinToString(", ")}"
            accServices.isNotEmpty() -> "${accServices.size} built-in accessibility service(s) active."
            else -> "Zero active Accessibility Services — maximum protection against overlay/tapjacking."
        },
        status = if (nonGoogleAcc.isNotEmpty()) AuditStatus.DANGER else AuditStatus.SECURE,
        standardRef = "MITRE T1453 · OWASP MASVS-PLATFORM",
        settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
    )

    // 4. Device Administrators (Ransomware / Lock Persistence)
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val admins = runCatching { dpm?.activeAdmins?.map { it.packageName }.orEmpty() }.getOrDefault(emptyList())
    val suspiciousAdmins = admins.filterNot { it.startsWith("com.google.") || it.startsWith("com.android.") }
    list += OsSecurityCheck(
        id = "chk-admin",
        titleFa = "سرپرست‌های دستگاه (Device Administrators)",
        titleEn = "Active Device Administrators",
        detailFa = when {
            suspiciousAdmins.isNotEmpty() -> "برنامه‌های دارای مجوز مدیریت دستگاه: ${suspiciousAdmins.joinToString()}"
            admins.isNotEmpty() -> "${admins.size} سرپرست سیستمی معتبر ثبت شده است."
            else -> "هیچ برنامه متفرقه‌ای مجوز Device Admin ندارد."
        },
        detailEn = when {
            suspiciousAdmins.isNotEmpty() -> "Non-platform Device Admin(s) found: ${suspiciousAdmins.joinToString()}"
            admins.isNotEmpty() -> "${admins.size} platform Device Admin(s) active."
            else -> "No third-party Device Administrators active."
        },
        status = if (suspiciousAdmins.isNotEmpty()) AuditStatus.WARNING else AuditStatus.SECURE,
        standardRef = "MITRE T1401 · CIS Android Benchmark",
        settingsAction = Settings.ACTION_SECURITY_SETTINGS,
    )

    // 5. Notification Listeners (OTP Interception Check)
    val notifListeners = runCatching {
        Settings.Secure.getString(cr, "enabled_notification_listeners").orEmpty()
    }.getOrDefault("").split(':').filter { it.isNotBlank() }
    val thirdPartyNotif = notifListeners.filterNot {
        it.startsWith("com.google.") || it.startsWith("com.android.") || it.startsWith("com.samsung.")
    }
    list += OsSecurityCheck(
        id = "chk-notif",
        titleFa = "شنود اعلان‌ها و پیامک‌های OTP",
        titleEn = "Notification / OTP Listeners Audit",
        detailFa = if (thirdPartyNotif.isEmpty()) {
            "هیچ برنامه غیررسمی مجوز خواندن اعلان‌ها را ندارد."
        } else {
            "${thirdPartyNotif.size} برنامه به اعلان‌های شما دسترسی دارد: ${thirdPartyNotif.joinToString()}"
        },
        detailEn = if (thirdPartyNotif.isEmpty()) {
            "No third-party Notification Listeners detected."
        } else {
            "${thirdPartyNotif.size} third-party listener(s) can read notifications/OTPs: ${thirdPartyNotif.joinToString()}"
        },
        status = if (thirdPartyNotif.isNotEmpty()) AuditStatus.WARNING else AuditStatus.SECURE,
        standardRef = "MITRE T1412 · OWASP MASVS-AUTH",
        settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
    )

    // 6. User CA Certificates in AndroidCAStore (SSL/HTTPS Interception)
    val userCaCount = runCatching {
        val ks = KeyStore.getInstance("AndroidCAStore")
        ks.load(null, null)
        ks.aliases().toList().count { it.startsWith("user:") }
    }.getOrDefault(0)
    list += OsSecurityCheck(
        id = "chk-ca",
        titleFa = "گواهی‌نامه‌های کاربری SSL / کشف شنود MITM",
        titleEn = "User-Installed Root CA Certificates (MITM Check)",
        detailFa = if (userCaCount == 0) {
            "هیچ گواهی ریشه کاربری (User CA) نصب نشده؛ خطر شنود ترافیک HTTPS پایین است."
        } else {
            "هشدار: $userCaCount گواهی ریشه کاربری در سیستم نصب شده که می‌تواند ترافیک رمزنگاری‌شده را شنود کند!"
        },
        detailEn = if (userCaCount == 0) {
            "0 user CA certificates in AndroidCAStore — no rogue SSL inspection root installed."
        } else {
            "$userCaCount user-installed CA certificate(s) found — HTTPS traffic could be intercepted!"
        },
        status = if (userCaCount > 0) AuditStatus.DANGER else AuditStatus.SECURE,
        standardRef = "OWASP MASVS-NETWORK · MITRE T1557",
        settingsAction = Settings.ACTION_SECURITY_SETTINGS,
    )

    // 7. Keyguard / Screen Lock Enforcement
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val deviceSecure = runCatching { km?.isDeviceSecure == true }.getOrDefault(false)
    list += OsSecurityCheck(
        id = "chk-lock",
        titleFa = "قفل صفحه و رمزنگاری ذخیره‌سازی",
        titleEn = "Device Screen Lock & Keyguard Protection",
        detailFa = if (deviceSecure) {
            "قفل صفحه رمزدار/بایومتریک و رمزنگاری سخت‌افزاری فعال است."
        } else {
            "هشدار جدی: قفل صفحه ایمن (PIN/الگو/اثرانگشت) روی گوشی تنظیم نشده است!"
        },
        detailEn = if (deviceSecure) {
            "Secure screen lock (PIN/Password/Pattern/Biometric) is active."
        } else {
            "No secure screen lock configured — physical access exposes all device data!"
        },
        status = if (deviceSecure) AuditStatus.SECURE else AuditStatus.DANGER,
        standardRef = "OWASP MASVS-STORAGE · NIST SP 800-124",
        settingsAction = Settings.ACTION_SECURITY_SETTINGS,
    )

    // 8. OS Security Patch Date & SDK Level
    val patch = Build.VERSION.SECURITY_PATCH
    val patchYear = patch.take(4).toIntOrNull() ?: 2020
    val patchStatus = when {
        patchYear >= 2025 -> AuditStatus.SECURE
        patchYear >= 2023 -> AuditStatus.WARNING
        else -> AuditStatus.DANGER
    }
    list += OsSecurityCheck(
        id = "chk-patch",
        titleFa = "سطح وصله امنیتی اندروید (Security Patch)",
        titleEn = "Android OS Security Patch & API Level",
        detailFa = "نسخه اندروید ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · تاریخ وصله امنیتی: $patch",
        detailEn = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · Security Patch: $patch",
        status = patchStatus,
        standardRef = "NIST SP 800-124 · Patch Hygiene",
    )

    // 9. Active VPN / Tunnel Interfaces (Pure Java NetworkInterface — Zero Network Perms!)
    val tunnels = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && (it.name.startsWith("tun") || it.name.startsWith("ppp") || it.name.startsWith("wg")) }
            ?.map { it.name }
            .orEmpty()
    }.getOrDefault(emptyList())
    list += OsSecurityCheck(
        id = "chk-vpn",
        titleFa = "پایش رابط‌های تونل و VPN فعال",
        titleEn = "Active Tunnel / VPN Interfaces Inspection",
        detailFa = if (tunnels.isEmpty()) {
            "هیچ رابط تونل/VPN ناشناس فعالی در زمان بازرسی دیده نشد."
        } else {
            "رابط تونل فعال شناسایی شد (${tunnels.joinToString()}). مطمئن شوید سرویس VPN متعلق به خودتان و معتبر است."
        },
        detailEn = if (tunnels.isEmpty()) {
            "No active tun/ppp/wg tunnel interfaces detected."
        } else {
            "Active tunnel interface(s): ${tunnels.joinToString()}. Verify this VPN belongs to a trusted provider."
        },
        status = if (tunnels.isNotEmpty()) AuditStatus.WARNING else AuditStatus.SECURE,
        standardRef = "OWASP MASVS-NETWORK · Traffic Hygiene",
        settingsAction = Settings.ACTION_WIRELESS_SETTINGS,
    )

    return list
}
