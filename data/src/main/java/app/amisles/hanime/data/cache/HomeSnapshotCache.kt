package app.amisles.hanime.data.cache

import android.content.Context
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSnapshotCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun read(siteUrl: String): HomeSnapshot? = withContext(Dispatchers.IO) {
        val file = fileOf(siteUrl)
        if (!file.exists()) return@withContext null
        val snapshot = runCatching { HomeSnapshotCacheCodec.decode(file.readText()) }.getOrNull()
            ?: return@withContext null
        // 文件名经过同名化处理，不同站点地址可能碰撞出同一个文件：回读必须核对内容归属，
        // 否则会把 A 站的快照渲染到 B 站上
        snapshot.takeIf { it.siteUrl == siteUrl }
    }

    suspend fun write(siteUrl: String, banner: HanimeBanner?, sections: List<HomeSection>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = fileOf(siteUrl)
                file.parentFile?.mkdirs()
                val temporary = File(file.parentFile, file.name + ".tmp")
                temporary.writeText(HomeSnapshotCacheCodec.encode(siteUrl, banner, sections))
                if (!temporary.renameTo(file)) {
                    // 缓存可重建：允许先删旧文件再 rename，两步之间的崩溃窗口最多丢一份快照
                    file.delete()
                    temporary.renameTo(file)
                }
            }
        }
    }

    private fun fileOf(siteUrl: String): File =
        File(File(context.cacheDir, "home_snapshot"), siteUrl.replace(Regex("[^A-Za-z0-9.-]"), "_") + ".json")
}
