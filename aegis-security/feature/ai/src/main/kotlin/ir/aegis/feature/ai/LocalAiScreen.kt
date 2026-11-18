package ir.aegis.feature.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.aegis.core.ai.*
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.*
import ir.aegis.core.ui.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LocalAiScreen(
    repository: AegisRepository,
    preferences: AppPreferences,
    onModelImported: (String, String, String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    val isFa = lang == AppLanguage.FA
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Phishing URL/SMS, 1=Password Lab, 2=Cyber AI Assistant

    // Tab 0: Phishing Analyzer state
    var phishingInput by remember {
        mutableStateOf("ابلاغیه فوری ثنا: http://sana-adliran-portal.xyz/eblagh.apk")
    }
    val phishingReport = remember(phishingInput) {
        PhishingAnalyzer.analyzeTextOrUrl(phishingInput)
    }

    // Tab 1: Password Entropy & Generator state
    var pwdInput by remember { mutableStateOf("") }
    var pwdLength by remember { mutableFloatStateOf(18f) }
    var copiedMsg by remember { mutableStateOf<String?>(null) }
    val pwdAudit = remember(pwdInput) { PasswordSecurityEngine.auditPassword(pwdInput) }

    // Tab 2: Cyber AI Assistant & Local Model
    var assistantQuery by remember { mutableStateOf("") }
    var assistantReply by remember { mutableStateOf<AiAssistantResponse?>(null) }
    var kbQuery by remember { mutableStateOf("") }

    val modelsDir = remember { File(context.filesDir, "models").apply { mkdirs() } }
    var localModelFiles by remember { mutableStateOf(modelsDir.listFiles()?.toList().orEmpty()) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                } ?: "local_model_${System.currentTimeMillis()}.gguf"
                val dest = File(modelsDir, name.replace("[^A-Za-z0-9._-]".toRegex(), "_"))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                val hash = CryptoEngine.sha256Hex(dest.readBytes())
                localModelFiles = modelsDir.listFiles()?.toList().orEmpty()
                onModelImported(dest.absolutePath, dest.name, hash)
                repository.logActivity(
                    "LOCAL_AI_MODEL_IMPORTED",
                    "Phishing & Password Lab",
                    "Model=${dest.name}, SHA256=${hash.take(16)}",
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.AI,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = tr("ZERO-NET THREAT LAB", "آزمایشگاه آفلاین ضد فیشینگ"),
            showBackButton = showBackButton,
        )
        GlassTabRow(
            tabs = listOf(
                tr("Phishing URL/SMS", "تشخیص فیشینگ"),
                tr("Password Strength", "آزمایشگاه رمز"),
                tr("Cyber Advisor AI", "مشاور امنیتی آفلاین"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            // TAB 0: Offline Phishing URL & SMS Forensics
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("ai_tab_lazy_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                            SectionHeader(
                                title = tr("Offline SMS & URL Phishing Analyzer", "تحلیلگر آفلاین پیامک و لینک جعلی فیشینگ"),
                                badge = "HEURISTIC",
                            )
                            Text(
                                text = tr(
                                    "Detects Punycode homographs, fake court/bank portals, .apk malware droppers, raw IP links & social-engineering scam keywords without connecting to any server.",
                                    "تشخیص لینک‌های جعلی ثنا، شاپرک و یارانه، دامنه‌های هموگراف، فایل‌های .apk مخرب و ترفندهای مهندسی اجتماعی بدون ارسال پیامک به اینترنت.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            GlassTextField(
                                value = phishingInput,
                                onValueChange = { phishingInput = it },
                                label = tr("Paste suspicious SMS or URL here...", "متن پیامک یا لینک مشکوک را اینجا وارد کنید..."),
                                singleLine = false,
                                minLines = 3,
                            )
                            Spacer(Modifier.height(6.dp))
                            ChipFlowGroup {
                                AegisFilterPill(
                                    label = tr("Sample: Fake Sana", "نمونه: ابلاغیه جعلی"),
                                    selected = false,
                                    onClick = { phishingInput = "ابلاغیه فوری ثنا: http://sana-adliran-portal.xyz/eblagh.apk" },
                                    accent = colors.accentCrimson,
                                )
                                AegisFilterPill(
                                    label = tr("Sample: Fake Bank", "نمونه: درگاه بانکی جعلی"),
                                    selected = false,
                                    onClick = { phishingInput = "رفع مسدودی کارت بانکی: http://shaparak-mellat-ir.top/login" },
                                    accent = colors.accentAmber,
                                )
                                AegisFilterPill(
                                    label = tr("Sample: Official HTTPS", "نمونه: دامنه رسمی امن"),
                                    selected = false,
                                    onClick = { phishingInput = "https://shaparak.ir" },
                                    accent = colors.accentEmerald,
                                )
                                AegisFilterPill(
                                    label = tr("Clear", "پاک کردن"),
                                    selected = false,
                                    onClick = { phishingInput = "" },
                                )
                            }
                        }
                    }

                    item {
                        val tone = when (phishingReport.verdict) {
                            PhishingVerdict.MALICIOUS_PHISHING -> colors.accentCrimson
                            PhishingVerdict.SUSPICIOUS -> colors.accentAmber
                            PhishingVerdict.SAFE -> colors.accentEmerald
                        }
                        val verdictText = when (phishingReport.verdict) {
                            PhishingVerdict.MALICIOUS_PHISHING -> tr("MALICIOUS PHISHING (${phishingReport.riskScore}%)", "فیشینگ خطرناک (${phishingReport.riskScore}٪)")
                            PhishingVerdict.SUSPICIOUS -> tr("SUSPICIOUS (${phishingReport.riskScore}%)", "مشکوک (${phishingReport.riskScore}٪)")
                            PhishingVerdict.SAFE -> tr("SAFE (${phishingReport.riskScore}%)", "سالم / امن (${phishingReport.riskScore}٪)")
                        }
                        ObsidianGlassCard(elevated = true, accentBorderColor = tone) {
                            SectionHeader(title = tr("Forensic Verdict", "نتیجه تحلیل امنیتی"), badge = verdictText)
                            Text(
                                text = if (isFa) phishingReport.recommendationFa else phishingReport.recommendationEn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tone,
                            )
                            if (phishingReport.extractedUrls.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = tr("Extracted URLs:", "لینک‌های استخراج‌شده:") + " " + phishingReport.extractedUrls.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                )
                            }
                            if (phishingReport.signals.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                phishingReport.signals.forEach { sig ->
                                    Text(
                                        text = "• (+${sig.points}) ${if (isFa) sig.titleFa else sig.titleEn}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: Offline Password Entropy Lab & Generator
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        val tone = when (pwdAudit.grade) {
                            PasswordGrade.FORTRESS, PasswordGrade.STRONG -> colors.accentEmerald
                            PasswordGrade.FAIR -> colors.accentAmber
                            else -> colors.accentCrimson
                        }
                        val gradeLabel = when (pwdAudit.grade) {
                            PasswordGrade.FORTRESS -> tr("FORTRESS (${pwdAudit.entropyBits} bits)", "بسیار مستحکم (${pwdAudit.entropyBits} بیت)")
                            PasswordGrade.STRONG -> tr("STRONG (${pwdAudit.entropyBits} bits)", "قوی (${pwdAudit.entropyBits} بیت)")
                            PasswordGrade.FAIR -> tr("FAIR (${pwdAudit.entropyBits} bits)", "متوسط (${pwdAudit.entropyBits} بیت)")
                            PasswordGrade.WEAK -> tr("WEAK (${pwdAudit.entropyBits} bits)", "ضعیف (${pwdAudit.entropyBits} بیت)")
                            PasswordGrade.CRITICAL_WEAK -> tr("CRITICAL WEAK (${pwdAudit.entropyBits} bits)", "بسیار ضعیف (${pwdAudit.entropyBits} بیت)")
                        }

                        ObsidianGlassCard(elevated = true, accentBorderColor = tone) {
                            SectionHeader(title = tr("Password Entropy & Breach Check", "ارزیابی قدرت و آنتروپی رمز عبور"), badge = gradeLabel)
                            GlassTextField(
                                value = pwdInput,
                                onValueChange = { pwdInput = it },
                                label = tr("Type or paste password to audit offline...", "رمز عبور را برای بررسی محلی وارد کنید..."),
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { pwdAudit.scorePercent / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = tone,
                                trackColor = colors.divider,
                            )
                            Spacer(Modifier.height(6.dp))
                            val warnings = if (isFa) pwdAudit.warningsFa else pwdAudit.warningsEn
                            if (warnings.isEmpty() && pwdInput.isNotEmpty()) {
                                Text(tr("Excellent password resistance against offline brute-force.", "مقاومت عالی در برابر حملات شکستن رمز!"), style = MaterialTheme.typography.bodySmall, color = colors.accentEmerald)
                            } else {
                                warnings.forEach { w ->
                                    Text("• $w", style = MaterialTheme.typography.bodySmall, color = tone)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(tr("Generator Length: ${pwdLength.toInt()} chars", "طول رمز تولیدی: ${pwdLength.toInt()} کاراکتر"), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            Slider(value = pwdLength, onValueChange = { pwdLength = it }, valueRange = 12f..30f)
                            ChipFlowGroup {
                                GlassButton(
                                    label = tr("Generate Secure Password", "تولید رمز تصادفی فوق‌قوی"),
                                    onClick = {
                                        pwdInput = PasswordSecurityEngine.generateStrongPassword(pwdLength.toInt())
                                        copiedMsg = null
                                    },
                                    accent = colors.accentCyan,
                                )
                                GlassButton(
                                    label = tr("Copy Password", "کپی رمز"),
                                    onClick = {
                                        if (pwdInput.isNotEmpty()) {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cb?.setPrimaryClip(ClipData.newPlainText("AegisPass", pwdInput))
                                            copiedMsg = trText(lang, "Copied to clipboard.", "رمز در کلیپ‌بورد کپی شد.")
                                        }
                                    },
                                    accent = colors.accentEmerald,
                                    enabled = pwdInput.isNotEmpty(),
                                )
                            }
                            copiedMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.accentEmerald) }
                        }
                    }
                }
            }

            // TAB 2: Cyber AI Advisor + Local GGUF Model Importer
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                            SectionHeader(
                                title = tr("Offline Cyber Defense Assistant", "دستیار آفلاین مقابله با هک و تهدیدها"),
                                badge = tr("OFFLINE AI", "هوش محلی"),
                            )
                            GlassTextField(
                                value = assistantQuery,
                                onValueChange = { assistantQuery = it },
                                label = tr("Ask about trojans, phishing, MITM CA certs, ADB...", "بپرسید: تروجان بانکی، فیشینگ ثنا، گواهی CA، استاکرور..."),
                                singleLine = false,
                                minLines = 2,
                            )
                            Spacer(Modifier.height(8.dp))
                            ChipFlowGroup {
                                val presets = listOf(
                                    tr("Banking Trojan", "تروجان بانکی و Accessibility"),
                                    tr("Stalkerware", "کشف استاکرور و شنود"),
                                    tr("SMS Phishing", "فیشینگ پیامکی و سرقت OTP"),
                                    tr("Device Hacked IR", "اقدام فوری هنگام هک شدن"),
                                )
                                presets.forEach { preset ->
                                    AegisFilterPill(
                                        label = preset,
                                        selected = false,
                                        onClick = {
                                            assistantQuery = preset
                                            assistantReply = LocalAiEngine.answerQuery(preset, preferences.localModelName, isFa)
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                label = tr("Analyze Offline Knowledge Base", "تحلیل از دانشنامه امنیتی آفلاین"),
                                onClick = {
                                    assistantReply = LocalAiEngine.answerQuery(assistantQuery, preferences.localModelName, isFa)
                                },
                                accent = colors.accentCyan,
                            )
                        }
                    }

                    assistantReply?.let { reply ->
                        item {
                            ObsidianGlassCard(accentBorderColor = colors.accentTeal) {
                                SectionHeader(title = reply.engineLabel, badge = "${reply.confidencePercent}%")
                                Text(reply.summaryAnswer, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                                Spacer(Modifier.height(4.dp))
                                reply.suggestedSteps.forEachIndexed { idx, s ->
                                    Text("${idx + 1}. $s", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                                if (reply.citedSources.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(tr("Standards: ", "استانداردها: ") + reply.citedSources.joinToString(" | "), style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                                }
                            }
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            SectionHeader(title = tr("Search Security Knowledge Library", "جستجو در دانشنامه امنیت موبایل"))
                            GlassTextField(value = kbQuery, onValueChange = { kbQuery = it }, label = tr("Filter playbooks...", "فیلتر راهنماها..."))
                        }
                    }
                    val hits = LocalAiEngine.searchKnowledge(kbQuery, limit = 6)
                    items(hits, key = { it.doc.id }) { hit ->
                        ObsidianGlassCard {
                            Text(hit.doc.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(hit.doc.reviewedSource, style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                            Text(hit.doc.content, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }

                    item {
                        ObsidianGlassCard {
                            SectionHeader(title = tr("Optional Local GGUF Model Importer", "مدل زبانی محلی آفلاین (اختیاری)"))
                            Text(
                                text = if (preferences.localModelName.isBlank()) {
                                    tr("Using built-in deterministic cybersecurity engine.", "هم‌اکنون از موتور هوشمند قطعی آفلاین استفاده می‌شود.")
                                } else {
                                    "${preferences.localModelName} (${preferences.localModelSha256.take(14)}…)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            GlassButton(
                                label = tr("Import Local .gguf / .tflite", "افزودن فایل مدل محلی"),
                                onClick = { modelPicker.launch(arrayOf("*/*")) },
                                accent = colors.accentViolet,
                            )
                        }
                    }
                }
            }
        }
    }
}
