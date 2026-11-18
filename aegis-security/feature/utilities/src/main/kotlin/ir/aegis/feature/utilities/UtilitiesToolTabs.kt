package ir.aegis.feature.utilities

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.aegis.core.model.AegisRepository
import ir.aegis.core.ui.*
import java.security.MessageDigest

@Composable
fun SecurityHashAndFieldTab(repository: AegisRepository) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var expectedHash by remember { mutableStateOf("") }
    val computedSha256 = remember(inputText) {
        if (inputText.isEmpty()) "" else MessageDigest.getInstance("SHA-256").digest(inputText.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    var torchOn by remember { mutableStateOf(false) }
    var magUt by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val mag = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    magUt = kotlin.math.sqrt(x * x + y * y + z * z)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (mag != null) sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                SectionHeader(title = tr("SHA-256 Integrity & Checksum Verifier", "محاسبه و راستی‌آزمایی هش SHA-256"))
                GlassTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = tr("Text / Certificate / Payload String", "متن یا رشته برای محاسبه امضای SHA-256"),
                    singleLine = false,
                    minLines = 2,
                )
                if (computedSha256.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("SHA-256: $computedSha256", style = MaterialTheme.typography.labelSmall, color = colors.accentCyan)
                }
                Spacer(Modifier.height(6.dp))
                GlassTextField(
                    value = expectedHash,
                    onValueChange = { expectedHash = it },
                    label = tr("Expected SHA-256 to compare (optional)", "هش مرجع جهت مقایسه اصالت (اختیاری)"),
                )
                if (expectedHash.isNotBlank() && computedSha256.isNotEmpty()) {
                    val matches = expectedHash.trim().equals(computedSha256, ignoreCase = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (matches) tr("EXACT MATCH — Verified Authentic", "تطابق کامل — اصالت تایید شد") else tr("MISMATCH — Integrity Warning!", "عدم تطابق — هشدار تغییر داده!"),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (matches) colors.accentEmerald else colors.accentCrimson,
                    )
                }
            }
        }

        item {
            ObsidianGlassCard {
                SectionHeader(
                    title = tr("RF / Magnetometer Anomaly Meter & Torch", "میدان‌سنج مغناطیسی (کشف تجهیزات پنهان) و چراغ"),
                    badge = "${"%.1f".format(magUt)} µT",
                )
                Text(
                    text = tr(
                        "Normal Earth field ranges ~25–65 µT. Sudden spikes (>95 µT) near surfaces may indicate concealed electronics or speakers.",
                        "میدان طبیعی زمین حدود ۲۵ تا ۶۵ میکروتسلا است؛ افزایش ناگهانی نزدیک اشیا می‌تواند نشانه تجهیزات الکترونیکی یا ردیاب نزدیک باشد.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    label = if (torchOn) tr("Turn Torch Off", "خاموش کردن چراغ") else tr("Turn Inspection Torch On", "روشن کردن چراغ بازرسی"),
                    onClick = {
                        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                        val id = runCatching {
                            cm?.cameraIdList?.firstOrNull {
                                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                            }
                        }.getOrNull()
                        if (id != null) {
                            val next = !torchOn
                            runCatching { cm?.setTorchMode(id, next) }
                            torchOn = next
                        }
                    },
                    accent = if (torchOn) colors.accentAmber else colors.accentTeal,
                )
            }
        }
    }
}
