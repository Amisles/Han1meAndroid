package app.amisles.hanime.data.cache

import android.content.Context
import app.amisles.hanime.domain.model.ComicDirectoryEntry
import app.amisles.hanime.domain.model.ComicDirectoryKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 标签 / 作者目录的当日缓存：同一天内只解析一次，跨天后重新请求。 */
@Singleton
class ComicDirectoryCacheStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun read(kind: ComicDirectoryKind): List<ComicDirectoryEntry>? = withContext(Dispatchers.IO) {
        val file = fileOf(kind)
        if (!file.exists()) return@withContext null
        val cached = runCatching { ComicDirectoryCacheCodec.decode(file.readText()) }.getOrNull()
            ?: return@withContext null
        if (!ComicDirectoryCacheCodec.isSameDay(cached.savedAt, System.currentTimeMillis())) {
            return@withContext null
        }
        cached.entries
    }

    suspend fun write(kind: ComicDirectoryKind, entries: List<ComicDirectoryEntry>) {
        withContext(Dispatchers.IO) {
            val file = fileOf(kind)
            runCatching {
                file.parentFile?.mkdirs()
                val temporary = File(file.parentFile, file.name + ".tmp")
                temporary.writeText(
                    ComicDirectoryCacheCodec.encode(entries, System.currentTimeMillis())
                )
                if (!temporary.renameTo(file)) {
                    file.delete()
                    temporary.renameTo(file)
                }
            }
        }
    }

    private fun fileOf(kind: ComicDirectoryKind): File =
        File(File(context.cacheDir, "comic_directory"), kind.name.lowercase() + ".json")
}
