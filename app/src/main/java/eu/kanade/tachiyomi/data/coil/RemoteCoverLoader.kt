package eu.kanade.tachiyomi.data.coil

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import tachiyomi.core.common.util.system.ImageUtil
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
class RemoteCoverLoader {
    // A cover URL is supplied by the user. Tracker sessions and source cookies belong to other clients.
    private val client = OkHttpClient.Builder()
        .callTimeout(25.seconds)
        .build()

    suspend fun load(value: String): ByteArray {
        val url = value.trim().toHttpUrlOrNull()?.takeUnless { it.username.isNotEmpty() || it.password.isNotEmpty() }
            ?: throw IOException("Invalid cover URL")
        val request = Request.Builder().url(url).build()
        val bytes = client.newCall(request).awaitSuccess().use { response ->
            val body = response.body
            if (body.contentType()?.subtype?.let(PAGE_SUBTYPES::contains) == true) {
                val html = body.readBounded(MAX_PAGE_BYTES).decodeToString()
                val imageUrl = findPreviewImage(html, response.request.url)
                    ?: throw IOException("Image missing from page")
                val imageRequest = Request.Builder().url(imageUrl).build()
                client.newCall(imageRequest).awaitSuccess().use {
                    it.body.readBounded(MAX_IMAGE_BYTES)
                }
            } else {
                body.readBounded(MAX_IMAGE_BYTES)
            }
        }
        currentCoroutineContext().ensureActive()
        if (ImageUtil.findImageType { bytes.inputStream() } == null) throw IOException("Unsupported cover image")
        return bytes
    }

    private fun ResponseBody.readBounded(limit: Long): ByteArray {
        val input = source()
        if (contentLength() > limit || input.request(limit + 1L)) throw IOException("Cover response is too large")
        return input.readByteArray()
    }

    private companion object {
        val PAGE_SUBTYPES = setOf("html", "xhtml+xml")
        const val MAX_PAGE_BYTES = 512L * 1024
        const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
    }
}

internal fun findPreviewImage(html: String, pageUrl: HttpUrl): HttpUrl? {
    val document = Jsoup.parse(html, pageUrl.toString())
    val selectors = listOf(
        "meta[property='og:image:secure_url']",
        "meta[property='og:image']",
        "meta[name='twitter:image'], meta[property='twitter:image']",
    )
    return selectors.firstNotNullOfOrNull { selector ->
        document.select(selector).firstNotNullOfOrNull { meta ->
            meta.attr("content").trim().takeIf { it.isNotEmpty() }?.let(pageUrl::resolve)
                ?.takeUnless { it.username.isNotEmpty() || it.password.isNotEmpty() }
        }
    }
}
