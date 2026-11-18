package ai.dana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountAndFreeTest {
    @Test
    fun hostedAuthConfigIsExactAndOnlyAllowsPublishableKeys() {
        val key = "sb_publishable_test_public_abcdefghijkl"
        val base = "https://abcdefghijklmnopqrst.supabase.co"
        assertEquals(base, EmailProject.parse(" $base/ ", key)?.origin)
        assertNull(EmailProject.parse("http://abcdefghijklmnopqrst.supabase.co", key))
        assertNull(EmailProject.parse("$base.evil.example", key))
        assertNull(EmailProject.parse("$base/auth/v1", key))
        assertNull(EmailProject.parse("$base?login=true", key))
        assertNull(EmailProject.parse(base, "sb_secret_test_abcdefghijkl"))
        assertNull(EmailProject.parse(base, "eyJraWQiOiJ0ZXN0In0.eyJyb2xlIjoiYWRtaW4ifQ.not-a-public-key"))
    }

    @Test
    fun acceptsPersianNumbersButRejectsMalformedAddressAndCode() {
        assertEquals("user+test@example.com", AccountInput.email(" user+test@EXAMPLE.com "))
        for (bad in listOf("a@localhost", "sub..part@example.com", "a@-example.com", "A B@example.com", "a@x.test\nBcc:a@x.test", "a@x..test")) {
            assertNull(AccountInput.email(bad))
        }
        assertEquals("129405", AccountInput.otp("۱۲۹٤۰۵"))
        assertNull(AccountInput.otp("۱۲۹۴۰"))
        assertNull(AccountInput.otp("۱۲A۴۰۵"))
    }

    @Test
    fun freeProfileRejectsOtherHostsAndNonFreeModels() {
        val protocol = ProviderProtocol.OPENROUTER_FREE
        val network = ProviderNetwork.INTERNET
        assertNotNull(ProviderConfig.create("رایگان", protocol, network, OPENROUTER_BASE_URL, OPENROUTER_FREE_MODEL))
        assertThrows(InputRejected::class.java) {
            ProviderConfig.create("رایگان", protocol, network, "https://openrouter.ai.example.net/api/v1", OPENROUTER_FREE_MODEL)
        }
        assertThrows(InputRejected::class.java) {
            ProviderConfig.create("رایگان", protocol, network, OPENROUTER_BASE_URL, "openrouter/auto")
        }
        assertThrows(InputRejected::class.java) {
            ProviderConfig.create("رایگان", protocol, ProviderNetwork.LOCAL_NETWORK, OPENROUTER_BASE_URL, OPENROUTER_FREE_MODEL)
        }
    }

    @Test
    fun browserCodeRequiresSameStateAndAnExactLoopbackPath() {
        val approval = RouterApproval.create()
        val url = approval.loopbackUrl(54_321)
        val state = url.substringAfter("&state=")
        assertEquals(43, state.length)
        val code = "fixture-validation-code"
        val good = "/callback?code=$code&state=$state"
        assertEquals(code, approval.acceptCallback(good)?.code)
        assertNull(RouterApproval.create().acceptCallback(good))
        for (bad in listOf("/other?code=$code&state=$state", "http://localhost:54321$good",
            "$good&code=duplicate-code", "$good&state=$state", "$good&error=access_denied", "$good#fragment",
            "/callback?code=fixture-%0A-invalid&state=$state", "/callback?code=$code", "$good${"a".repeat(4096)}")) {
            assertNull(approval.acceptCallback(bad))
        }
    }

    @Test
    fun exactFreeVariantKeepsRealAccountButScopesConsentToTheRequestedModel() {
        val free = ProviderProfile("account", QuickService.FREE.run {
            ProviderConfig.create(title, protocol, ProviderNetwork.INTERNET, api, OPENROUTER_FREE_MODEL)
        }, true, "revision")
        val input = ConversationInput.single("سلام")
        assertNull(RouterFreeModel.fromCatalog("openai/gpt-6", "Paid"))
        assertNull(RouterFreeModel.fromCatalog("bad/suffix:free:paid", "Invalid"))
        assertNull(RouterFreeModel.fromCatalog("a/b:free", "Bad\u202eName"))
        val model = checkNotNull(RouterFreeModel.fromCatalog("a/b:free", "Specific"))
        val selected = checkNotNull(ChatRoute.routerFree(input, free, model))
        assertEquals(listOf(free), selected.candidates)
        assertEquals(model.id, selected.destinations.single().config.model)
        assertEquals(free.config.credentialScope, selected.destinations.single().config.credentialScope)
        assertNull(ChatRoute.routerFree(input, free.copy(hasCredential = false), model))
        val autoGrant = SendGrant.chat(listOf(free), false, true, false, false, true)
        val exactGrant = SendGrant.chat(selected.destinations, false, true, false, false, true)
        org.junit.Assert.assertNotEquals(autoGrant, exactGrant)
    }

    @Test
    fun automaticRouteWithFreePresetNeverIncludesCustomPaidProfile() {
        val free = ProviderProfile("free", ProviderConfig.create("رایگان", ProviderProtocol.OPENROUTER_FREE,
            ProviderNetwork.INTERNET, OPENROUTER_BASE_URL, OPENROUTER_FREE_MODEL), true)
        val paid = ProviderProfile("paid", ProviderConfig.create("دیگر", ProviderProtocol.OPENAI_COMPATIBLE,
            ProviderNetwork.INTERNET, "https://example.com/v1", "paid-model"), true)
        val input = ConversationInput.single("سلام")
        assertEquals(listOf(free), ChatRoute.select(input, listOf(paid, free), null)?.candidates)
        assertEquals(listOf(paid), ChatRoute.select(input, listOf(paid, free), "paid")?.candidates)
        assertNull(ChatRoute.select(input, listOf(free.copy(hasCredential = false)), null))
    }
}
