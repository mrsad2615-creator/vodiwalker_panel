package ai.dana.data

import ai.dana.data.local.DanaDatabase
import ai.dana.data.local.PreferencesStore
import ai.dana.data.local.RoomHistoryRepository
import ai.dana.data.local.RoomMemoryRepository
import ai.dana.data.local.RoomNotesRepository
import ai.dana.data.provider.EngineRepository
import ai.dana.data.provider.KeystoreVault
import ai.dana.domain.ApiCredential
import ai.dana.domain.CredentialChange
import ai.dana.domain.CredentialStoreUnavailable
import ai.dana.domain.InputIssue
import ai.dana.domain.InputRejected
import ai.dana.domain.MessageRole
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.ProviderProfile
import ai.dana.domain.SendGrant
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun noteSavesEditsSurvivesReopenAndCanBeDeleted() = deviceTest {
        val name = "notes-test-${UUID.randomUUID()}.db"
        try {
            val id = Room.databaseBuilder(context, DanaDatabase::class.java, name).build().useDb { db ->
                val notes = RoomNotesRepository(db.notes())
                val newId = notes.save(null, " یادداشت اول ")
                assertEquals("یادداشت اول", notes.notes.first().single().text)
                assertEquals(newId, notes.save(newId, "یادداشت ویرایش‌شده"))
                newId
            }
            Room.databaseBuilder(context, DanaDatabase::class.java, name).build().useDb { db ->
                val notes = RoomNotesRepository(db.notes())
                val note = notes.notes.first().single()
                assertEquals(id, note.id)
                assertEquals("یادداشت ویرایش‌شده", note.text)
                notes.delete(id)
                assertTrue(notes.notes.first().isEmpty())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun messageOrderIsStableAndConversationRemovalCascades() = deviceTest {
        database().useDb { db ->
            val history = RoomHistoryRepository(db)
            val thread = history.create("پرسش")
            val first = history.append(thread, MessageRole.USER, "سلام")
            val second = history.append(thread, MessageRole.ASSISTANT, "درود")
            assertEquals(listOf(first, second), history.messages(thread).first().map { it.id })
            assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), history.messages(thread).first().map { it.role })
            history.delete(thread)
            assertTrue(history.conversations.first().isEmpty())
            assertTrue(history.messages(thread).first().isEmpty())
            expectInputIssue(InputIssue.RECORD_NOT_FOUND) { history.append(thread, MessageRole.USER, "نباید ثبت شود") }
        }
    }

    @Test
    fun completeChatExchangeSavesAtomicallyAndCanBeSearchedRenamedAndErased() = deviceTest {
        database().useDb { db ->
            val history = RoomHistoryRepository(db)
            val id = history.saveExchange(null, "جلسه", "تعطیلات شنبه", "برای شنبه آماده شوید")
            assertEquals(id, history.search("تعطیلات").first().single().id)
            assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), history.messages(id).first().map { it.role })
            expectInputIssue(InputIssue.INVALID_MESSAGE) { history.saveExchange(id, "جلسه", "نباید ذخیره شود", " ") }
            assertEquals(2, history.messages(id).first().size)
            history.rename(id, "سفر")
            assertEquals(id, history.search("سفر").first().single().id)
            history.clear()
            assertTrue(history.messages(id).first().isEmpty())
            assertTrue(history.search("تعطیلات").first().isEmpty())
        }
    }

    @Test
    fun localMemorySupportsEditingIndividualDeletionAndClearing() = deviceTest {
        database().useDb { db ->
            val memory = RoomMemoryRepository(db.memory())
            val first = memory.save(null, "ترجیح اول")
            val second = memory.save(null, "ترجیح دوم")
            memory.save(first, "ترجیح ویرایش‌شده")
            memory.delete(second)
            assertEquals("ترجیح ویرایش‌شده", memory.facts.first().single().fact)
            memory.clear()
            assertTrue(memory.facts.first().isEmpty())
        }
    }

    @Test
    fun offlineDefaultsToTrueAndSurvivesReopeningTheDataStore() = deviceTest {
        val file = File(context.cacheDir, "privacy-${UUID.randomUUID()}.preferences_pb")
        val job = Job()
        try {
            val store = PreferencesStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
            assertTrue(store.offlineMode.first())
            store.setOfflineMode(false)
            assertFalse(store.offlineMode.first())
            job.cancelAndJoin()

            val nextJob = Job()
            try {
                val reopened = PreferencesStore(
                    PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + nextJob)) { file },
                )
                assertFalse(reopened.offlineMode.first())
                reopened.setOfflineMode(true)
                assertTrue(reopened.offlineMode.first())
            } finally {
                nextJob.cancelAndJoin()
            }
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun activatedRoutePersistsAndDoesNotAuthorizeChangedCredentialsOrPhotos() = deviceTest {
        val file = File(context.cacheDir, "grants-${UUID.randomUUID()}.preferences_pb")
        val job = Job()
        val nextJob = Job()
        fun load(scope: Job) = PreferencesStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + scope)) { file })
        val profile = ProviderProfile("my-engine", config("https://original.example.com/v1"), true, "v1.fixture")
        fun grant(profile: ProviderProfile, photo: Boolean = false) = SendGrant.chat(listOf(profile), false, true, false, photo, true)
        try {
            val store = load(job)
            assertFalse(store.permits(grant(profile)))
            store.activate(profile, allowDirectText = true)
            assertFalse(store.offlineMode.first())
            assertEquals(profile.id, store.preferredEngine.first())
            job.cancelAndJoin()
            val next = load(nextJob)
            assertTrue(next.permits(grant(profile)))
            assertFalse(next.permits(grant(profile.copy(credentialRevision = "v1.reissued"))))
            assertFalse(next.permits(grant(profile, photo = true)))
            next.forgetAll()
            assertFalse(next.permits(grant(profile)))
        } finally {
            job.cancelAndJoin()
            nextJob.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun profileUpdateIsAtomicAndRequiresExplicitKeyChangeForNewDestination() = deviceTest {
        withVault { vault ->
            database().useDb { db ->
                val repo = EngineRepository(db, vault)
                val original = config("https://original.example.com/v1")
                val secret = ApiCredential.parse("device-test-only-secret")
                val id = repo.save(null, original, CredentialChange.Replace(secret))
                val profile = repo.profiles.first().single()
                assertTrue(profile.hasCredential)
                assertFalse(profile.toString().contains(secret.value))
                val raw = db.engines().find(id)?.sealedKey
                assertNotNull(raw)
                assertFalse(raw.orEmpty().contains(secret.value))
                assertEquals(secret.value, repo.credentialFor(profile)?.value)
                val changed = config("https://different.example.com/v1")
                expectInputIssue(InputIssue.CREDENTIAL_DESTINATION_CHANGED) { repo.save(id, changed, CredentialChange.Keep) }
                assertEquals(profile, repo.profiles.first().single())
                assertEquals(raw, db.engines().find(id)?.sealedKey)
                repo.save(id, original, CredentialChange.Replace(ApiCredential.parse("rotated-device-test-only")))
                val rotated = repo.profiles.first().single()
                assertNotEquals(profile.credentialRevision, rotated.credentialRevision)
                expectInputIssue(InputIssue.RECORD_NOT_FOUND) { repo.credentialFor(profile) }
                repo.save(id, changed, CredentialChange.Remove)
                val updated = repo.profiles.first().single()
                assertFalse(updated.hasCredential)
                assertEquals(changed, updated.config)
                assertNull(repo.credentialFor(updated))
                expectInputIssue(InputIssue.RECORD_NOT_FOUND) { repo.credentialFor(profile) }
                repo.delete(id)
                assertTrue(repo.profiles.first().isEmpty())
            }
        }
    }

    @Test
    fun vaultUsesFreshIvAndRejectsWrongScopeProfileAndTamperedCiphertext() = deviceTest {
        withVault { vault ->
            val value = ApiCredential.parse("device-test-only-value")
            val scope = config("https://original.example.com/v1").credentialScope
            val first = vault.seal("profile-a", scope, value)
            val second = vault.seal("profile-a", scope, value)
            assertNotEquals(first.split('.')[1], second.split('.')[1])
            assertEquals(value.value, vault.open("profile-a", scope, first).value)
            assertEquals(value.value, vault.open("profile-a", scope, second).value)
            expectVaultFailure { vault.open("profile-b", scope, first) }
            expectVaultFailure { vault.open("profile-a", "$scope/changed", first) }
            val prefix = first.substringBeforeLast('.')
            val data = first.substringAfterLast('.')
            val flipped = (if (data.first() == 'A') 'B' else 'A') + data.drop(1)
            expectVaultFailure { vault.open("profile-a", scope, "$prefix.$flipped") }
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, DanaDatabase::class.java).build()

    private inline fun <T> DanaDatabase.useDb(block: (DanaDatabase) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private fun config(url: String) = ProviderConfig.create(
        "آزمون اندروید", ProviderProtocol.OPENAI_COMPATIBLE, ProviderNetwork.INTERNET, url, "model-id",
    )

    private fun deviceTest(block: suspend () -> Unit) {
        runBlocking { withTimeout(20_000) { block() } }
    }

    private suspend fun withVault(block: suspend (KeystoreVault) -> Unit) {
        val alias = "dana.instrumented.${UUID.randomUUID()}"
        try {
            block(KeystoreVault(alias))
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    private suspend fun expectInputIssue(issue: InputIssue, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected rejected input: $issue")
        } catch (error: InputRejected) {
            assertEquals(issue, error.issue)
        }
    }

    private suspend fun expectVaultFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected authentication failure")
        } catch (_: CredentialStoreUnavailable) {
            // The vault must not return a credential if envelope authentication fails.
        }
    }
}
