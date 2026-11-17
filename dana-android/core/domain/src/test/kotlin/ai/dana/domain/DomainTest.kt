package ai.dana.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainTest {
    @Test
    fun canonicalizesHostPortAndTrailingSlash() {
        assertEquals("https://api.example.com/service/v1", config(" HTTPS://API.Example.COM:443/service/v1/ ").baseUrl)
        assertEquals("https://api.example.com", config("https://api.example.com/").baseUrl)
    }

    @Test
    fun acceptsIpv4AndIpv6WithTlsOnExplicitPorts() {
        assertEquals("https://[fd00::1]:11434/v1", config("https://[FD00::1]:11434/v1").baseUrl)
        assertEquals("https://192.168.0.10:8443/v1", config("https://192.168.0.10:8443/v1").baseUrl)
    }

    @Test
    fun rejectsCleartextRelativeUrlsAndBadPorts() {
        listOf("http://192.168.0.10:11434", "example.com/v1", "https:///v1", "https://api.example.com:",
            "https://api.example.com:0", "https://api.example.com:65536", "https://api.example.com./v1").forEach {
            rejects(InputIssue.INVALID_BASE_URL) { config(it) }
        }
    }

    @Test
    fun rejectsEmbeddedCredentialsQueriesAndFragments() {
        listOf("https://secret@api.example.com", "https://@api.example.com", "https://api.example.com/v1?",
            "https://api.example.com/v1?key=test", "https://api.example.com/v1#", "https://api.example.com/v1#anchor").forEach {
            rejects(InputIssue.INVALID_BASE_URL) { config(it) }
        }
    }

    @Test
    fun rejectsAmbiguousOrTraversalPaths() {
        listOf("https://api.example.com/a/../v1", "https://api.example.com/./v1",
            "https://api.example.com/%2e%2e/v1", "https://api.example.com/%2fv1", "https://api.example.com/a//v1",
            "https://api.example.com/v1//", "https://api.example.com\\@origin.example/v1", "https://api.example.com/ v1").forEach {
            rejects(InputIssue.INVALID_BASE_URL) { config(it) }
        }
    }

    @Test
    fun changingTheLabelOrModelDoesNotChangeCredentialScope() {
        val current = config()
        val renamed = config(name = "موتور خانگی", model = "models/model-2")
        assertEquals(current.credentialScope, renamed.credentialScope)
        CredentialChange.Keep.validateFor(profile(current), renamed)
    }

    @Test
    fun keepingAStoredKeyIsRejectedWhenDestinationChanges() {
        val current = profile()
        val alternatives = listOf(
            config(url = "https://different.example.com/v1"),
            config(url = "https://api.example.com/v2"),
            config(url = "https://api.example.com:8443/v1"),
            config(network = ProviderNetwork.LOCAL_NETWORK),
            config(protocol = ProviderProtocol.GEMINI_NATIVE),
        )
        alternatives.forEach { destination ->
            assertNotEquals(current.config.credentialScope, destination.credentialScope)
            rejects(InputIssue.CREDENTIAL_DESTINATION_CHANGED) { CredentialChange.Keep.validateFor(current, destination) }
        }
    }

    @Test
    fun destinationChangeCanRemoveOrReplaceKeyAndDoesNotBlockAnUnkeyedProfile() {
        val next = config("https://new.example.com")
        CredentialChange.Remove.validateFor(profile(), next)
        CredentialChange.Replace(ApiCredential.parse("unit-test-value")).validateFor(profile(), next)
        CredentialChange.Keep.validateFor(profile().copy(hasCredential = false), next)
        CredentialChange.Keep.validateFor(null, next)
    }

    @Test
    fun boundsNamesAndModelIdentifiers() {
        rejects(InputIssue.INVALID_PROVIDER_NAME) { config(name = "  ") }
        rejects(InputIssue.INVALID_PROVIDER_NAME) { config(name = "الف".repeat(ProviderConfig.NAME_LIMIT + 1)) }
        rejects(InputIssue.INVALID_PROVIDER_NAME) { config(name = "سه\nخط") }
        rejects(InputIssue.INVALID_MODEL) { config(model = "two words") }
        rejects(InputIssue.INVALID_MODEL) { config(model = "m".repeat(ProviderConfig.MODEL_LIMIT + 1)) }
        assertEquals("model-alpha:1", config(model = " model-alpha:1 ").model)
    }

    @Test
    fun credentialIsRedactedInDiagnostics() {
        val key = ApiCredential.parse(" unit-test-only-secret ")
        assertEquals("unit-test-only-secret", key.value)
        assertFalse(key.toString().contains(key.value))
        assertFalse(CredentialChange.Replace(key).toString().contains(key.value))
    }

    @Test
    fun rejectsHeaderInjectionWhitespaceNonAsciiAndOversizedCredentials() {
        listOf("", "two words", "abcd\r\nBearer: other", "کلید", "k".repeat(ApiCredential.LIMIT + 1)).forEach {
            rejects(InputIssue.INVALID_API_KEY) { ApiCredential.parse(it) }
        }
    }

    @Test
    fun notesPreserveFarsiTextAndHaveBoundedSize() {
        assertEquals("یادداشت‌ تازه\nخط دوم", LocalText.note("  یادداشت‌ تازه\nخط دوم  "))
        assertEquals(LocalText.NOTE_LIMIT, LocalText.note("۱".repeat(LocalText.NOTE_LIMIT)).length)
        rejects(InputIssue.EMPTY_NOTE) { LocalText.note(" \t\n ") }
        rejects(InputIssue.NOTE_TOO_LONG) { LocalText.note("۱".repeat(LocalText.NOTE_LIMIT + 1)) }
    }

    @Test
    fun localHistoryAndMemoryRejectEmptyOrOversizedInput() {
        assertEquals("موضوع امروز", LocalText.title(" موضوع امروز "))
        assertEquals("ترجیح فارسی", LocalText.memory(" ترجیح فارسی "))
        assertEquals("سؤال\n\nدوخط", LocalText.message("سؤال\n\nدوخط"))
        rejects(InputIssue.INVALID_CONVERSATION_TITLE) { LocalText.title("") }
        rejects(InputIssue.INVALID_MESSAGE) { LocalText.message("ب".repeat(LocalText.MESSAGE_LIMIT + 1)) }
        rejects(InputIssue.INVALID_MEMORY) { LocalText.memory("ب".repeat(LocalText.MEMORY_LIMIT + 1)) }
    }

    private fun profile(config: ProviderConfig = config()) = ProviderProfile("test-profile", config, hasCredential = true)

    private fun config(
        url: String = "https://api.example.com/v1",
        name: String = "آزمون",
        model: String = "provider/model-1",
        protocol: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE,
        network: ProviderNetwork = ProviderNetwork.INTERNET,
    ) = ProviderConfig.create(name, protocol, network, url, model)

    private fun rejects(issue: InputIssue, block: () -> Unit) {
        val error = assertThrows(InputRejected::class.java) { block() }
        assertEquals(issue, error.issue)
        assertEquals(issue.name, error.message)
    }
}
