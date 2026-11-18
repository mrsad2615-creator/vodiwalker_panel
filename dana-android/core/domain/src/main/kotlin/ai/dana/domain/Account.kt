package ai.dana.domain

import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Only a Supabase project root and an explicitly public client key, never an admin credential. */
class EmailProject private constructor(val origin: String, val publishableKey: String) {
    val host: String get() = origin.removePrefix("https://")
    override fun toString(): String = "EmailProject($host)"

    companion object {
        private val originPattern = Regex("https://[a-z0-9]{20}\\.supabase\\.co")
        private val keyPattern = Regex("sb_publishable_[a-zA-Z0-9_-]{16,96}")

        fun parse(rawOrigin: String, rawKey: String): EmailProject? {
            val origin = rawOrigin.trim().removeSuffix("/").lowercase(Locale.ROOT)
            val key = rawKey.trim()
            if (!originPattern.matches(origin) || !keyPattern.matches(key)) return null
            return EmailProject(origin, key)
        }
    }
}

object AccountInput {
    const val EMAIL_LIMIT = 254
    const val OTP_LENGTH = 6
    private val dotAtom = Regex("[a-zA-Z0-9!#%&'*+/=?^_`{|}~$-]+(?:\\.[a-zA-Z0-9!#%&'*+/=?^_`{|}~$-]+)*")
    private val dnsLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun email(raw: String): String? {
        val clean = raw.trim()
        if (clean.length !in 3..EMAIL_LIMIT || clean.count { it == '@' } != 1) return null
        val name = clean.substringBefore('@')
        val domain = clean.substringAfter('@').lowercase(Locale.ROOT)
        val labels = domain.split('.')
        if (name.length > 64 || !dotAtom.matches(name) || labels.size < 2 || labels.any { !dnsLabel.matches(it) }) return null
        return "$name@$domain"
    }

    fun otp(raw: String): String? {
        val ascii = raw.trim().map { ch -> when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            else -> ch
        } }.joinToString("")
        return ascii.takeIf { it.length == OTP_LENGTH && it.all { ch -> ch in '0'..'9' } }
    }
}

enum class AccountProblem { SETUP, OFFLINE, INVALID_EMAIL, INVALID_CODE, EXPIRED_SESSION, RATE_LIMIT, UNAVAILABLE, NETWORK, TLS, RESPONSE, LOCAL }
class AccountFailure(val problem: AccountProblem) : Exception(problem.name)

data class AccountSetup(val host: String?, val editable: Boolean, val canResume: Boolean)
class AccountIdentity(val email: String) {
    override fun toString(): String = "AccountIdentity([redacted])"
}

enum class SignOutResult { REVOKED, LOCAL_ONLY }

interface EmailAccounts {
    val setup: Flow<AccountSetup>
    suspend fun configure(origin: String, publishableKey: String)
    suspend fun removeProject()
    suspend fun requestCode(email: String)
    suspend fun verify(email: String, code: String): AccountIdentity
    suspend fun resume(): AccountIdentity
    suspend fun signOut(): SignOutResult
}
