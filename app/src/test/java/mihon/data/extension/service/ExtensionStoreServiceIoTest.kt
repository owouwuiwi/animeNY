package mihon.data.extension.service

import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.repository.AnimeExtensionStoreRepositoryImpl
import mihon.domain.extension.anime.interactor.AddAnimeExtensionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler

class ExtensionStoreServiceIoTest {
    private val indexUrl = "https://catalog.example/index.json"
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `anime metadata and list are consumed off the calling thread`() = runBlocking {
        withStoreNetwork { network ->
            val service = AnimeExtensionStoreService(network, json, ProtoBuf)
            val store = service.fetch(indexUrl, validateLinkedList = true).getOrThrow()

            assertEquals("Test catalogue", store.name)
            assertEquals("org.example.anime.test", service.getExtensions(store).getOrThrow().single().pkgName)
        }
    }

    @Test
    fun `manga metadata and list are consumed off the calling thread`() = runBlocking {
        withStoreNetwork { network ->
            val service = MangaExtensionStoreService(network, json, ProtoBuf)
            val store = service.fetch(indexUrl).getOrThrow()

            assertEquals("Test catalogue", store.name)
            assertEquals("org.example.extension.test", service.getExtensions(store).getOrThrow().single().pkgName)
        }
    }

    @Test
    fun `adding anime metadata with an incompatible external list fails before saving`() = runBlocking {
        withStoreNetwork(linkedList = """{"unknown":42}""") { network ->
            val handler = mockk<AnimeDatabaseHandler>()
            val addStore = AddAnimeExtensionStore(
                AnimeExtensionStoreRepositoryImpl(AnimeExtensionStoreService(network, json, ProtoBuf), handler, mockk()),
            )

            val error = addStore(indexUrl).exceptionOrNull()

            assertEquals(
                "The linked extension list is invalid. Ask the repository maintainer for a working index.",
                error?.message,
            )
            verify { handler wasNot Called }
        }
    }

    private suspend fun withStoreNetwork(linkedList: String? = null, test: suspend (NetworkHelper) -> Unit) {
        val caller = Thread.currentThread()
        val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val data = when (request.url.encodedPath) {
                "/index.json" -> """
                    {
                        "name": "Test catalogue",
                        "badgeLabel": "Test",
                        "signingKey": "test",
                        "contact": {"website": "https://catalog.example/"},
                        "extensionListUrl": "entries.json"
                    }
                """.trimIndent()

                "/entries.json" -> linkedList ?: """
                    {"extensions": [
                        ${extensionJson("org.example.anime.test")},
                        ${extensionJson("org.example.extension.test")}
                    ]}
                """.trimIndent()

                else -> error("Unexpected request: ${request.url}")
            }
            val source = object : ForwardingSource(Buffer().writeUtf8(data)) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    check(Thread.currentThread() !== caller) { "Response body was read on the calling thread" }
                    return super.read(sink, byteCount)
                }

                override fun close() {
                    check(Thread.currentThread() !== caller) { "Response body was closed on the calling thread" }
                    super.close()
                }
            }.buffer()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    object : ResponseBody() {
                        override fun contentType() = "application/json".toMediaType()
                        override fun contentLength() = -1L
                        override fun source() = source
                    },
                )
                .build()
        }.build()
        val network = mockk<NetworkHelper> {
            every { client } returns httpClient
        }
        try {
            test(network)
        } finally {
            httpClient.dispatcher.executorService.shutdown()
        }
    }

    private fun extensionJson(pkg: String) = """
        {
            "name": "Test extension",
            "packageName": "$pkg",
            "resources": {
                "apkUrl": "https://catalog.example/test.apk",
                "iconUrl": "https://catalog.example/test.png"
            },
            "extensionLib": "1.4",
            "versionCode": 123,
            "versionName": "1.4.123",
            "contentWarning": "SAFE",
            "sources": []
        }
    """.trimIndent()
}
