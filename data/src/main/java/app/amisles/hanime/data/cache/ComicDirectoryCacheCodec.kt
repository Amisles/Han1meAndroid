package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.ComicDirectoryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class CachedComicDirectory(
    val savedAt: Long,
    val entries: List<ComicDirectoryEntry>
)

internal object ComicDirectoryCacheCodec {

    fun encode(entries: List<ComicDirectoryEntry>, savedAt: Long): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("count", entry.count)
                    .put("url", entry.url)
            )
        }
        return JSONObject()
            .put("savedAt", savedAt)
            .put("entries", array)
            .toString()
    }

    fun decode(raw: String): CachedComicDirectory? = runCatching {
        val json = JSONObject(raw)
        val savedAt = json.getLong("savedAt")
        val array = json.getJSONArray("entries")
        val entries = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ComicDirectoryEntry(
                        name = item.optString("name"),
                        count = item.optString("count"),
                        url = item.optString("url")
                    )
                )
            }
        }
        CachedComicDirectory(savedAt, entries)
    }.getOrNull()

    fun isSameDay(first: Long, second: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(first, zone) == toLocalDate(second, zone)

    private fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
