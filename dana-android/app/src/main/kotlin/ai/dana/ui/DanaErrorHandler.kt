package ai.dana.ui

import ai.dana.R
import ai.dana.domain.CredentialStoreUnavailable
import ai.dana.domain.InputIssue
import ai.dana.domain.InputRejected
import android.database.sqlite.SQLiteException
import androidx.annotation.StringRes
import java.io.IOException

internal object DanaErrorHandler {
    @StringRes
    fun messageFor(error: Exception): Int = when (error) {
        is InputRejected -> when (error.issue) {
            InputIssue.EMPTY_NOTE -> R.string.error_note_empty
            InputIssue.NOTE_TOO_LONG -> R.string.error_note_long
            InputIssue.INVALID_CONVERSATION_TITLE, InputIssue.INVALID_MESSAGE, InputIssue.INVALID_MEMORY ->
                R.string.error_invalid_local_text
            InputIssue.INVALID_PROVIDER_NAME -> R.string.error_provider_name
            InputIssue.INVALID_MODEL -> R.string.error_model
            InputIssue.INVALID_BASE_URL -> R.string.error_url
            InputIssue.INVALID_API_KEY -> R.string.error_api_key
            InputIssue.CREDENTIAL_DESTINATION_CHANGED -> R.string.error_credential_destination
            InputIssue.RECORD_NOT_FOUND -> R.string.error_record_missing
        }
        is CredentialStoreUnavailable -> R.string.error_vault
        is SQLiteException, is IOException -> R.string.error_local_io
        else -> R.string.error_generic
    }
}
