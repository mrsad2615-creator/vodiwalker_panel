package ai.dana.ui

import ai.dana.MainActivity
import ai.dana.MessageReviewActivity
import ai.dana.R
import ai.dana.data.di.EngineClientModule
import ai.dana.data.network.EngineClients
import ai.dana.domain.ArtModel
import ai.dana.domain.QuickService
import ai.dana.domain.ReviewMessage
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.MediaStore
import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real UI / DI / app storage with isolated, strict HTTP response fixtures in the test APK only. */
@OptIn(ExperimentalTestApi::class)
@UninstallModules(EngineClientModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StudioSmokeTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val calls = LinkedBlockingQueue<WireCall>()
    private val replies = LinkedBlockingQueue<WireReply>()
    @BindValue @JvmField val engines = EngineClients(OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val body = Buffer().apply { request.body?.writeTo(this) }.readUtf8()
        calls.add(WireCall(request.url.toString(), request.header("Authorization"), body))
        val reply = replies.poll() ?: error("Unexpected request in isolated UI test")
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(reply.code).message("Isolated fixture")
            .body(reply.body.toResponseBody(reply.mime.toMediaType())).build()
    }.build())

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    // Advance effects on the test clock rather than letting I/O completions drive layout off-main.
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>(effectContext = StandardTestDispatcher())

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun aFreshInstallRejectsBadKeyThenConnectsChatsAndSavesImageThroughUi() {
        visible(R.string.workspace_chat_title)
        compose.onNodeWithContentDescription(label(R.string.workspace_settings)).performClick()
        visible(R.string.workspace_connection)
        capture("connection")
        val setup = clickable(R.string.quick_title) or clickable(R.string.quick_add_other)
        waitFor(setup)
        compose.onNode(setup).assertIsDisplayed().assertIsEnabled().performClick()
        visible(R.string.quick_key)
        compose.onNodeWithText(label(R.string.quick_connect)).assertIsNotEnabled()
        enter(R.string.quick_key, BAD_KEY)
        replies.add(WireReply(401, """{"error":{"code":"invalid_api_key"}}"""))
        click(R.string.quick_connect)
        visible("HTTP 401")
        visible(R.string.error_engine_auth)
        checkCall("/chat/completions", BAD_KEY, "Reply with one short word: OK.", "\"stream\":false")
        assertNoMic()

        enter(R.string.quick_key, KEY)
        replies.add(WireReply(200, completion("OK")))
        click(R.string.quick_connect)
        visible(R.string.hub_begin_chat, scroll = true)
        checkCall("/chat/completions", KEY, "Reply with one short word: OK.", "\"max_completion_tokens\":256")
        assertTrue(calls.isEmpty())
        click(R.string.hub_begin_chat, scroll = true)
        click(R.string.models_picker)
        modelRow("saved-OPENAI_COMPATIBLE-gpt-5.4-nano").performClick()
        visible(R.string.workspace_ai_ready)
        click(R.string.workspace_fast)
        enter(R.string.chat_prompt, "پاسخ سریع آزمایشی")
        replies.add(WireReply(200, "data: {\"choices\":[{\"delta\":{\"content\":\"پاسخ دریافت شد.\"}}]}\n\ndata: [DONE]\n\n", "text/event-stream"))
        click(R.string.workspace_send)
        visible("پاسخ دریافت شد.")
        checkCall("/chat/completions", KEY, "پاسخ سریع آزمایشی", "\"stream\":true", "\"max_completion_tokens\":448")
        capture("chat")

        click(R.string.tab_image)
        val draftModel = "${QuickService.AVALAI.title} · ${ArtModel.AVAL_IMAGE.title}"
        waitFor(hasText(draftModel) and hasClickAction())
        compose.onNodeWithText(draftModel).performClick()
        click(R.string.art_connect)
        val chooseDraft = context.getString(R.string.art_select_named, ArtModel.AVAL_IMAGE.title)
        visible(chooseDraft, scroll = true)
        compose.onNodeWithText(chooseDraft).performClick()
        visible(ArtModel.AVAL_STUDIO.title)
        compose.onNodeWithText(ArtModel.AVAL_STUDIO.title).performClick()
        enter(R.string.quick_key, KEY, scroll = true)
        // A listed Flare does not validate a Sunburst selection. No generation request is made during setup.
        replies.add(WireReply(200, """{"data":[{"id":"${ArtModel.AVAL_IMAGE.id}"}]}"""))
        click(R.string.quick_connect)
        visible(R.string.error_engine_config)
        checkCall("/models", KEY)
        replies.add(WireReply(200, """{"data":[{"id":"${ArtModel.AVAL_STUDIO.id}"}]}"""))
        click(R.string.quick_connect)
        visible(R.string.workspace_image_key_ok)
        checkCall("/models", KEY)
        visible("${QuickService.AVALAI.title} · ${ArtModel.AVAL_STUDIO.title}")
        assertTrue(calls.isEmpty())
        enter(R.string.art_prompt, "کوه بنفش در سپیده دم")
        click(R.string.art_landscape, scroll = true)
        click(R.string.art_generate)
        visible(R.string.art_allow_title)
        assertTrue(calls.isEmpty())
        val original = tinyPng()
        replies.add(WireReply(200, """{"data":[{"b64_json":"${Base64.encodeToString(original, Base64.NO_WRAP)}"}]}"""))
        click(R.string.send_allow)
        visible(R.string.art_ready)
        compose.onNodeWithContentDescription(label(R.string.art_preview)).assertIsDisplayed()
        checkCall("/images/generations", KEY, "کوه بنفش در سپیده دم", ArtModel.AVAL_STUDIO.id, "1536x1024", "\"quality\":\"high\"")
        capture("art")

        // Stub only the picker result (not the click or write). Android writes the original bytes to a real content URI.
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mrsad-ui-fixture.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }))
        val document = allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasType("image/png"), hasExtra(Intent.EXTRA_TITLE, "mrsad-image.png"))
        Intents.init()
        try {
            Intents.intending(document).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            click(R.string.art_save, scroll = true)
            visible(R.string.export_saved, scroll = true)
            Intents.intended(document)
            assertArrayEquals(original, resolver.openInputStream(uri)?.use { it.readBytes() })
        } finally {
            Intents.release()
            resolver.delete(uri, null, null)
        }
        assertTrue(calls.isEmpty())
        click(R.string.art_generate)
        visible(R.string.art_allow_title) // Studio never reuses a previous generation approval.
        click(R.string.cancel)
        assertTrue(calls.isEmpty())
        assertNoMic()
        trace("UI passed: saved key / fast SSE, exact Sunburst catalog, high-quality PNG consent and original export")
        reviewPastedMessageOnline()
    }

    @Test
    fun assistantIgnoresExtrasAndPhoneCommandsWorkWithoutAnyPermissions() {
        val assist = Intent(Intent.ACTION_ASSIST).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(assist, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.name == MainActivity::class.java.name })
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(assist.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Intent.EXTRA_ASSIST_CONTEXT, Bundle().apply { putString("fixture", "note DO_NOT_IMPORT") }))
        }
        visible(R.string.workspace_phone_title)
        compose.onNodeWithText("note DO_NOT_IMPORT").assertDoesNotExist()
        assertNoMic()
        val battery = checkNotNull(context.getSystemService(BatteryManager::class.java))
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        assertTrue(percent in 0..100)
        val result = context.getString(if (battery.isCharging) R.string.phone_battery_charging else R.string.phone_battery_result, faDigits(percent))
        click(R.string.phone_battery)
        visible(result)
        enter(R.string.workspace_phone_prompt, "فرمان ناشناس محلی", scroll = true)
        click(R.string.workspace_run, scroll = true)
        assertTrue(compose.onAllNodes(hasText(label(R.string.workspace_unrecognized))).fetchSemanticsNodes().isNotEmpty())
        enter(R.string.workspace_phone_prompt, "شارژ گوشی چقدره؟", scroll = true)
        click(R.string.workspace_run, scroll = true)
        visible(result, scroll = true)
        capture("phone")
        click(R.string.workspace_access, scroll = true)
        visible(R.string.workspace_mic_off, scroll = true)
        visible(R.string.workspace_android_permissions, scroll = true)
        assertNoMic()
        assertTrue(calls.isEmpty())
        trace("UI passed: optional permissions not granted, local battery typed / tap, unknown command, settings recovery entry")
    }

    @Test
    fun sharedTextIsOnlyADraftAndPhoneRequiresReviewForTypedNotes() {
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON))
        val source = "لطفاً گزارش جلسه را فردا ساعت ۱۸:۳۰ آماده کنید. note DANA_SMOKE_LOCAL"
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
        val shareTargets = context.packageManager.queryIntentActivities(share, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(MessageReviewActivity::class.java.name), shareTargets.map { it.activityInfo.name })
        val typedCommand = "note DANA_SMOKE_LOCAL"
        fun sendToReview(text: String) = compose.activityRule.scenario.onActivity {
            it.startActivity(Intent(share).putExtra(Intent.EXTRA_TEXT, text))
        }
        sendToReview(typedCommand)
        visible(R.string.message_title)
        visible(typedCommand)
        compose.onNodeWithText(label(R.string.chat_confirm_action)).assertDoesNotExist()
        assertTrue(calls.isEmpty())
        sendToReview(source) // singleTop receives a new share and drops the previous draft.
        visible(source)
        compose.onNodeWithText(typedCommand).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.chat_note_saved)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.message_result)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.voice_playing)).assertDoesNotExist()
        assertTrue(calls.isEmpty())
        messageClick(R.string.message_local)
        messageScroll(hasText(context.getString(R.string.message_clock, "۱۸:۳۰")))
        visible(context.getString(R.string.message_clock, "۱۸:۳۰"))
        val answer = hasTestTag("message-review-answer")
        compose.waitUntilAtLeastOneExists(answer, timeoutMillis = 170_000)
        val text = compose.onNode(answer).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }
        assertTrue("Review must contain generated text, not echo the shared source", text.isNotBlank() && text != source)
        assertTrue(calls.isEmpty())
        assertNoMic()
        compose.onNodeWithContentDescription(label(R.string.studio_back)).performClick()
        visible(R.string.workspace_chat_title)
        compose.onNodeWithText(source).assertDoesNotExist()
        click(R.string.tab_phone)
        enter(R.string.workspace_phone_prompt, typedCommand, scroll = true)
        click(R.string.workspace_run, scroll = true)
        visible(R.string.chat_confirm_action)
        click(R.string.chat_execute)
        visible(R.string.chat_note_saved, scroll = true)
        assertTrue(calls.isEmpty())
        trace("Share opens isolated review; explicitly requested Qwen analysis ran in airplane mode. Only typed Phone note reached action review.")
    }

    @Test
    fun aBundledModelsGenerateWithoutNetworkAndWithoutAnAccount() {
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON))
        visible(R.string.workspace_chat_title)
        waitFor(hasTestTag("local-qwen3-0.6b"))
        compose.onNodeWithTag("local-qwen3-0.6b").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("local-smollm2-135m").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(label(R.string.quick_key)).assertDoesNotExist()
        capture("home")
        val persian = "سلام، به فارسی با یک جمله سلام کن."
        enter(R.string.chat_prompt, persian)
        click(R.string.workspace_send)
        val first = completedReply()
        assertTrue("Qwen reply did not contain Persian script", first.any { it in '\u0600'..'\u06ff' } && first.trim() != persian)
        assertTrue(calls.isEmpty())
        trace("Real Qwen inference returned text in airplane mode, no HTTP calls")
        capture("offline-chat")

        click(R.string.chat_new)
        val smol = compose.onNodeWithTag("local-smollm2-135m")
        smol.assertIsDisplayed().performClick()
        val english = "How many legs does a cat have? Answer in one short sentence."
        enter(R.string.chat_prompt, english)
        click(R.string.workspace_send)
        val second = completedReply()
        assertTrue("SmolLM2 reply was empty or an echo", second.any { it in 'a'..'z' } && second.trim() != english)
        assertTrue(calls.isEmpty())
        assertNoMic()
        trace("Real SmolLM2 inference returned text in airplane mode, no HTTP calls")
    }

    @Test
    fun browserSignInLinksChosenFreeModelWithoutPaidFallback() {
        val variant = "fixture/checked-text:free"
        val id = "free-$variant"
        click(R.string.models_picker)
        modelPickerScroll(hasText(label(R.string.models_fetch_online)))
        replies.add(WireReply(200, """{"data":[
            {"id":"$variant","name":"Checked Text","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"}},
            {"id":"fixture/paid","name":"Paid Test","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0.1","completion":"0"}}
        ]}"""))
        click(R.string.models_fetch_online)
        val listing = checkNotNull(calls.poll(10, TimeUnit.SECONDS))
        assertTrue(listing.url.startsWith("https://openrouter.ai/api/v1/models?"))
        assertEquals(null, listing.auth)
        assertTrue(listing.body.isEmpty())
        compose.waitForIdle()
        modelRow(id).performClick()
        visible(R.string.models_sign_in)
        assertTrue(calls.isEmpty())
        val browser = hasAction(Intent.ACTION_VIEW)
        Intents.init()
        try {
            Intents.intending(browser).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            click(R.string.models_open_browser)
            visible(R.string.models_sign_in_wait)
            compose.waitUntil(timeoutMillis = 10_000) { Intents.getIntents().any { it.action == Intent.ACTION_VIEW } }
            val uri = checkNotNull(Intents.getIntents().single { it.action == Intent.ACTION_VIEW }.data)
            assertEquals("openrouter.ai", uri.host)
            assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
            val callback = Uri.parse(checkNotNull(uri.getQueryParameter("callback_url")))
            assertEquals("localhost", callback.host)
            assertEquals("/callback", callback.path)
            assertTrue(callbackResult(callback.port, "wrong-state").startsWith("HTTP/1.1 400"))
            assertTrue(calls.isEmpty())
            replies.add(WireReply(200, """{"key":"sk-or-v1-ui-fixture"}"""))
            assertTrue(callbackResult(callback.port, checkNotNull(uri.getQueryParameter("state"))).startsWith("HTTP/1.1 200"))
            visible(R.string.models_signed_in)
            val exchange = checkNotNull(calls.poll(10, TimeUnit.SECONDS))
            assertEquals("https://openrouter.ai/api/v1/auth/keys", exchange.url)
            assertEquals(null, exchange.auth)
            val json = org.json.JSONObject(exchange.body)
            val digest = MessageDigest.getInstance("SHA-256").digest(json.getString("code_verifier").toByteArray())
            assertEquals(uri.getQueryParameter("code_challenge"), Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            assertEquals("fixture-auth-code", json.getString("code"))
        } finally { Intents.release() }
        assertTrue(calls.isEmpty())
        enter(R.string.chat_prompt, "پیام فقط برای مدل انتخابی")
        click(R.string.workspace_send)
        visible(R.string.chat_destinations)
        assertTrue(calls.isEmpty())
        replies.add(WireReply(200, completion("جواب آزمایشی مدل انتخابی")))
        click(R.string.send_allow)
        visible("جواب آزمایشی مدل انتخابی")
        val sent = checkNotNull(calls.poll(10, TimeUnit.SECONDS))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", sent.url)
        assertEquals("Bearer sk-or-v1-ui-fixture", sent.auth)
        val request = org.json.JSONObject(sent.body)
        assertEquals(variant, request.getString("model"))
        assertFalse(request.has("models"))
        val caps = request.getJSONObject("provider").getJSONObject("max_price")
        assertEquals(setOf("prompt", "completion", "request", "image"), caps.keys().asSequence().toSet())
        assertTrue(caps.keys().asSequence().all { caps.getInt(it) == 0 })
        assertTrue(calls.isEmpty())
        trace("Public catalog, actual loopback state validation, PKCE exchange fixture and selected zero-price model passed")
    }

    @Test
    fun timerFormValidatesPersianNumbersAndHandsOffToAndroidClock() {
        click(R.string.tab_phone)
        click(R.string.phone_timer_button)
        enter(R.string.phone_duration, "۱۴۴۱")
        compose.onNodeWithText(label(R.string.phone_submit_clock)).assertIsNotEnabled()
        enter(R.string.phone_duration, "۲")
        val timer = allOf(hasAction(AlarmClock.ACTION_SET_TIMER), hasExtra(AlarmClock.EXTRA_LENGTH, 120), hasExtra(AlarmClock.EXTRA_SKIP_UI, false))
        Intents.init()
        try {
            Intents.intending(timer).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            click(R.string.phone_submit_clock)
            Intents.intended(timer)
            visible(R.string.phone_clock_opened)
        } finally { Intents.release() }
        assertTrue(calls.isEmpty())
        assertNoMic()
        trace("Timer form rejected out-of-range duration and sent 120 seconds with Clock UI requested")
    }

    private fun reviewPastedMessageOnline() {
        click(R.string.tab_message)
        visible(R.string.message_title)
        val raw = "کد ورود ۱۲۳-۴۵۶؛ https://example.test/private-token"
        val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
        compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("fixture", raw)) }
        try {
            compose.onNodeWithText(raw).assertDoesNotExist()
            messageClick(R.string.message_paste)
            visible(raw)
            assertTrue(calls.isEmpty())
            fun review() {
                messageClick(R.string.message_online)
                val engine = "${QuickService.AVALAI.title} · ${QuickService.AVALAI.models.first()}"
                waitFor(hasText(engine) and hasClickAction())
                compose.onNode(hasText(engine) and hasClickAction()).assertIsDisplayed().assertIsEnabled().performClick()
                visible(R.string.message_send_title)
                compose.onNodeWithTag("message-outbound").assertTextEquals("کد ورود [محفوظ]؛ [پیوند]")
                assertTrue(calls.isEmpty())
            }
            review()
            click(R.string.cancel)
            assertTrue(calls.isEmpty())
            review()
            replies.add(WireReply(200, completion("برای این پیام اصالت فرستنده تأیید نشده است.")))
            click(R.string.message_send_once)
            val result = "برای این پیام اصالت فرستنده تأیید نشده است."
            messageScroll(hasText(result))
            visible(result)
            val request = checkNotNull(calls.poll(10, TimeUnit.SECONDS))
            assertEquals("${QuickService.AVALAI.api}/chat/completions", request.url)
            assertEquals("Bearer $KEY", request.auth)
            val body = org.json.JSONObject(request.body)
            val messages = body.getJSONArray("messages")
            assertEquals(2, messages.length())
            assertEquals(ReviewMessage.INSTRUCTIONS, messages.getJSONObject(0).getString("content"))
            assertEquals("کد ورود [محفوظ]؛ [پیوند]", messages.getJSONObject(1).getString("content"))
            assertFalse(request.body.contains("۱۲۳-۴۵۶") || request.body.contains("private-token"))
            review()
            click(R.string.cancel)
            assertTrue(calls.isEmpty())
            assertNoMic()
            trace("Explicit clipboard paste, exact masked preview, cancel and fresh approval for one isolated text request passed")
        } finally { compose.runOnIdle { clipboard.clearPrimaryClip() } }
        compose.onNodeWithContentDescription(label(R.string.studio_back)).performClick()
        visible(R.string.art_generate)
    }

    private fun messageScroll(matcher: SemanticsMatcher) = compose.onNodeWithTag("message-review-list").performScrollToNode(matcher)
    private fun messageClick(@StringRes id: Int) {
        messageScroll(clickable(id))
        click(id)
    }

    private fun completedReply(): String {
        val tag = hasTestTag("completed-reply")
        compose.waitUntilAtLeastOneExists(tag, timeoutMillis = 150_000)
        return compose.onAllNodes(hasAnyAncestor(tag), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }.joinToString(" ") { it.text }
    }

    private fun modelPickerScroll(matcher: SemanticsMatcher) = compose.onNodeWithTag("model-picker-list").performScrollToNode(matcher)
    private fun modelRow(tag: String) = compose.onNodeWithTag(tag).also {
        modelPickerScroll(hasTestTag(tag))
        it.assertIsDisplayed().assertIsEnabled()
    }
    private fun callbackResult(port: Int, state: String): String = Socket(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port).use { socket ->
        socket.soTimeout = 5_000
        socket.getOutputStream().write(("GET /callback?code=fixture-auth-code&state=$state HTTP/1.1\r\nHost: localhost:$port\r\n\r\n").toByteArray())
        socket.getInputStream().bufferedReader().readLine()
    }

    private fun checkCall(path: String, key: String, vararg fragments: String) {
        val call = checkNotNull(calls.poll())
        assertEquals(QuickService.AVALAI.api + path, call.url)
        assertEquals("Bearer $key", call.auth)
        assertFalse(call.body.contains(key))
        fragments.forEach { assertTrue("Expected request fragment: $it", call.body.contains(it)) }
    }

    private fun label(@StringRes id: Int) = context.getString(id)
    private fun clickable(@StringRes id: Int) = hasText(label(id)) and hasClickAction()
    private fun waitFor(matcher: SemanticsMatcher) = compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 15_000)
    private fun visible(@StringRes id: Int, scroll: Boolean = false) = visible(label(id), scroll)
    private fun visible(text: String, scroll: Boolean = false) {
        waitFor(hasText(text))
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
    }
    private fun click(@StringRes id: Int, scroll: Boolean = false) {
        val matcher = clickable(id)
        waitFor(matcher)
        val node = compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun enter(@StringRes id: Int, value: String, scroll: Boolean = false) {
        val matcher = hasText(label(id)) and hasSetTextAction()
        waitFor(matcher)
        val node = compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed().performTextReplacement(value)
    }
    private fun trace(message: String) = inst.sendStatus(2, Bundle().apply { putString("stream", "\nmrsad: $message\n") })
    private fun assertNoMic() = assertTrue(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
    private fun completion(text: String) = """{"choices":[{"message":{"role":"assistant","content":"$text"},"finish_reason":"stop"}]}"""
    private fun tinyPng() = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(160, 104, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(116, 87, 181))
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } finally { bitmap.recycle() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(context.filesDir, "ci-screens").apply { mkdirs() }
        val capture = inst.uiAutomation.executeShellCommand("screencap -p")
        ParcelFileDescriptor.AutoCloseInputStream(capture).use { input ->
            File(directory, "$name.png").outputStream().use { output -> assertTrue(input.copyTo(output) > 24) }
        }
        trace("Captured $name")
    }
    private data class WireReply(val code: Int, val body: String, val mime: String = "application/json")
    private class WireCall(val url: String, val auth: String?, val body: String)
    private companion object {
        const val KEY = "dana-fixture-accepted"
        const val BAD_KEY = "dana-fixture-rejected"
    }
}
