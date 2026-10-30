package app.amisles.hanime.data.repository

import android.database.SQLException
import app.amisles.hanime.data.cache.ComicDirectoryCacheStore
import app.amisles.hanime.data.local.database.ComicProgressDao
import app.amisles.hanime.data.local.entity.toDomain
import app.amisles.hanime.data.local.entity.toEntity
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.data.parser.ComicDetailParser
import app.amisles.hanime.data.parser.ComicDirectoryParser
import app.amisles.hanime.data.parser.ComicDirectoryResultParser
import app.amisles.hanime.data.parser.ComicPageParser
import app.amisles.hanime.data.parser.ComicReaderParser
import app.amisles.hanime.domain.model.ComicChapter
import app.amisles.hanime.domain.model.ComicDetail
import app.amisles.hanime.domain.model.ComicDirectoryEntry
import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.ComicDirectoryPhase
import app.amisles.hanime.domain.model.ComicDirectoryResult
import app.amisles.hanime.domain.model.ComicPage
import app.amisles.hanime.domain.model.ComicProgress
import app.amisles.hanime.domain.model.ComicSort
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.amisles.hanime.domain.repository.ComicRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicRepositoryImpl @Inject constructor(
    private val networkService: NetworkService,
    private val comicPageParser: ComicPageParser,
    private val comicDetailParser: ComicDetailParser,
    private val comicReaderParser: ComicReaderParser,
    private val comicDirectoryParser: ComicDirectoryParser,
    private val comicDirectoryResultParser: ComicDirectoryResultParser,
    private val comicDirectoryCache: ComicDirectoryCacheStore,
    private val comicProgressDao: ComicProgressDao
) : ComicRepository {

    override suspend fun getComics(page: Int): AppResult<ComicPage> {
        return try {
            val result = networkService.fetchComicsPage(page)
            AppLogger.log("HanimeRepository", "Comics HTML received, length: " + result.html.length)
            AppResult.success(comicPageParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getComics: " + e.message, e)
            AppResult.error(e.message ?: "加载漫画失败", e)
        }
    }



    override suspend fun getComicDirectory(
        kind: ComicDirectoryKind,
        refresh: Boolean,
        onPhase: (ComicDirectoryPhase) -> Unit
    ): AppResult<List<ComicDirectoryEntry>> {
        if (!refresh) {
            val cached = comicDirectoryCache.read(kind)
            if (cached != null) return AppResult.success(cached)
        }
        return try {
            onPhase(ComicDirectoryPhase.FETCHING)
            val result = networkService.fetchComicDirectoryPage(kind)
            onPhase(ComicDirectoryPhase.PARSING)
            val entries = comicDirectoryParser.parse(result.html, result.baseUrl, kind)
            comicDirectoryCache.write(kind, entries)
            AppResult.success(entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getComicDirectory: " + e.message, e)
            val fallback = if (kind == ComicDirectoryKind.TAG) "加载标签失败" else "加载作者失败"
            AppResult.error(e.message ?: fallback, e)
        }
    }


    override suspend fun getComicDirectoryResult(
        baseUrl: String,
        sort: ComicSort,
        page: Int
    ): AppResult<ComicDirectoryResult> {
        return try {
            val result = networkService.fetchComicDirectoryResultPage(baseUrl, sort, page)
            AppResult.success(comicDirectoryResultParser.parse(result.html, result.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getComicDirectoryResult: " + e.message, e)
            AppResult.error(e.message ?: "加载漫画列表失败", e)
        }
    }


    override suspend fun getComicDetail(comicId: String): AppResult<ComicDetail> {
        return try {
            val result = networkService.fetchComicDetailPage(comicId)
            AppResult.success(comicDetailParser.parse(result.html, result.baseUrl, comicId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getComicDetail: " + e.message, e)
            AppResult.error(e.message ?: "加载漫画详情失败", e)
        }
    }


    override suspend fun getComicChapter(comicId: String, page: Int): AppResult<ComicChapter> {
        return try {
            val result = networkService.fetchComicChapterPage(comicId, page)
            AppResult.success(comicReaderParser.parse(result.html, result.baseUrl, comicId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("HanimeRepository", "Error in getComicChapter: " + e.message, e)
            AppResult.error(e.message ?: "加载漫画失败", e)
        }
    }


    override fun observeComicProgress(comicId: String): Flow<ComicProgress?> =
        comicProgressDao.observeProgress(comicId).map { it?.toDomain() }


    override fun observeComicProgressList(): Flow<List<ComicProgress>> =
        comicProgressDao.getAllProgress().map { list -> list.map { it.toDomain() } }


    override suspend fun saveComicProgress(progress: ComicProgress): Boolean {
        return try {
            val updated = comicProgressDao.updateLastPage(
                progress.comicId,
                progress.lastPage,
                progress.updatedAt
            )
            if (updated == 0) comicProgressDao.upsert(progress.toEntity())
            true
        } catch (e: SQLException) {
            AppLogger.logError("HanimeRepository", "Error saving comic progress: " + e.message, e)
            false
        }
    }

}
