package ai.dana.data.account

import ai.dana.data.provider.KeystoreVault
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountIdentity
import ai.dana.domain.AccountInput
import ai.dana.domain.AccountProblem
import ai.dana.domain.AccountSetup
import ai.dana.domain.ApiCredential
import ai.dana.domain.CredentialStoreUnavailable
import ai.dana.domain.EmailAccounts
import ai.dana.domain.EmailProject
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.SignOutResult
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.emailSettings by preferencesDataStore("dana_email_account")

internal class StoredEmailAccounts(
    context: Context,
    private val fixed: EmailProject?,
    private val privacy: PrivacyPreferences,
    private val api: SupabaseEmailApi,
    private val store: DataStore<Preferences> = context.applicationContext.emailSettings,
    private val vault: KeystoreVault = KeystoreVault("dana.account.refresh.v1"),
) : EmailAccounts {
    private val lock = Mutex()
    private val projectUrl = stringPreferencesKey("project_url")
    private val projectKey = stringPreferencesKey("project_publishable_key")
    private val refresh = stringPreferencesKey("protected_refresh")
    private val sessionOrigin = stringPreferencesKey("session_project_url")

    private fun project(data: Preferences): EmailProject? = fixed ?: EmailProject.parse(data[projectUrl].orEmpty(), data[projectKey].orEmpty())

    override val setup = store.data.map { data ->
        val project = project(data)
        AccountSetup(project?.host, fixed == null, project != null && data[sessionOrigin] == project.origin && data[refresh] != null)
    }.distinctUntilChanged()

    override suspend fun configure(origin: String, publishableKey: String) = lock.withLock {
        if (fixed != null) throw AccountFailure(AccountProblem.SETUP)
        val newProject = EmailProject.parse(origin, publishableKey) ?: throw AccountFailure(AccountProblem.SETUP)
        store.edit { data ->
            val prior = project(data)
            if (prior == null || prior.origin != newProject.origin || prior.publishableKey != newProject.publishableKey) {
                data.remove(refresh)
                data.remove(sessionOrigin)
            }
            data[projectUrl] = newProject.origin
            data[projectKey] = newProject.publishableKey
        }
        Unit
    }

    override suspend fun removeProject() = lock.withLock {
        if (fixed != null) throw AccountFailure(AccountProblem.SETUP)
        store.edit { it.clear() }
        Unit
    }

    override suspend fun requestCode(email: String) = lock.withLock {
        val to = AccountInput.email(email) ?: throw AccountFailure(AccountProblem.INVALID_EMAIL)
        val project = requireProject(store.data.first())
        online { api.send(project, to) }
    }

    override suspend fun verify(email: String, code: String): AccountIdentity = lock.withLock {
        val to = AccountInput.email(email) ?: throw AccountFailure(AccountProblem.INVALID_EMAIL)
        val otp = AccountInput.otp(code) ?: throw AccountFailure(AccountProblem.INVALID_CODE)
        val project = requireProject(store.data.first())
        // The UI cannot mark an address verified from a request to send a code.
        online { keep(project, api.verify(project, to, otp)) }
    }

    override suspend fun resume(): AccountIdentity = lock.withLock {
        val state = store.data.first()
        val project = requireProject(state)
        val token = readRefresh(state, project) ?: throw AccountFailure(AccountProblem.EXPIRED_SESSION)
        try {
            online { keep(project, api.refresh(project, token)) }
        } catch (fault: AccountFailure) {
            if (fault.problem == AccountProblem.EXPIRED_SESSION) clearRefresh()
            throw fault
        }
    }

    override suspend fun signOut(): SignOutResult = lock.withLock {
        val state = store.data.first()
        val project = project(state)
        val token = try { project?.let { readRefresh(state, it) } } catch (_: CredentialStoreUnavailable) { null }
        clearRefresh() // Local sign-out works even if the network or Keystore is unavailable.
        if (project == null || token == null) return@withLock SignOutResult.LOCAL_ONLY
        try {
            online { api.revoke(project, api.refresh(project, token).access) }
            SignOutResult.REVOKED
        } catch (_: AccountFailure) {
            SignOutResult.LOCAL_ONLY
        }
    }

    private suspend fun clearRefresh() {
        store.edit { it.remove(refresh); it.remove(sessionOrigin) }
    }

    private suspend fun readRefresh(data: Preferences, project: EmailProject): ApiCredential? =
        data[refresh]?.takeIf { data[sessionOrigin] == project.origin }?.let { vault.open(ENTRY, scope(project), it) }

    private suspend fun keep(project: EmailProject, session: VerifiedSession): AccountIdentity {
        // Finish the small encrypted write if a refresh token was already rotated by the server.
        withContext(NonCancellable) {
            val sealed = vault.seal(ENTRY, scope(project), session.refresh)
            store.edit { it[refresh] = sealed; it[sessionOrigin] = project.origin }
        }
        return AccountIdentity(session.email)
    }

    private fun scope(project: EmailProject) = "${project.origin}\u0000${project.publishableKey}"
    private fun requireProject(data: Preferences) = project(data) ?: throw AccountFailure(AccountProblem.SETUP)

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun <T> online(action: suspend () -> T): T = privacy.offlineMode
        .catch { if (it is CancellationException || it !is Exception) throw it else emit(true) }
        .distinctUntilChanged()
        .transformWhile { offline -> emit(offline); !offline }
        .mapLatest { if (it) throw AccountFailure(AccountProblem.OFFLINE) else action() }
        .first()

    private companion object { const val ENTRY = "email-refresh" }
}
