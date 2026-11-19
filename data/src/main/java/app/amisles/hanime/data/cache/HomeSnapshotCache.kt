package app.amisles.hanime.data.cache

import android.content.Context
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.HomeSnapshot
import app.amisles.hanime.domain.repository.HomeSnapshotRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeSnapshotCache @Inject constructor(
    @ApplicationContext private val context: Context
) : HomeSnapshotRepository {

    override suspend fun read(siteUrl: String): HomeSnapshot? = withContext(Dispatchers.IO) {
        // 文件名经过同名化处理，不同站点地址可能碰撞出同一个文件：回读必须核对内容归属，
        // 否则会把 A 站的快照渲染到 B 站上
        readCacheFile(fileOf(siteUrl), HomeSnapshotCacheCodec::decode)?.takeIf { it.siteUrl == siteUrl }
    }

    override suspend fun write(siteUrl: String, banner: HanimeBanner?, sections: List<HomeSection>) {
        withContext(Dispatchers.IO) {
            writeCacheFile(fileOf(siteUrl), HomeSnapshotCacheCodec.encode(siteUrl, banner, sections))
        }
    }

    private fun fileOf(siteUrl: String): File =
        File(File(context.cacheDir, "home_snapshot"), siteUrl.replace(Regex("[^A-Za-z0-9.-]"), "_") + ".json")
}
