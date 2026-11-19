package app.amisles.hanime.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashLogTextTest {

    @Test
    fun previewPicksExceptionLine() {
        val text = """
            time: Mon Oct 24 22:00:00 GMT+08:00 2026
            version: 1.8.0
            device: Google Pixel 8
            thread: main

            java.lang.NullPointerException: Attempt to invoke virtual method
            	at app.amisles.hanime.MainActivity.onCreate(MainActivity.kt:42)
        """.trimIndent()

        assertEquals(
            "java.lang.NullPointerException: Attempt to invoke virtual method",
            CrashLogText.previewOf(text)
        )
    }

    @Test
    fun previewPicksErrorLine() {
        assertEquals(
            "java.lang.OutOfMemoryError: Failed to allocate",
            CrashLogText.previewOf("version: 1.0\n\njava.lang.OutOfMemoryError: Failed to allocate")
        )
    }

    @Test
    fun previewIsTruncatedTo120Chars() {
        val line = "java.lang.RuntimeException: " + "x".repeat(200)

        assertEquals(120, CrashLogText.previewOf(line).length)
    }

    @Test
    fun previewEmptyWhenNoStackLine() {
        assertEquals("", CrashLogText.previewOf("time: now\nversion: 1.0"))
    }
}
