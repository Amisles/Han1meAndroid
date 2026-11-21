package app.amisles.hanime.data.remote

import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.ComicSort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicApi @Inject constructor(
    private val transport: HttpTransport
) {
    suspend fun fetchComics(page: Int = 1): FetchResult {
        val url = COMICS_BASE_URL + if (page > 1) "/comics?page=$page" else "/comics"
        return FetchResult(transport.get(url), COMICS_BASE_URL)
    }

    suspend fun fetchComicDirectory(kind: ComicDirectoryKind): FetchResult {
        val url = COMICS_BASE_URL + ComicUrls.directoryPath(kind)
        return FetchResult(transport.get(url), COMICS_BASE_URL)
    }

    suspend fun fetchComicDirectoryResult(baseUrl: String, sort: ComicSort, page: Int): FetchResult {
        val url = ComicUrls.directoryResult(baseUrl, sort, page)
        return FetchResult(transport.get(url), COMICS_BASE_URL)
    }

    suspend fun fetchComicDetail(comicId: String): FetchResult {
        val url = COMICS_BASE_URL + "/comic/" + comicId
        return FetchResult(transport.get(url), COMICS_BASE_URL)
    }

    suspend fun fetchComicChapter(comicId: String, page: Int): FetchResult {
        val url = COMICS_BASE_URL + "/comic/" + comicId + "/" + page
        return FetchResult(transport.get(url), COMICS_BASE_URL)
    }

    companion object {
        const val COMICS_BASE_URL = "https://hanimeone.me"
    }
}
