package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.HomeSnapshot

interface HomeSnapshotRepository {

    /** 读取指定站点的快照；站点不匹配或文件损坏时返回 null。 */
    suspend fun read(siteUrl: String): HomeSnapshot?

    suspend fun write(siteUrl: String, banner: HanimeBanner?, sections: List<HomeSection>)
}
