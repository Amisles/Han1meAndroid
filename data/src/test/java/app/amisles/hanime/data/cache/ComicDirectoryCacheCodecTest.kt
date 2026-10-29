package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.ComicDirectoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 目录缓存的编解码与「同一天」判定；文件写入依赖 Android 存储，不在单测覆盖范围。 */
class ComicDirectoryCacheCodecTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millisOf(date: String, hour: Int): Long =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun encodeAndDecodeKeepEveryField() {
        val entries = listOf(
            ComicDirectoryEntry("純愛", "4.2k", "https://hanimeone.me/comics/tags/1"),
            ComicDirectoryEntry("NTR", "", "https://hanimeone.me/comics/tags/2")
        )
        val raw = ComicDirectoryCacheCodec.encode(entries, 1_700_000_000_000)

        val cached = ComicDirectoryCacheCodec.decode(raw)

        assertEquals(entries, cached?.entries)
        assertEquals(1_700_000_000_000, cached?.savedAt)
    }

    @Test
    fun decodeRejectsMalformedPayload() {
        assertNull(ComicDirectoryCacheCodec.decode(""))
        assertNull(ComicDirectoryCacheCodec.decode("{}"))
        assertNull(ComicDirectoryCacheCodec.decode("{\"savedAt\":1}"))
    }

    @Test
    fun decodeSkipsEmptyArrays() {
        val cached = ComicDirectoryCacheCodec.decode("{\"savedAt\":1,\"entries\":[]}")

        assertEquals(emptyList<ComicDirectoryEntry>(), cached?.entries)
    }

    @Test
    fun sameDayOnlyInsideOneCalendarDay() {
        val morning = millisOf("2026-10-29", 1)
        val night = millisOf("2026-10-29", 23)
        val nextDay = millisOf("2026-10-30", 0)

        assertTrue(ComicDirectoryCacheCodec.isSameDay(morning, night, zone))
        assertFalse(ComicDirectoryCacheCodec.isSameDay(night, nextDay, zone))
    }
}
