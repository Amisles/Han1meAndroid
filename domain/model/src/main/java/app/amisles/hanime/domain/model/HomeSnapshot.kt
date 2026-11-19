package app.amisles.hanime.domain.model

/** 首页快照：站点、横幅与分区列表，供冷启动时先渲染再回源。 */
data class HomeSnapshot(
    val siteUrl: String,
    val banner: HanimeBanner?,
    val sections: List<HomeSection>
)
