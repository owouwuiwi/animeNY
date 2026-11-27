package mihon.data.extension.service

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkAnimeExtensionStore
import mihon.data.extension.model.NetworkExtensionStore
import mihon.domain.extension.model.ExtensionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ExtensionIndexListTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val protoBuf = ProtoBuf
    private val store = ExtensionStore(
        indexUrl = "https://catalog.example/index.json",
        name = "Test",
        badgeLabel = "Test",
        signingKey = "test",
        contact = ExtensionStore.Contact(website = "https://catalog.example/", discord = null),
        isLegacy = false,
    )

    @Test
    fun `loads a persisted anime store's list relative to its redirected index`() = runBlocking {
        val finalIndexUrl = "https://cdn.example/store/index.json"
        val listUrl = "https://cdn.example/store/entries.json"
        val expected = NetworkAnimeExtensionStore.ExtensionList(
            listOf(
                NetworkAnimeExtensionStore.Extension(
                    name = "Test extension",
                    packageName = "org.example.anime.test",
                    resources = NetworkAnimeExtensionStore.Resources(
                        apkUrl = "https://catalog.example/test.apk",
                        iconUrl = "https://catalog.example/test.png",
                    ),
                    extensionLib = "1.4",
                    versionCode = 123,
                    versionName = "1.4.123",
                ),
            ),
        )
        val index = NetworkAnimeExtensionStore(
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contact = NetworkAnimeExtensionStore.Contact(store.contact.website, discord = null),
            extensionListUrl = "entries.json",
        )
        val requests = mutableListOf<String>()

        val result = store.readModernExtensionList<NetworkAnimeExtensionStore, NetworkAnimeExtensionStore.ExtensionList>(
            json,
            protoBuf,
        ) { url ->
            requests += url
            when (url) {
                store.indexUrl -> response(finalIndexUrl, json.encodeToString(index).encodeToByteArray())
                listUrl -> response(url, json.encodeToString(expected).encodeToByteArray())
                else -> error("Unexpected request: $url")
            }
        }

        assertEquals(expected, result)
        assertEquals(listOf(store.indexUrl, listUrl), requests)
    }

    @Test
    fun `loads an external gzip protobuf manga list from protobuf metadata`() = runBlocking {
        val indexUrl = "https://catalog.example/index.pb"
        val listUrl = "https://catalog.example/entries.pb"
        val expected = NetworkExtensionStore.ExtensionList(
            listOf(
                NetworkExtensionStore.Extension(
                    name = "Test manga extension",
                    packageName = "org.example.extension.test",
                    resources = NetworkExtensionStore.Resources(
                        apkUrl = "https://catalog.example/manga.apk",
                        iconUrl = "https://catalog.example/manga.png",
                    ),
                    extensionLib = "1.4",
                    versionCode = 123,
                    versionName = "1.4.123",
                    contentWarning = NetworkExtensionStore.ContentWarning.SAFE,
                    sources = emptyList(),
                ),
            ),
        )
        val index = NetworkExtensionStore(
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contact = NetworkExtensionStore.Contact(store.contact.website, discord = null),
            extensionListUrl = listUrl,
        )
        val compressed = ByteArrayOutputStream().also { target ->
            GZIPOutputStream(target).use { it.write(protoBuf.encodeToByteArray(expected)) }
        }.toByteArray()
        val requests = mutableListOf<String>()

        val result = store.copy(indexUrl = indexUrl)
            .readModernExtensionList<NetworkExtensionStore, NetworkExtensionStore.ExtensionList>(json, protoBuf) { url ->
                requests += url
                when (url) {
                    indexUrl -> response(url, protoBuf.encodeToByteArray(index), "application/octet-stream")
                    listUrl -> response(url, compressed, "application/octet-stream")
                    else -> error("Unexpected request: $url")
                }
            }

        assertEquals(expected, result)
        assertEquals(listOf(indexUrl, listUrl), requests)
    }

    @Test
    fun `continues to use an inline extension list without any additional request`() = runBlocking {
        val expected = NetworkAnimeExtensionStore.ExtensionList(emptyList())
        val index = NetworkAnimeExtensionStore(
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contact = NetworkAnimeExtensionStore.Contact(store.contact.website, discord = null),
            extensionList = expected,
        )
        val requests = mutableListOf<String>()

        val result = store.readModernExtensionList<NetworkAnimeExtensionStore, NetworkAnimeExtensionStore.ExtensionList>(
            json,
            protoBuf,
        ) { url ->
            requests += url
            response(url, json.encodeToString(index).encodeToByteArray())
        }

        assertEquals(expected, result)
        assertEquals(listOf(store.indexUrl), requests)
    }

    @Test
    fun `reports an unsupported index without exposing a serializer class name`() {
        val response = response(store.indexUrl, """{"unknown":42}""".encodeToByteArray())

        val error = assertThrows(IllegalArgumentException::class.java) {
            response.readExtensionIndex { source, format ->
                source.decodeExtensionIndexObject<NetworkAnimeExtensionStore>(format, json, protoBuf)
            }
        }

        assertEquals(
            "The extension repository returned an unsupported index. Use the repository's direct index URL.",
            error.message,
        )
    }

    private fun response(url: String, data: ByteArray, contentType: String = "application/json") = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_2)
        .code(200)
        .message("OK")
        .body(data.toResponseBody(contentType.toMediaType()))
        .build()
}
