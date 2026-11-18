package ai.dana.data.network

import ai.dana.domain.EngineTransport
import ai.dana.domain.ImageTransport
import ai.dana.domain.ModelDirectory
import ai.dana.domain.QuickConnect
import okhttp3.OkHttpClient

/** Shared sockets for text, model discovery and images. Exposes a client injection point for isolated protocol tests. */
class EngineClients(client: OkHttpClient = danaHttpClient()) {
    private val lan = client.privateLanClient()
    val catalog: ModelDirectory = OkHttpModelDirectory(client, lan)
    val chat: EngineTransport = OkHttpEngineTransport(client, lan)
    val art: ImageTransport = OkHttpArtTransport(client)
    val setup = QuickConnect(catalog, chat, OkHttpRouterKeyExchange(client))
}
