package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.ComicPage
import app.amisles.hanime.domain.result.AppResult

/** 漫画内容。只有 hanimeone.me 提供，基址写死，不跟随镜像站设置。 */
interface ComicRepository {

    /** 拉取漫画首页指定页；[page] 从 1 开始。 */
    suspend fun getComics(page: Int = 1): AppResult<ComicPage>
}
