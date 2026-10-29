package app.amisles.hanime.domain.model

/** 标签 / 作者目录的加载阶段，界面据此给出分步提示。 */
enum class ComicDirectoryPhase {
    /** 正在请求站点页面。 */
    FETCHING,

    /** 页面已到手，正在解析出条目。 */
    PARSING
}
