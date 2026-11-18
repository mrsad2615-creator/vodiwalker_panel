package ir.aegis.feature.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.aegis.core.ai.*
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Composable
fun AdvancedUtilitiesScreen(
    repository: AegisRepository,
    reminderScheduler: ReminderScheduler,
    preferences: AppPreferences,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0=App Malware Scan, 1=File & APK Scanner, 2=Security Tools

    var scanningApps by remember { mutableStateOf(false) }
    var includeSystemApps by remember { mutableStateOf(false) }
    var appFilter by remember { mutableStateOf("ALL") } // ALL, SUSPICIOUS, SIDELOADED
    var searchAppQuery by remember { mutableStateOf("") }
    var scanResults by remember { mutableStateOf<List<AppScanResult>>(emptyList()) }

    suspend fun runFullAppScan(withSystem: Boolean) {
        scanningApps = true
        val results = withContext(Dispatchers.IO) { scanInstalledApplications(context, withSystem) }
        scanResults = results
        scanningApps = false
        val risky = results.count { it.riskLevel != ThreatRiskLevel.CLEAN && it.riskLevel != ThreatRiskLevel.LOW }
        repository.logActivity(
            "APP_MALWARE_SCAN",
            "App Malware Scanner",
            "Scanned=${results.size}, Suspicious=$risky",
        )
    }

    LaunchedEffect(includeSystemApps) {
        runFullAppScan(includeSystemApps)
    }

    val filteredApps = remember(scanResults, appFilter, searchAppQuery) {
        scanResults.filter { app ->
            val matchFilter = when (appFilter) {
                "SUSPICIOUS" -> app.riskLevel == ThreatRiskLevel.MEDIUM || app.riskLevel == ThreatRiskLevel.HIGH || app.riskLevel == ThreatRiskLevel.CRITICAL
                "SIDELOADED" -> app.isSideloaded
                else -> true
            }
            val matchQuery = searchAppQuery.isBlank() ||
                app.appLabel.contains(searchAppQuery, ignoreCase = true) ||
                app.packageName.contains(searchAppQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    val criticalCount = scanResults.count { it.riskLevel == ThreatRiskLevel.CRITICAL || it.riskLevel == ThreatRiskLevel.HIGH }
    val sideloadCount = scanResults.count { it.isSideloaded }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.UTILITIES,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = tr("${scanResults.size} Apps Audited", "${scanResults.size} برنامه اسکن شد"),
            showBackButton = showBackButton,
        )
        GlassTabRow(
            tabs = listOf(
                tr("App Malware Scan", "اسکنر بدافزار برنامه"),
                tr("File / APK Scanner", "اسکن فایل و APK"),
                tr("Crypto & Field Lab", "ابزار هش و میدان"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

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
                                value = "${scanResults.size}",
                                label = tr("Apps", "کل برنامه‌ها"),
                                icon = Icons.Rounded.Apps,
                                accent = colors.accentCyan,
                                modifier = Modifier.weight(1f),
                            )
                            BentoMetricTile(
                                value = "$criticalCount",
                                label = tr("Alerts", "مشکوک"),
                                icon = Icons.Rounded.BugReport,
                                accent = if (criticalCount > 0) colors.accentCrimson else colors.accentEmerald,
                                modifier = Modifier.weight(1f),
                            )
                            BentoMetricTile(
                                value = "$sideloadCount",
                                label = tr("Sideload", "نصب دستی"),
                                icon = Icons.Rounded.InstallMobile,
                                accent = if (sideloadCount > 0) colors.accentAmber else colors.accentTeal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    item {
                        ObsidianGlassCard(
                            elevated = true,
                            accentBorderColor = if (criticalCount > 0) colors.accentCrimson else colors.accentEmerald,
                        ) {
                            SectionHeader(
                                title = tr("Antivirus & APK Heuristic Engine", "موتور ضدویروس و تشخیص بدافزار برنامه‌ها"),
                                badge = if (criticalCount == 0) tr("CLEAN", "پاک") else tr("$criticalCount ALERT", "$criticalCount هشدار"),
                            )
                            Text(
                                text = tr(
                                    "Audits signing certificates (SHA-256), installer trust chain, debuggable flags, spyware/trojan permission combos & MITRE ATT&CK Mobile signatures completely offline.",
                                    "اسکن کامل گواهی امضای دیجیتال، مرجع نصب (استور یا نصب دستی)، فلگ دیباگ و ترکیب مجوزهای تروجان بانکی و جاسوسی مطابق استاندارد OWASP/MITRE بدون اینترنت.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            GlassTextField(
                                value = searchAppQuery,
                                onValueChange = { searchAppQuery = it },
                                label = tr("Search app name or package ID...", "جستجوی نام برنامه یا شناسه بسته..."),
                            )
                            Spacer(Modifier.height(6.dp))
                            ChipFlowGroup {
                                AegisFilterPill(tr("All (${scanResults.size})", "همه (${scanResults.size})"), appFilter == "ALL", { appFilter = "ALL" })
                                AegisFilterPill(
                                    tr("Suspicious ($criticalCount+)", "مشکوک ($criticalCount+)"),
                                    appFilter == "SUSPICIOUS",
                                    { appFilter = "SUSPICIOUS" },
                                    accent = colors.accentCrimson,
                                )
                                AegisFilterPill(
                                    tr("Sideloaded ($sideloadCount)", "نصب دستی ($sideloadCount)"),
                                    appFilter == "SIDELOADED",
                                    { appFilter = "SIDELOADED" },
                                    accent = colors.accentAmber,
                                )
                                AegisFilterPill(
                                    tr("Include System", "شامل سیستمی"),
                                    includeSystemApps,
                                    { includeSystemApps = !includeSystemApps },
                                    accent = colors.accentViolet,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            GlassButton(
                                label = if (scanningApps) tr("Scanning Packages...", "در حال اسکن بسته‌ها...") else tr("Re-Scan Installed Apps", "اسکن مجدد تمام برنامه‌ها"),
                                onClick = { scope.launch { runFullAppScan(includeSystemApps) } },
                                accent = colors.accentCyan,
                                enabled = !scanningApps,
                            )
                        }
                    }

                    items(filteredApps, key = { it.packageName }) { app ->
                        val tone = when (app.riskLevel) {
                            ThreatRiskLevel.CRITICAL -> colors.accentCrimson
                            ThreatRiskLevel.HIGH -> colors.accentCrimson
                            ThreatRiskLevel.MEDIUM -> colors.accentAmber
                            ThreatRiskLevel.LOW -> colors.accentTeal
                            ThreatRiskLevel.CLEAN -> colors.accentEmerald
                        }
                        val riskLabel = when (app.riskLevel) {
                            ThreatRiskLevel.CRITICAL -> tr("CRITICAL ${app.riskScore}%", "بحرانی ${app.riskScore}٪")
                            ThreatRiskLevel.HIGH -> tr("HIGH ${app.riskScore}%", "ریسک بالا ${app.riskScore}٪")
                            ThreatRiskLevel.MEDIUM -> tr("MEDIUM ${app.riskScore}%", "متوسط ${app.riskScore}٪")
                            ThreatRiskLevel.LOW -> tr("LOW ${app.riskScore}%", "کم ${app.riskScore}٪")
                            ThreatRiskLevel.CLEAN -> tr("CLEAN", "پاک")
                        }
                        ObsidianGlassCard(accentBorderColor = tone) {
                            SectionHeader(title = app.appLabel, badge = riskLabel)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Text(
                                text = tr("Source: ", "مرجع نصب: ") +
                                    (if (lang == AppLanguage.FA) app.installerLabelFa else app.installerLabelEn) +
                                    " · ${app.sensitivePermCount} " + tr("sensitive perms", "مجوز حساس"),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = "Cert SHA-256: ${app.signingCertSha256.take(20)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                            )
                            if (app.findings.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                app.findings.forEach { f ->
                                    Text(
                                        text = "• [${f.mitreTag}] ${if (lang == AppLanguage.FA) f.titleFa else f.titleEn}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tone,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "  ${if (lang == AppLanguage.FA) f.detailFa else f.detailEn}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            if (app.riskLevel != ThreatRiskLevel.CLEAN) {
                                Spacer(Modifier.height(6.dp))
                                GlassButton(
                                    label = tr("Inspect / Uninstall App", "بررسی مجوزها / حذف برنامه"),
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", app.packageName, null),
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                    accent = tone,
                                )
                            }
                        }
                    }
                }
            }

            1 -> LocalPayloadFileScannerTab(repository = repository)
            else -> SecurityHashAndFieldTab(repository = repository)
        }
    }
}

@Composable
private fun LocalPayloadFileScannerTab(repository: AegisRepository) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileHistory by remember { mutableStateOf<List<FileScanResult>>(emptyList()) }
    var isScanningFile by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                isScanningFile = true
                val result = withContext(Dispatchers.IO) {
                    var displayName = "selected_file"
                    var size = 0L
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val nIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (c.moveToFirst()) {
                            if (nIdx >= 0) displayName = c.getString(nIdx) ?: displayName
                            if (sIdx >= 0) size = c.getLong(sIdx)
                        }
                    }
                    val md = MessageDigest.getInstance("SHA-256")
                    var actualBytes = 0L
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            md.update(buf, 0, read)
                            actualBytes += read
                        }
                    }
                    val hex = md.digest().joinToString("") { "%02x".format(it) }
                    ThreatSignatureEngine.scanLocalPayloadFile(displayName, if (size > 0) size else actualBytes, hex)
                }
                fileHistory = listOf(result) + fileHistory
                isScanningFile = false
                repository.logActivity(
                    "FILE_PAYLOAD_SCAN",
                    "App Malware Scanner",
                    "${result.fileName} -> ${result.riskLevel} (SHA256=${result.sha256Hex.take(12)})",
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                SectionHeader(
                    title = tr("Storage File & APK Payload Scanner", "اسکن فایل‌های دانلودی، APK و هش SHA-256"),
                    badge = "OFFLINE AV",
                )
                Text(
                    text = tr(
                        "Pick any downloaded APK, document, or archive to calculate its SHA-256 fingerprint, detect fake double extensions (e.g. .pdf.apk), and check threat hashes locally.",
                        "فایل دانلودی یا فایل نصبی APK را انتخاب کنید تا اثرانگشت SHA-256 محاسبه شده، پسوندهای دوگانه فریبنده و الگوهای مخرب کاملاً آفلاین شناسایی شوند.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                ChipFlowGroup {
                    GlassButton(
                        label = if (isScanningFile) tr("Hashing & Scanning...", "در حال اسکن...") else tr("Pick File / APK to Scan", "انتخاب فایل یا APK برای اسکن"),
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        accent = colors.accentCyan,
                        enabled = !isScanningFile,
                    )
                    GlassButton(
                        label = tr("Test EICAR Signature", "تست نمونه استاندارد EICAR"),
                        onClick = {
                            val eicarBytes = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*".toByteArray()
                            val hex = MessageDigest.getInstance("SHA-256").digest(eicarBytes).joinToString("") { "%02x".format(it) }
                            val res = ThreatSignatureEngine.scanLocalPayloadFile("eicar_sample.txt", eicarBytes.size.toLong(), hex)
                            fileHistory = listOf(res) + fileHistory
                        },
                        accent = colors.accentAmber,
                    )
                }
            }
        }

        items(fileHistory, key = { "${it.fileName}-${it.sha256Hex}" }) { item ->
            val tone = when (item.riskLevel) {
                ThreatRiskLevel.CRITICAL, ThreatRiskLevel.HIGH -> colors.accentCrimson
                ThreatRiskLevel.MEDIUM -> colors.accentAmber
                else -> colors.accentEmerald
            }
            ObsidianGlassCard(accentBorderColor = tone) {
                SectionHeader(title = item.fileName, badge = item.riskLevel.name)
                Text(if (lang == AppLanguage.FA) item.reasonFa else item.reasonEn, style = MaterialTheme.typography.bodyMedium, color = tone)
                Text("${item.sizeBytes} bytes · SHA-256: ${item.sha256Hex}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
    }
}

@SuppressLint("QueryPermissionsNeeded")
@Suppress("DEPRECATION")
private fun scanInstalledApplications(context: Context, includeSystem: Boolean): List<AppScanResult> {
    val pm = context.packageManager
    val flags = PackageManager.GET_PERMISSIONS or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    val packages = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
    }.getOrDefault(emptyList())

    val certFactory = CertificateFactory.getInstance("X.509")

    return packages.mapNotNull { pkg ->
        val appInfo = pkg.applicationInfo ?: return@mapNotNull null
        val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (!includeSystem && isSys) return@mapNotNull null
        if (pkg.packageName == context.packageName) return@mapNotNull null

        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg.packageName)
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg.packageName).installingPackageName
            } else {
                pm.getInstallerPackageName(pkg.packageName)
            }
        }.getOrNull()

        val sigBytes = extractSignatureBytes(pkg)
        val (sha256, subject) = if (sigBytes != null) {
            val hash = MessageDigest.getInstance("SHA-256").digest(sigBytes).joinToString("") { "%02x".format(it) }
            val subj = runCatching {
                val cert = certFactory.generateCertificate(ByteArrayInputStream(sigBytes)) as? X509Certificate
                cert?.subjectX500Principal?.name ?: ""
            }.getOrDefault("")
            hash to subj
        } else {
            "unavailable" to ""
        }

        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isTestOnly = (appInfo.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0
        val usesCleartext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
        } else {
            false
        }
        val perms = pkg.requestedPermissions?.toSet().orEmpty()

        ThreatSignatureEngine.analyzeInstalledApp(
            InstalledAppInput(
                packageName = pkg.packageName,
                appLabel = label,
                installerPackage = installer,
                isSystemApp = isSys,
                isDebuggable = isDebuggable,
                isTestOnly = isTestOnly,
                usesCleartextTraffic = usesCleartext,
                signingCertSha256 = sha256,
                signerSubject = subject,
                requestedPermissions = perms,
                exportedComponentsCount = 0,
                targetSdk = appInfo.targetSdkVersion,
            ),
        )
    }.sortedByDescending { it.riskScore }
}

@Suppress("DEPRECATION")
private fun extractSignatureBytes(pkg: PackageInfo): ByteArray? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val si = pkg.signingInfo ?: return null
        val sigs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        sigs?.firstOrNull()?.toByteArray()
    } else {
        pkg.signatures?.firstOrNull()?.toByteArray()
    }
}
