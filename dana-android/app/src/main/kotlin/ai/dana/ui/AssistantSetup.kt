package ai.dana.ui

import ai.dana.R
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun AssistantSetup(enabled: Boolean, beforeLaunch: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var held by remember(context) { mutableStateOf(isAssistant(context)) }
    var failed by remember { mutableStateOf(false) }
    var requestedRole by rememberSaveable { mutableStateOf(false) }
    var notSelected by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        held = isAssistant(context)
        notSelected = requestedRole && !held
        requestedRole = false
    }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) held = isAssistant(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun openAssistantSettings() {
        requestedRole = false
        failed = listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_VOICE_INPUT_SETTINGS).none { action ->
            runCatching { launcher.launch(Intent(action)) }.isSuccess
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.phone_assistant_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (held) R.string.phone_assistant_active else R.string.phone_assistant_help), color = if (held) Mint else Titanium)
        OutlinedButton(onClick = {
            beforeLaunch()
            val request = if (Build.VERSION.SDK_INT >= 29 && !held) runCatching { requestRole(context) }.getOrNull() else null
            notSelected = false
            requestedRole = request != null
            val roleOpened = request != null && runCatching { launcher.launch(request) }.isSuccess
            failed = false
            if (!roleOpened) openAssistantSettings()
        }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (held) R.string.phone_assistant_settings else R.string.phone_assistant_enable))
        }
        if (notSelected && !held) {
            Text(stringResource(R.string.workspace_assistant_not_selected), color = Amber, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { beforeLaunch(); openAssistantSettings() }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.phone_assistant_settings)) }
        }
        if (failed) ReadError(R.string.phone_assistant_unavailable)
    }
}

private fun isAssistant(context: Context): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    } else {
        val assistant = Settings.Secure.getString(context.contentResolver, "assistant")
        assistant?.let(ComponentName::unflattenFromString)?.packageName == context.packageName
    }
}.getOrDefault(false)

@RequiresApi(29)
internal fun requestRole(context: Context): Intent? = context.getSystemService(RoleManager::class.java)?.let {
    if (it.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) it.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT) else null
}
