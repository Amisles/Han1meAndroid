package app.amisles.hanime.data.cookie

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieStringTest {

    @Test
    fun `test filterPrintableAscii with normal string`() {
        val input = "Hello World 123"
        val result = input.filterPrintableAscii()
        assertEquals("Hello World 123", result)
    }

    @Test
    fun `test filterPrintableAscii with special characters`() {
        val input = "Hello\tWorld\nTest"
        val result = input.filterPrintableAscii()
        assertEquals("HelloWorldTest", result)
    }

    @Test
    fun `test filterPrintableAscii with mixed characters`() {
        val input = "user@example.com\t\n"
        val result = input.filterPrintableAscii()
        assertEquals("user@example.com", result)
    }

    @Test
    fun `test filterPrintableAscii with emoji`() {
        val input = "Hello😀World"
        val result = input.filterPrintableAscii()
        assertEquals("HelloWorld", result)
    }

    @Test
    fun `test filterPrintableAscii with all printable ASCII`() {
        val input = " !\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
        val result = input.filterPrintableAscii()
        assertEquals(input, result)
    }
}