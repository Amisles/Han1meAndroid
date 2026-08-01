package app.amisles.hanime.domain.model

data class HomeSection(
    val title: String,
    val moreUrl: String,
    val videos: List<HanimeVideo>
)

data class HomePageData(
    val banner: HanimeBanner?,
    val sections: List<HomeSection>
)