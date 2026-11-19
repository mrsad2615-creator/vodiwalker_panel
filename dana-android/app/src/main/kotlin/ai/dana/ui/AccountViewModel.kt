package ai.dana.ui

import ai.dana.R
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountIdentity
import ai.dana.domain.AccountInput
import ai.dana.domain.AccountProblem
import ai.dana.domain.AccountSetup
import ai.dana.domain.EmailAccounts
import ai.dana.domain.SignOutResult
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class PendingCode(val email: String, val resendAtMs: Long)
internal data class AccountScreenState(
    val pending: PendingCode? = null,
    val identity: AccountIdentity? = null,
    val busy: Boolean = false,
    val problem: AccountProblem? = null,
    val signOut: SignOutResult? = null,
)

@HiltViewModel
internal class AccountViewModel @Inject constructor(private val accounts: EmailAccounts) : ViewModel() {
    private val mutable = MutableStateFlow(AccountScreenState())
    val state = mutable.asStateFlow()
    val setup = accounts.setup.map<AccountSetup, Reading<AccountSetup>> { Reading.Ready(it) }
        .catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            emit(Reading.Failed(R.string.account_error_local))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Reading.Loading)
    private var work: Job? = null

    @MainThread
    fun configure(origin: String, publishable: String, done: () -> Unit) = run {
        accounts.configure(origin, publishable)
        mutable.update { AccountScreenState(busy = true) }
        done()
    }

    @MainThread
    fun removeProject() = run {
        accounts.removeProject()
        mutable.update { AccountScreenState(busy = true) }
    }

    @MainThread
    fun requestCode(rawEmail: String) {
        val to = AccountInput.email(rawEmail) ?: return fail(AccountProblem.INVALID_EMAIL)
        val pending = mutable.value.pending
        if (pending != null && pending.email.equals(to, true) && pending.resendAtMs > SystemClock.elapsedRealtime()) return
        run {
            accounts.requestCode(to)
            mutable.update { it.copy(pending = PendingCode(to, SystemClock.elapsedRealtime() + 60_000L)) }
        }
    }

    @MainThread
    fun verify(code: String, onEntered: (() -> Unit)? = null) {
        val pending = mutable.value.pending ?: return
        val clean = AccountInput.otp(code) ?: return fail(AccountProblem.INVALID_CODE)
        run {
            val verified = accounts.verify(pending.email, clean)
            mutable.update { it.copy(identity = verified, pending = null) }
            onEntered?.invoke()
        }
    }

    @MainThread
    fun changeEmail() { if (!mutable.value.busy) mutable.update { it.copy(pending = null, problem = null) } }

    @MainThread
    fun resume(onEntered: (() -> Unit)? = null) = run {
        val verified = accounts.resume()
        mutable.update { it.copy(identity = verified, pending = null) }
        onEntered?.invoke()
    }

    @MainThread
    fun signOut() = run {
        val result = accounts.signOut()
        mutable.update { it.copy(identity = null, pending = null, signOut = result) }
    }

    @MainThread
    fun clearProblem() { if (!mutable.value.busy) mutable.update { it.copy(problem = null, signOut = null) } }

    @MainThread
    fun background() { work?.cancel() }

    private fun fail(problem: AccountProblem) { mutable.update { it.copy(problem = problem) } }

    private fun run(operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, problem = null, signOut = null) }
        work = viewModelScope.launch {
            try {
                operation()
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: AccountFailure) {
                fail(error.problem)
            } catch (_: Exception) {
                fail(AccountProblem.LOCAL)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
