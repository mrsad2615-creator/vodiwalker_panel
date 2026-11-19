package ai.dana.ui

import ai.dana.R
import ai.dana.domain.AccountInput
import ai.dana.domain.AccountProblem
import ai.dana.domain.AccountSetup
import ai.dana.domain.EmailProject
import ai.dana.domain.SignOutResult
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun AccountScreen(model: AccountViewModel, offline: Boolean, canAllow: Boolean, onAllow: () -> Unit,
    managed: Boolean = false, onEntered: (() -> Unit)? = null,
) {
    val account by model.state.collectAsStateWithLifecycle()
    val status by model.setup.collectAsStateWithLifecycle()
    var configure by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var signOut by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    val setup = (status as? Reading.Ready)?.value
    val host = setup?.host

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionIntro(R.string.account_title, if (managed) R.string.managed_email_help else R.string.account_blurb)
        if (account.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!configure) account.problem?.let { ReadError(accountMessage(it)) }
        account.signOut?.let { Text(stringResource(if (it == SignOutResult.REVOKED) R.string.account_signed_out else R.string.account_signed_out_local), color = Titanium) }

        when {
            status == Reading.Loading -> Loading()
            status is Reading.Failed -> ReadError(R.string.account_error_local)
            setup == null || host == null -> GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_email_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.account_unconfigured), color = Titanium)
                if (setup?.editable == true && !managed) Button(onClick = { model.clearProblem(); configure = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !account.busy) {
                    Text(stringResource(R.string.account_configure))
                }
            }
            else -> {
                val current = account.identity
                val pending = account.pending
                GlassCard(Modifier.fillMaxWidth()) {
                    if (!managed) {
                        Text(stringResource(R.string.account_host), style = MaterialTheme.typography.labelMedium, color = Titanium)
                        TechnicalText(host)
                    }
                    when {
                        current != null -> {
                            Text(stringResource(R.string.account_verified), style = MaterialTheme.typography.titleLarge, color = Mint)
                            TechnicalText(current.email, maxLines = 4)
                            if (managed && onEntered != null) Button(onClick = { model.resume(onEntered) }, enabled = !offline && !account.busy) {
                                Text(stringResource(R.string.account_continue))
                            }
                            TextButton(enabled = !account.busy, onClick = { signOut = true }) { Text(stringResource(R.string.account_logout)) }
                        }
                        setup.canResume -> {
                            Text(stringResource(R.string.account_saved), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { model.resume(onEntered) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !offline && !account.busy) {
                                Text(stringResource(R.string.account_continue))
                            }
                            TextButton(enabled = !account.busy, onClick = { signOut = true }) { Text(stringResource(R.string.account_forget)) }
                        }
                        pending != null -> CodeForm(pending, !offline && !account.busy, model, onEntered)
                        else -> {
                            Text(stringResource(R.string.account_email_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.account_send_help), color = Titanium)
                            AccountField(email, { email = it.take(AccountInput.EMAIL_LIMIT) }, R.string.account_email, KeyboardType.Email, !account.busy)
                            Button(onClick = { model.requestCode(email) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                enabled = !offline && !account.busy && AccountInput.email(email) != null) {
                                Text(stringResource(R.string.account_send))
                            }
                        }
                    }
                }
                if (offline && current == null) OfflinePanel(R.string.account_offline_help, canAllow && !account.busy, onAllow)
                if (current == null && !setup.canResume && pending == null && setup.editable && !managed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { model.clearProblem(); configure = true }, enabled = !account.busy) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = { remove = true }, enabled = !account.busy) { Text(stringResource(R.string.account_remove_config)) }
                    }
                }
            }
        }
        Text(stringResource(if (managed) R.string.managed_account_boundary else R.string.account_data_boundary), style = MaterialTheme.typography.bodySmall, color = Titanium)
    }

    if (configure) ProjectDialog(setup, account, { if (!account.busy) { configure = false; model.clearProblem() } }, model) { configure = false }
    if (remove) AlertDialog(
        onDismissRequest = { remove = false }, title = { Text(stringResource(R.string.account_remove_config)) },
        text = { Text(stringResource(R.string.account_remove_explain)) },
        confirmButton = { TextButton({ remove = false; model.removeProject() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ remove = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (signOut) AlertDialog(
        onDismissRequest = { signOut = false }, title = { Text(stringResource(R.string.account_logout)) },
        text = { Text(stringResource(R.string.account_logout_explain)) },
        confirmButton = { TextButton({ signOut = false; model.signOut() }) { Text(stringResource(R.string.account_logout)) } },
        dismissButton = { TextButton({ signOut = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CodeForm(pending: PendingCode, enabled: Boolean, model: AccountViewModel, onEntered: (() -> Unit)?) {
    var code by remember(pending) { mutableStateOf("") }
    var remain by remember(pending) { mutableLongStateOf(60L) }
    LaunchedEffect(pending) {
        do {
            remain = ((pending.resendAtMs - SystemClock.elapsedRealtime() + 999L) / 1000L).coerceAtLeast(0L)
            if (remain > 0) delay(500)
        } while (remain > 0)
    }
    Text(stringResource(R.string.account_verify_prompt), style = MaterialTheme.typography.titleLarge)
    TechnicalText(pending.email)
    Text(stringResource(R.string.account_inbox), color = Titanium)
    AccountField(code, { code = it.take(10) }, R.string.account_code, KeyboardType.NumberPassword, enabled)
    Button(onClick = { model.verify(code, onEntered) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled && AccountInput.otp(code) != null) { Text(stringResource(R.string.account_verify)) }
    TextButton(onClick = { model.requestCode(pending.email) }, enabled = enabled && remain == 0L) {
        Text(if (remain == 0L) stringResource(R.string.account_resend) else stringResource(R.string.account_resend_wait, faDigits(remain)))
    }
    TextButton(onClick = model::changeEmail, enabled = enabled) { Text(stringResource(R.string.account_change_email)) }
}

@Composable
private fun ProjectDialog(setup: AccountSetup?, state: AccountScreenState, dismiss: () -> Unit, model: AccountViewModel, done: () -> Unit) {
    var origin by remember { mutableStateOf(setup?.host?.let { "https://$it" }.orEmpty()) }
    var publicKey by remember { mutableStateOf("") }
    val valid = EmailProject.parse(origin, publicKey) != null
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.account_configure)) },
        properties = DialogProperties(dismissOnBackPress = !state.busy, dismissOnClickOutside = !state.busy, securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_config_help))
                AccountField(origin, { origin = it.take(120) }, R.string.account_project_url, KeyboardType.Uri, !state.busy)
                AccountField(publicKey, { publicKey = it.take(120) }, R.string.account_public_key, KeyboardType.Ascii, !state.busy)
                state.problem?.let { ReadError(accountMessage(it)) }
            }
        },
        confirmButton = { TextButton({ model.configure(origin, publicKey, done) }, enabled = valid && !state.busy) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AccountField(value: String, update: (String) -> Unit, label: Int, keyboard: KeyboardType, enabled: Boolean) {
    OutlinedTextField(value, update, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text(stringResource(label)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        visualTransformation = if (keyboard == KeyboardType.NumberPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false))
}

private fun accountMessage(problem: AccountProblem): Int = when (problem) {
    AccountProblem.SETUP -> R.string.account_error_setup
    AccountProblem.OFFLINE -> R.string.account_offline_help
    AccountProblem.INVALID_EMAIL -> R.string.account_error_email
    AccountProblem.INVALID_CODE -> R.string.account_error_code
    AccountProblem.EXPIRED_SESSION -> R.string.account_error_session
    AccountProblem.RATE_LIMIT -> R.string.account_error_rate
    AccountProblem.UNAVAILABLE -> R.string.account_error_unavailable
    AccountProblem.NETWORK -> R.string.error_engine_network
    AccountProblem.TLS -> R.string.error_engine_tls
    AccountProblem.RESPONSE -> R.string.account_error_response
    AccountProblem.LOCAL -> R.string.account_error_local
}
