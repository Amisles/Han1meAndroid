package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.ComicChapter
import app.amisles.hanime.domain.model.ComicDetail
import app.amisles.hanime.domain.model.ComicPage
import app.amisles.hanime.domain.model.ComicProgress
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** 漫画内容。只有 hanimeone.me 提供，基址写死，不跟随镜像站设置。 */
interface ComicRepository {

    /** 拉取漫画首页指定页；page 从 1 开始。 */
    suspend fun getComics(page: Int = 1): AppResult<ComicPage>

    /** 拉取漫画详情（/comic/{id}）。 */
    suspend fun getComicDetail(comicId: String): AppResult<ComicDetail>

    /** 拉取漫画正文；一次返回整话所有页地址，翻页不再发请求。 */
    suspend fun getComicChapter(comicId: String, page: Int = 1): AppResult<ComicChapter>

    /** 阅读进度流；没有记录时发射 null。 */
    fun observeComicProgress(comicId: String): Flow<ComicProgress?>

    /** 全部阅读进度，按最近阅读排序。 */
    fun observeComicProgressList(): Flow<List<ComicProgress>>

    /** 保存进度：已有记录只更新页码，没有则整行写入。返回 false 表示本地写入失败。 */
    suspend fun saveComicProgress(progress: ComicProgress): Boolean
}
