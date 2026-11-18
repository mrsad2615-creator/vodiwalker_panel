package ir.aegis.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ir.aegis.core.crypto.CryptoEngine
import ir.aegis.core.model.AegisModule
import ir.aegis.core.model.AppPreferences
import ir.aegis.core.ui.*

@Composable
fun LockGateOverlay(
    preferences: AppPreferences,
    onUnlocked: () -> Unit,
) {
    val colors = LocalObsidianColors.current
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val canUseBiometric = remember(context, preferences.biometricEnabled) {
        preferences.biometricEnabled &&
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    errorMsg = errString.toString()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AegisGuard")
            .setSubtitle("Authenticate locally — zero data leaves this device")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) triggerBiometric()
    }

    ObsidianBackground {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ObsidianGlassCard(elevated = true, accentBorderColor = colors.accentCyan) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AegisModuleIcon(module = AegisModule.PRIVACY, tint = colors.accentCyan, size = 44.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(tr("AegisGuard Locked", "سیستم حفاظت قفل است"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text(tr("Offline PBKDF2-SHA256 Verification", "احراز هویت آفلاین"), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    GlassTextField(pinInput, { pinInput = it; errorMsg = null }, tr("Enter PIN", "پین را وارد کنید"))
                    if (errorMsg != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(errorMsg!!, style = MaterialTheme.typography.labelMedium, color = colors.accentCrimson)
                    }
                    Spacer(Modifier.height(14.dp))
                    val incorrectPinMsg = tr("Incorrect PIN", "پین اشتباه است")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            label = tr("Unlock", "باز کردن"),
                            onClick = {
                                if (CryptoEngine.verifyPin(pinInput, preferences.pinSaltBase64, preferences.pinHashBase64)) {
                                    onUnlocked()
                                } else {
                                    errorMsg = incorrectPinMsg
                                }
                            },
                        )
                        if (canUseBiometric) {
                            GlassButton(
                                label = tr("Biometric", "اثر انگشت"),
                                accent = colors.accentTeal,
                                onClick = { triggerBiometric() },
                            )
                        }
                    }
                }
            }
        }
    }
}
