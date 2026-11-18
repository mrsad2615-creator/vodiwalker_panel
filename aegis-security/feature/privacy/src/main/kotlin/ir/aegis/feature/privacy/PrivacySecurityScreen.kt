package ir.aegis.feature.privacy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivacySecurityScreen(
    repository: AegisRepository,
    preferencesRepository: PreferencesRepository,
    preferences: AppPreferences,
    onLockAppNow: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val auditLogs by repository.observeAuditLogs().collectAsState(initial = emptyList())
    val vaultCount by repository.observeVaultItems().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    var newPin by remember { mutableStateOf("") }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

    var backupPass by remember { mutableStateOf("") }
    var restorePass by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val biometricAvailable = remember(context) {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val restoreFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    require(restorePass.length >= 6) { "Enter restore passphrase (min 6 chars) first." }
                    val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read selected backup file")
                    val snapshot = CryptoEngine.openEncryptedBackup(rawText, restorePass)
                    repository.restoreFullSnapshot(snapshot)
                    preferencesRepository.replacePreferences(snapshot.preferences)
                    repository.logActivity("BACKUP_RESTORE", "Privacy & Security", "Restored encrypted offline backup (${snapshot.incidents.size} incidents)")
                }.onSuccess {
                    backupStatus = "Backup decrypted & restored cleanly!"
                }.onFailure { err ->
                    backupStatus = "Restore failed: ${err.message}"
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.PRIVACY,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = if (preferences.pinEnabled) tr("PIN PROTECTED · ZERO CLOUD", "محافظت‌شده با پین · بدون اینترنت") else tr("LOCAL CRYPTO READY", "رمزنگاری محلی آماده"),
            showBackButton = showBackButton,
        )
        GlassTabRow(
            tabs = listOf(
                tr("Lock & Privacy", "قفل و حریم خصوصی"),
                tr("Encrypted Backup", "بکاپ رمزنگاری"),
                tr("Audit & Perms", "گزارش و مجوزها"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("PIN & Biometric App Lock", "قفل پین و بیومتریک"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(6.dp))
                            GlassTextField(newPin, { newPin = it }, tr("Set 4–8 Digit App Lock PIN", "تعیین پین ۴ تا ۸ رقمی"))
                            Spacer(Modifier.height(8.dp))
                            val pinEnabledText = tr("PIN protection enabled offline.", "قفل پین فعال شد.")
                            val pinDisabledText = tr("PIN lock disabled.", "قفل پین غیرفعال شد.")
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Enable / Update PIN", "فعال‌سازی پین"),
                                    enabled = newPin.length >= 4,
                                    onClick = {
                                        scope.launch {
                                            val salt = CryptoEngine.generateSaltBase64()
                                            val hash = CryptoEngine.hashPin(newPin, salt)
                                            preferencesRepository.setPinCredential(saltBase64 = salt, hashBase64 = hash)
                                            repository.logActivity("PIN_SET", "Privacy & Security", "Configured PBKDF2-SHA256 app lock PIN")
                                            newPin = ""
                                            pinFeedback = pinEnabledText
                                        }
                                    },
                                )
                                if (preferences.pinEnabled) {
                                    GlassButton(
                                        label = tr("Lock Now", "قفل فوری"),
                                        accent = colors.accentAmber,
                                        onClick = onLockAppNow,
                                    )
                                }
                            }
                            if (preferences.pinEnabled) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            preferencesRepository.clearPinCredential()
                                            pinFeedback = pinDisabledText
                                        }
                                    },
                                ) { Text(tr("Disable PIN Lock", "حذف قفل پین"), color = colors.accentCrimson) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (biometricAvailable) tr("Biometric Unlock (Fingerprint/Face)", "بازگشایی با اثر انگشت")
                                    else tr("Biometric Hardware Not Enrolled on Device", "سخت‌افزار بیومتریک ثبت نشده"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = preferences.biometricEnabled,
                                    enabled = biometricAvailable && preferences.pinEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { preferencesRepository.setBiometricEnabled(enabled) }
                                    },
                                )
                            }
                            if (pinFeedback != null) {
                                Text(pinFeedback!!, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald)
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Privacy & Sensitive Notification Controls", "کنترل حریم خصوصی و اعلان‌ها"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tr("Sensitive Notification Redaction", "مخفی‌سازی متن اعلان‌های حساس"), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                    Text(tr("Hides reminder details on lock screen notifications.", "جزئیات یادآورها را در صفحه قفل مخفی می‌کند."), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                }
                                Switch(
                                    checked = preferences.sensitiveNotificationRedacted,
                                    onCheckedChange = { v -> scope.launch { preferencesRepository.setSensitiveNotificationRedacted(v) } },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tr("Screen Capture Protection (FLAG_SECURE)", "محافظت در برابر اسکرین‌شات"), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                    Text(tr("Prevents screenshots & recent-apps thumbnail leaks.", "مانع ثبت اسکرین‌شات می‌شود."), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                }
                                Switch(
                                    checked = preferences.flagSecureEnabled,
                                    onCheckedChange = { v -> scope.launch { preferencesRepository.setFlagSecure(v) } },
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("${tr("Encrypted Vault Items Stored Locally", "اسناد رمزنگاری‌شده موجود")}: ${vaultCount.size}", style = MaterialTheme.typography.labelMedium, color = colors.accentViolet)
                        }
                    }

                    item {
                        val wipedText = tr("Sensitive vault & private journal wiped.", "گاوصندوق و یادداشت‌های حساس پاک شدند.")
                        ObsidianGlassCard(accentBorderColor = colors.accentCrimson) {
                            Text(tr("Emergency Sensitive Wipe (Vault + Journal)", "پاکسازی اضطراری داده‌های حساس"), style = MaterialTheme.typography.titleMedium, color = colors.accentCrimson)
                            Spacer(Modifier.height(6.dp))
                            GlassButton(
                                label = tr("Wipe Sensitive Local Secrets", "حذف فوری اطلاعات حساس"),
                                accent = colors.accentCrimson,
                                onClick = {
                                    scope.launch {
                                        repository.panicWipeSensitiveData()
                                        backupStatus = wipedText
                                    }
                                },
                            )
                        }
                    }
                }
            }

            1 -> {
                val backupDir = remember { File(context.filesDir, "backups").apply { mkdirs() } }
                var localBackups by remember { mutableStateOf(backupDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true) {
                            Text(tr("Create AES-256-GCM Encrypted Backup", "ساخت نسخه پشتیبان رمزنگاری‌شده"), style = MaterialTheme.typography.titleMedium, color = colors.accentCyan)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(backupPass, { backupPass = it }, tr("Backup Encryption Passphrase (min 6 chars)", "رمز بکاپ (حداقل ۶ کاراکتر)"))
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Seal & Export Encrypted Backup (.aegisbak)", "رمزنگاری و خروجی بکاپ"),
                                enabled = backupPass.length >= 6,
                                onClick = {
                                    scope.launch {
                                        val now = System.currentTimeMillis()
                                        val snap = repository.exportFullSnapshot(preferences)
                                        val sealed = CryptoEngine.sealEncryptedBackup(snap, backupPass, now)
                                        val f = LocalFileExporter.exportTextFile(context, "backups", "aegis_backup_$now.aegisbak", sealed)
                                        preferencesRepository.setLastBackupTimestamp(now)
                                        localBackups = backupDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
                                        repository.logActivity("BACKUP_EXPORT", "Privacy & Security", "Created AES-256-GCM backup (${f.name})")
                                        backupStatus = "Encrypted backup created: ${f.name}"
                                        LocalFileExporter.shareFile(context, f, "application/octet-stream", "Export Encrypted Aegis Backup")
                                    }
                                },
                            )
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            Text(tr("Restore Encrypted Backup", "بازیابی نسخه پشتیبان رمزنگاری‌شده"), style = MaterialTheme.typography.titleMedium, color = colors.accentTeal)
                            Spacer(Modifier.height(8.dp))
                            GlassTextField(restorePass, { restorePass = it }, tr("Backup Decryption Passphrase", "رمز فایل پشتیبان"))
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Pick .aegisbak File to Restore", "انتخاب فایل بکاپ برای بازیابی"),
                                accent = colors.accentTeal,
                                enabled = restorePass.length >= 6,
                                onClick = { restoreFilePicker.launch(arrayOf("*/*")) },
                            )
                            if (backupStatus != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(backupStatus!!, style = MaterialTheme.typography.labelMedium, color = colors.accentEmerald)
                            }
                        }
                    }

                    items(localBackups, key = { it.name }) { file ->
                        ObsidianGlassCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                                    Text("${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                }
                                TextButton(
                                    enabled = restorePass.length >= 6,
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                val snap = CryptoEngine.openEncryptedBackup(file.readText(), restorePass)
                                                repository.restoreFullSnapshot(snap)
                                                preferencesRepository.replacePreferences(snap.preferences)
                                            }.onSuccess {
                                                backupStatus = "Restored from ${file.name}!"
                                            }.onFailure { err ->
                                                backupStatus = "Failed: ${err.message}"
                                            }
                                        }
                                    },
                                ) { Text(tr("Restore", "بازیابی")) }
                            }
                        }
                    }
                }
            }

            2 -> {
                var selectedPerm by remember { mutableStateOf("android.permission.CAMERA") }
                val permOptions = listOf(
                    "android.permission.CAMERA" to tr("Camera", "دوربین"),
                    "android.permission.RECORD_AUDIO" to tr("Microphone", "میکروفون"),
                    "android.permission.READ_SMS" to tr("SMS / OTP", "پیامک"),
                    "android.permission.ACCESS_BACKGROUND_LOCATION" to tr("BG Location", "مکان دائم"),
                    "android.permission.SYSTEM_ALERT_WINDOW" to tr("Overlay", "روی صفحه"),
                    "android.permission.REQUEST_INSTALL_PACKAGES" to tr("Install APK", "نصب برنامه"),
                )
                val appsWithPerm = remember(context, selectedPerm) {
                    queryAppsForPermission(context, selectedPerm)
                }
                val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                            SectionHeader(
                                title = tr("App Privacy Permission Auditor", "ممیزی مجوزهای حساس برنامه‌های نصب‌شده"),
                                badge = "${appsWithPerm.size} " + tr("apps", "برنامه"),
                            )
                            Text(
                                text = tr(
                                    "Zero-network design verified: AegisGuard has NO INTERNET permission. Audit which third-party apps access sensitive hardware below:",
                                    "دسترسی اینترنت در ایجیس‌گارد صفر است. با انتخاب هر مجوز، برنامه‌های شخص‌ثالث دارای آن دسترسی را ببینید و مدیریت کنید:",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            ChipFlowGroup {
                                permOptions.forEach { (perm, title) ->
                                    AegisFilterPill(
                                        label = title,
                                        selected = selectedPerm == perm,
                                        onClick = { selectedPerm = perm },
                                        accent = colors.accentCyan,
                                    )
                                }
                            }
                        }
                    }

                    items(appsWithPerm, key = { it.first }) { (pkgName, appLabel, granted) ->
                        ObsidianGlassCard(accentBorderColor = if (granted) colors.accentAmber else colors.divider) {
                            SectionHeader(
                                title = appLabel,
                                badge = if (granted) tr("GRANTED", "مجوز فعال") else tr("REQUESTED", "درخواست‌شده"),
                            )
                            Text(pkgName, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Spacer(Modifier.height(4.dp))
                            GlassButton(
                                label = tr("Manage / Revoke in System", "لغو یا بررسی در تنظیمات اندروید"),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkgName, null))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                                accent = if (granted) colors.accentAmber else colors.accentTeal,
                            )
                        }
                    }

                    item {
                        Text(tr("Local Security Audit History (${auditLogs.size})", "تاریخچه ممیزی و عملیات (${auditLogs.size})"), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    }
                    items(auditLogs, key = { it.id }) { log ->
                        ObsidianGlassCard {
                            Text("[${log.moduleName}] ${log.actionType}", style = MaterialTheme.typography.labelMedium, color = colors.accentTeal)
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            Text(fmt.format(Date(log.timestampMillis)), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("QueryPermissionsNeeded")
@Suppress("DEPRECATION")
private fun queryAppsForPermission(context: android.content.Context, selectedPerm: String): List<Triple<String, String, Boolean>> {
    val pm = context.packageManager
    val pkgs = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
    }.getOrDefault(emptyList())
    return pkgs.filter { pkg ->
        val isSys = ((pkg.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        !isSys && (pkg.requestedPermissions?.contains(selectedPerm) == true)
    }.map { pkg ->
        val lbl = pkg.applicationInfo?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() } ?: pkg.packageName
        val granted = pkg.requestedPermissions?.let { perms ->
            val idx = perms.indexOf(selectedPerm)
            val flags = pkg.requestedPermissionsFlags
            idx >= 0 && flags != null && (flags[idx] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        } ?: false
        Triple(pkg.packageName, lbl, granted)
    }.sortedByDescending { it.third }
}
