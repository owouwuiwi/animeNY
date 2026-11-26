package mihon.data.extension.service

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExtensionIndexFormatTest {
    @Test
    fun `recognizes a JSON catalog with a BOM and leading whitespace`() {
        val content = " \r\n\t{}"
        val source = Buffer().writeUtf8("\uFEFF$content")

        assertEquals(ExtensionIndexFormat.JSON_OBJECT, source.extensionIndexFormat(jsonHint = true))
        assertEquals(content, source.readUtf8())
    }

    @Test
    fun `recognizes a legacy index with leading whitespace`() {
        val source = Buffer().writeUtf8("\n  []")

        assertEquals(ExtensionIndexFormat.JSON_ARRAY, source.extensionIndexFormat(jsonHint = true))
    }

    @Test
    fun `does not treat the first protobuf tag as JSON whitespace`() {
        val content = byteArrayOf(0x0A, 0x7B, 0x22, 0x22)
        val source = Buffer().write(content)

        assertEquals(ExtensionIndexFormat.PROTOBUF, source.extensionIndexFormat(jsonHint = false))
        assertArrayEquals(content, source.readByteArray())
    }

    @Test
    fun `rejects HTML and empty JSON before handing them to a decoder`() {
        assertThrows(IllegalArgumentException::class.java) {
            Buffer().writeUtf8(" \n<html>").extensionIndexFormat(jsonHint = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Buffer().writeUtf8(" \n").extensionIndexFormat(jsonHint = true)
        }
    }
}
