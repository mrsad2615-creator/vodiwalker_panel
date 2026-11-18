package ai.dana.ui

import ai.dana.domain.ApiCredential
import ai.dana.domain.ArtImage
import ai.dana.domain.ArtModel
import ai.dana.domain.ArtRequest
import ai.dana.domain.Conversation
import ai.dana.domain.ConversationInput
import ai.dana.domain.ConversationMessage
import ai.dana.domain.CredentialChange
import ai.dana.domain.DanaAiRouter
import ai.dana.domain.DirectSendSettings
import ai.dana.domain.EngineText
import ai.dana.domain.EngineTransport
import ai.dana.domain.HistoryRepository
import ai.dana.domain.ImageGeneration
import ai.dana.domain.ImageTransport
import ai.dana.domain.LocalAction
import ai.dana.domain.MemoryRepository
import ai.dana.domain.MessageRole
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderRepository
import ai.dana.domain.QuickService
import ai.dana.domain.SendGrant
import ai.dana.domain.UserMemory
import ai.dana.domain.VisionPhoto
import ai.dana.localai.DeviceModels
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.provider.AlarmClock
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Installed app ViewModels and Android IO, driven by test-only transport fixtures, not a fake shipping mode. */
@RunWith(AndroidJUnit4::class)
class InstalledWorkflowsTest {
    private val google = QuickService.GOOGLE
    private val profile = ProviderProfile("trial", ProviderConfig.create(google.title, google.protocol,
        ProviderNetwork.INTERNET, google.api, google.models.first()), true, "fixture-iv")
    private val repo = object : ProviderRepository {
        override val profiles = flowOf(listOf(profile))
        override suspend fun credentialFor(profile: ProviderProfile) = ApiCredential.parse("test-only")
        override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = error("Fixture is read only")
        override suspend fun delete(id: String) = error("Fixture is read only")
    }
    private val prefs = object : PrivacyPreferences {
        override val offlineMode = MutableStateFlow(false)
        override suspend fun setOfflineMode(enabled: Boolean) { offlineMode.value = enabled }
    }
    private val settings = object : DirectSendSettings {
        private val grants = mutableSetOf<SendGrant>()
        override val preferredEngine = flowOf(profile.id)
        override suspend fun permits(grant: SendGrant) = grant in grants
        override suspend fun remember(grant: SendGrant) { grants += grant }
        override suspend fun forgetAll() { grants.clear() }
        override suspend fun activate(profile: ProviderProfile, allowDirectText: Boolean) = error("Not used")
    }

    @Test fun chatRequiresFirstApprovalButNextPressSendsExactlyOnceAndNewPhotoRequiresItsOwnApproval() = deviceTest {
        val sent = mutableListOf<ConversationInput>()
        val router = DanaAiRouter(prefs, repo, object : EngineTransport {
            override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText> {
                sent += input
                return flowOf(EngineText("پاسخ آزمایشی"))
            }
        })
        val vm = ChatViewModel(router, unusedHistory, unusedMemory, settings, DeviceModels(InstrumentationRegistry.getInstrumentation().targetContext))
        onMain { vm.select(ChatDestination.Saved(profile.id)) }
        fun send(text: String) = onMain {
            vm.draft(text)
            vm.submit(listOf(profile), offline = false)
        }
        send("پرسش اول")
        vm.state.first { !it.busy && it.dialog is ChatDialog.Send }
        assertTrue(sent.isEmpty())
        onMain { vm.approve(offline = false) }
        vm.state.first { !it.busy && it.turns.singleOrNull()?.complete == true }
        send("پرسش دوم")
        val done = vm.state.first { !it.busy && it.turns.size == 2 && it.turns.last().complete }
        assertNull(done.dialog)
        assertEquals(listOf("پرسش اول", "پرسش دوم"), sent.map { it.prompt })
        assertEquals(2, sent.last().history.size)
        onMain { vm.photo(VisionPhoto.fromJpeg(byteArrayOf(-1, -40, 1, -1, -39))) }
        send("این عکس چیست؟")
        vm.state.first { !it.busy && it.dialog is ChatDialog.Send }
        assertEquals(2, sent.size)
        onMain { vm.background() }
        assertNull(vm.state.value.dialog)
    }

    @Test fun restoredLocalArchiveNeverBecomesCloudContext() = deviceTest {
        val local = Conversation(7, "ذخیرهٔ محلی", 10L)
        val archive = object : HistoryRepository by unusedHistory {
            override fun messages(id: Long) = flowOf(listOf(
                ConversationMessage(1, local.id, MessageRole.USER, "PRIVATE_ON_DEVICE", 1L),
                ConversationMessage(2, local.id, MessageRole.ASSISTANT, "LOCAL_ONLY", 2L),
            ))
        }
        val transport = object : EngineTransport {
            override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText> =
                error("Should not send without approval")
        }
        val vm = ChatViewModel(DanaAiRouter(prefs, repo, transport), archive, unusedMemory, settings,
            DeviceModels(InstrumentationRegistry.getInstrumentation().targetContext))
        onMain { vm.open(local) }
        vm.state.first { !it.busy && it.turns.size == 1 }
        onMain {
            vm.select(ChatDestination.Saved(profile.id))
            vm.draft("پرسش آنلاین")
            vm.submit(listOf(profile), offline = false)
        }
        val review = vm.state.first { !it.busy && it.dialog is ChatDialog.Send }.dialog as ChatDialog.Send
        assertEquals("پرسش آنلاین", review.route.input.prompt)
        assertTrue(review.route.input.history.isEmpty())
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun artworkPipelinePreviewsNativePngAndWritesExactBytesToUserSelectableContentUri() = deviceTest {
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff547fa1.toInt()) }
        val bytes = ByteArrayOutputStream().also { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
        bitmap.recycle()
        val png = checkNotNull(ArtImage.create("image/png", bytes))
        var sent = 0
        val vm = ArtViewModel(ImageGeneration(prefs, repo, object : ImageTransport {
            override suspend fun generate(request: ArtRequest, key: ApiCredential): ArtImage {
                assertEquals("گلدان کوچک", request.prompt)
                assertEquals(ArtModel.NANO_LITE, request.model)
                sent++
                return png
            }
        }), settings)
        onMain { vm.prompt("گلدان کوچک"); vm.submit(ArtModel.NANO_LITE, profile) }
        vm.state.first { it.pending != null && !it.running }
        assertEquals(0, sent)
        onMain { vm.approve() }
        val preview = checkNotNull(vm.state.first { !it.running && it.preview != null }.preview)
        assertEquals(16, preview.width)
        assertEquals(12, preview.height)
        assertEquals(1, sent)

        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "mrsad-ci-${System.nanoTime()}.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
        }))
        try {
            writeDocument(resolver, uri, preview.image::writeTo)
            assertArrayEquals(bytes, resolver.openInputStream(uri)?.use { it.readBytes() })
        } finally {
            resolver.delete(uri, null, null)
        }
        onMain { vm.submit(ArtModel.NANO_LITE, profile) }
        val repeated = vm.state.first { it.preview?.id != preview.id && !it.running }
        assertNull(repeated.pending)
        assertEquals(2, sent)
    }

    @Test fun clockIntentsAreTypedBoundedAndMissingClockReturnsHelpfulStatus() {
        val timer = checkNotNull(DeviceActions.clockIntent(LocalAction.Timer(120)))
        assertEquals(AlarmClock.ACTION_SET_TIMER, timer.action)
        assertEquals(120, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertEquals(false, timer.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
        val alarm = checkNotNull(DeviceActions.clockIntent(LocalAction.Alarm(7, 30)))
        assertEquals(AlarmClock.ACTION_SET_ALARM, alarm.action)
        assertEquals(7, alarm.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, alarm.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals(false, alarm.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
        assertNull(DeviceActions.clockIntent(LocalAction.Timer(0)))
        assertNull(DeviceActions.clockIntent(LocalAction.Timer(86_401)))
        assertNull(DeviceActions.clockIntent(LocalAction.Alarm(24, 0)))
        val noClock = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        val failed = DeviceActions.execute(noClock, LocalAction.Timer(60))
        assertEquals(false, failed.ok)
        assertEquals(ai.dana.R.string.phone_clock_unavailable, failed.message)
    }

    @Test fun scheduledCalendarUsesAndroidInsertFormAndReportContainsOriginalPromptAndLaneMetrics() {
        val form = checkNotNull(DeviceActions.calendarIntent(LocalAction.CalendarDraft("جلسه", LocalAction.CalendarStart(1, 18, 30))))
        assertEquals(Intent.ACTION_INSERT, form.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, form.data)
        assertEquals("جلسه", form.getStringExtra(CalendarContract.Events.TITLE))
        val begin = form.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0)
        assertEquals(18, Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).hour)
        assertEquals(1_800_000L, form.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0) - begin)
        val report = comparisonReport(ComparisonRun("fixture", Instant.EPOCH, ai.dana.domain.AssistantTask.ASK, "پرامپت اصلی"),
            listOf(EngineLane(profile, answer = "پاسخ آزمون", status = LaneStatus.COMPLETE, firstTextMillis = 41, elapsedMillis = 52)))
        assertTrue(listOf("پرامپت اصلی", "پاسخ آزمون", profile.config.model, "41 ms", "52 ms").all { it in report })
        assertTrue("fixture-iv" !in report)
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    private fun deviceTest(test: suspend () -> Unit) { runBlocking { withTimeout(10_000) { test() } } }

    private val unusedMemory = object : MemoryRepository {
        override val facts = flowOf(emptyList<UserMemory>())
        override suspend fun save(id: Long?, fact: String): Long = error("No memory write")
        override suspend fun delete(id: Long) = error("No memory write")
        override suspend fun clear() = error("No memory write")
    }
    private val unusedHistory = object : HistoryRepository {
        override val conversations = flowOf(emptyList<Conversation>())
        override fun messages(conversationId: Long) = flowOf(emptyList<ConversationMessage>())
        override suspend fun create(title: String): Long = error("No history write")
        override suspend fun append(conversationId: Long, role: MessageRole, body: String): Long = error("No history write")
        override suspend fun saveExchange(conversationId: Long?, title: String, user: String, answer: String): Long = error("No history write")
        override suspend fun rename(conversationId: Long, title: String) = error("No history write")
        override suspend fun delete(conversationId: Long) = error("No history write")
        override suspend fun clear() = error("No history write")
        override fun search(text: String) = flowOf(emptyList<Conversation>())
    }
}
