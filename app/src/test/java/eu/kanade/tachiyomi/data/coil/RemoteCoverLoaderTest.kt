package eu.kanade.tachiyomi.data.coil

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RemoteCoverLoaderTest {
    private val page = "https://tracker.example/manga/title".toHttpUrl()

    @Test
    fun `secure Open Graph candidates take priority and resolve relative URLs`() {
        val html = """
            <meta property="og:image" content="https://images.example/low.jpg">
            <meta property="og:image:secure_url" content="/images/high.jpg">
        """.trimIndent()

        assertEquals("https://tracker.example/images/high.jpg", findPreviewImage(html, page).toString())
    }

    @Test
    fun `malformed candidates do not hide the next usable image`() {
        val html = """
            <meta property="og:image" content="file:///private/covers.jpg">
            <meta property="og:image" content="https://username:password@images.example/cover.jpg">
            <meta name="twitter:image" content="//images.example/valid.jpg">
        """.trimIndent()

        assertEquals("https://images.example/valid.jpg", findPreviewImage(html, page).toString())
    }

    @Test
    fun `a page without cover metadata does not select an unrelated image`() {
        assertNull(findPreviewImage("""<img src="/site-logo.png">""", page))
    }
}
