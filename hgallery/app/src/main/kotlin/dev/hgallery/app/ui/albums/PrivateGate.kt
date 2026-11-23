package dev.hgallery.app.ui.albums

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Whether this device can ask for the device lock.
 *
 * ## What this is not
 *
 * This is not encryption. A private album hides its tile behind the device lock and keeps
 * its contents out of the timeline, the search results and the system picker's view of
 * this app. The media files themselves stay in the same folders, readable by any app that
 * holds media permission. The screen says that in words, because a lock icon on its own
 * invites the stronger assumption.
 *
 * ## Why the credential fallback is required
 *
 * A device with no fingerprint enrolled still has a PIN, and a user who has not enrolled
 * a finger should not lose the feature. [BIOMETRIC_WEAK or DEVICE_CREDENTIAL] asks for
 * whichever the device has, and the platform shows the right prompt.
 */
object PrivateGate {

    /** The authenticator combination the gate asks for. */
    const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether the device can authenticate at all.
     *
     * A device with neither a lock screen nor an enrolled biometric cannot protect an
     * album. The app must then refuse the feature rather than show a gate that cannot
     * be satisfied — a lock that opens for anyone is worse than no lock.
     */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the platform prompt.
     *
     * [activity] must be a [FragmentActivity]; the prompt is a fragment. The caller passes
     * its own activity, so the prompt is owned by the screen that asked for it and is
     * dismissed when that screen goes away.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // A cancel is not a failure to report: the user chose to stop. Every
                    // other error is reported, because silence would look like a lock that
                    // does nothing.
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        return
                    }
                    onFailure(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            // The negative button is deliberately absent: the prompt cannot be dismissed
            // by a button that also does nothing, and Back already cancels it.
            .setAllowedAuthenticators(ALLOWED)
            .build()
        prompt.authenticate(info)
    }
}
