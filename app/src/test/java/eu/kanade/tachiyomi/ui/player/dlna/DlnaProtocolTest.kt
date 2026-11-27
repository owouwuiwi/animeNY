package eu.kanade.tachiyomi.ui.player.dlna

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import java.net.InetAddress

class DlnaProtocolTest {
    @Test
    fun `verified device announcements bind location to UDP sender`() {
        val sender = InetAddress.getByName("127.0.0.1") as Inet4Address
        val reply = """
            HTTP/1.1 200 OK
            st: ${DLNA_RENDERER_PREFIX}1
            usn: $DEVICE_ID::${DLNA_RENDERER_PREFIX}1
            Location: http://127.0.0.1:18080/description.xml
        """.trimIndent()

        assertEquals(announcement(), DlnaDiscovery.parseReply(reply, sender))
        assertNull(DlnaDiscovery.parseReply(reply.replace("127.0.0.1", "127.0.0.2"), sender))
        assertNull(DlnaDiscovery.parseReply(reply.replace("127.0.0.1", "localhost"), sender))
        assertNull(DlnaDiscovery.parseReply(reply.replace("127.0.0.1", "name@127.0.0.1"), sender))
        assertNull(DlnaDiscovery.parseReply("$reply\nLOCATION: http://127.0.0.1:18080/o", sender))
        assertNull(DlnaDiscovery.parseReply(reply.replace("MediaRenderer", "MediaServer"), sender))
    }

    @Test
    fun `identities and endpoints in device descriptions are checked`() {
        val renderer = DlnaControlPoint.parseDescription(announcement(), description())
        assertNotNull(renderer)
        assertEquals("Test & TV", renderer?.name)
        assertEquals("http://127.0.0.1:18080/control/AVTransport", renderer?.controlUrl.toString())
        assertNull(DlnaControlPoint.parseDescription(announcement(), description().replace(DEVICE_ID, "uuid:other")))
        assertNull(DlnaControlPoint.parseDescription(announcement(), description("//127.0.0.2:18080/control")))
        assertNull(DlnaControlPoint.parseDescription(announcement(), description("//127.0.0.1:18081/control")))
        assertNull(DlnaControlPoint.parseDescription(announcement(), description("/control#section")))
    }

    @Test
    fun `legacy url base stays bound to the description endpoint`() {
        val xml = description("render").replace(
            "<device>",
            "<URLBase>http://127.0.0.1:18080/legacy/</URLBase><device>",
        )
        val renderer = DlnaControlPoint.parseDescription(announcement(), xml)
        assertEquals("http://127.0.0.1:18080/legacy/render", renderer?.controlUrl.toString())
        assertNull(DlnaControlPoint.parseDescription(announcement(), xml.replace("127.0.0.1", "127.0.0.2")))
    }

    @Test
    fun `malformed and declared entity XML is rejected`() {
        assertNull(DlnaControlPoint.parseDescription(announcement(), "<root>"))
        assertNull(DlnaControlPoint.parseDescription(announcement(), "<!DOCTYPE root []>${description()}"))
        assertNull(DlnaControlPoint.parseDescription(announcement(), description().replace("device-1-0", "unexpected")))
    }

    @Test
    fun `only direct media without private access requirements is offered`() {
        val headers = Headers.Builder().add("User-Agent", "Player").build()
        val media = DlnaMedia.fromDirectVideo("https://example.invalid/FILM.MP4", "Film", headers)
        assertEquals("video/mp4", media?.mimeType)
        assertNull(DlnaMedia.fromDirectVideo("https://example.invalid/FILM.MP4?token=example", "Film", headers))
        assertNull(DlnaMedia.fromDirectVideo("https://example.invalid/live.m3u8", "Film", headers))
        assertNull(DlnaMedia.fromDirectVideo("http://127.0.0.1:8080/FILM.MP4", "Film", null))
        assertNull(DlnaMedia.fromDirectVideo("file:///storage/film.mp4", "Film", null))
        assertNull(DlnaMedia.fromDirectVideo("https://login@example.invalid/FILM.MP4", "Film", null))
        assertNull(
            DlnaMedia.fromDirectVideo(
                "https://example.invalid/film.mp4",
                "Film",
                Headers.Builder().add("Authorization", "Bearer synthetic").build(),
            ),
        )
    }

    private fun announcement() =
        DlnaAnnouncement(DEVICE_ID, "http://127.0.0.1:18080/description.xml".toHttpUrl())

    private fun description(control: String = "/control/AVTransport") = """
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>${DLNA_RENDERER_PREFIX}1</deviceType>
            <friendlyName>Test &amp; TV</friendlyName>
            <UDN>$DEVICE_ID</UDN>
            <serviceList>
              <service>
                <serviceType>${DLNA_TRANSPORT_PREFIX}1</serviceType>
                <controlURL>$control</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private companion object {
        const val DEVICE_ID = "uuid:synthetic-renderer"
    }
}
