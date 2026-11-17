package dev.suks.midas.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Decides when the app should ask to be unlocked. Leaving the app briefly (to pay in a UPI app,
 * pick a file or grant a permission) doesn't re-lock it; staying away longer than [graceMs] does.
 */
class AppLock(private val graceMs: Long = GRACE_MS, private val now: () -> Long = System::currentTimeMillis) {
    /** Starts locked so a cold start always asks. */
    var locked: Boolean = true
        private set
    private var stoppedAt: Long? = null

    fun onStop() { if (!locked) stoppedAt = now() }

    /** Called when the app comes back; returns whether it should now be locked. */
    fun onStart(enabled: Boolean): Boolean {
        if (!enabled) locked = false
        else if (stoppedAt?.let { now() - it > graceMs } == true) locked = true
        stoppedAt = null
        return locked
    }

    fun unlock() { locked = false; stoppedAt = null }

    companion object {
        const val GRACE_MS = 60_000L

        /** Fingerprint/face, falling back to the phone's PIN, pattern or password. */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        fun available(activity: FragmentActivity): Boolean =
            BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

        /** Shows the system unlock sheet. [onResult] gets true on success, false if cancelled or failed. */
        fun prompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
            val prompt = BiometricPrompt(
                activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint, face or screen lock")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
        }
    }
}
