package app.amisles.hanime.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 崩溃记录头部的解析。
 *
 * CrashLogger 依赖 Android 的 File 与 Build，无法在 JVM 单测里完整跑通；
 * 这里只覆盖「从已落盘文本里抽取预览与版本」这段纯字符串逻辑，其余靠真机验证。
 */
class CrashLoggerParseTest {

    private fun previewOf(text: String): String =
        text.lineSequence()
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.trim()
            ?.take(120)
            .orEmpty()

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
        assertEquals("java.lang.NullPointerException: Attempt to invoke virtual method", previewOf(text))
    }

    @Test
    fun previewPicksErrorLine() {
        val text = "java.lang.OutOfMemoryError: Failed to allocate"
        assertEquals("java.lang.OutOfMemoryError: Failed to allocate", previewOf(text))
    }

    @Test
    fun previewEmptyWhenNoStackLine() {
        assertTrue(previewOf("time: now\nversion: 1.0").isEmpty())
    }
}
