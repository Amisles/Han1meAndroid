package app.amisles.hanime.domain.model

/**
 * 首页数据流式加载事件
 */
sealed interface HomeDataEvent {
    data class Banner(val banner: HanimeBanner?) : HomeDataEvent
    data class Section(val section: HomeSection) : HomeDataEvent
    data class Error(val message: String) : HomeDataEvent
}
