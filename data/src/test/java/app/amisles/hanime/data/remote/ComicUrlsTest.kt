package app.amisles.hanime.data.remote

import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.ComicSort
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicUrlsTest {

    private val tagUrl = "https://hanimeone.me/tags/%E7%A0%B4%E8%99%95"
    private val artistUrl = "https://hanimeone.me/artists/kuroinu%20juu"

    @Test
    fun directoryPathsMatchSite() {
        assertEquals("/comics/tags", ComicUrls.directoryPath(ComicDirectoryKind.TAG))
        assertEquals("/comics/artists", ComicUrls.directoryPath(ComicDirectoryKind.ARTIST))
    }

    @Test
    fun latestKeepsBaseUrl() {
        assertEquals(tagUrl, ComicUrls.directoryResult(tagUrl, ComicSort.LATEST, 1))
    }

    @Test
    fun popularSortsAppendPathSegment() {
        assertEquals(tagUrl + "/popular-today", ComicUrls.directoryResult(tagUrl, ComicSort.POPULAR_TODAY, 1))
        assertEquals(tagUrl + "/popular-week", ComicUrls.directoryResult(tagUrl, ComicSort.POPULAR_WEEK, 1))
        assertEquals(tagUrl + "/popular", ComicUrls.directoryResult(tagUrl, ComicSort.POPULAR, 1))
    }

    @Test
    fun artistSortsUseSameSegments() {
        assertEquals(
            artistUrl + "/popular-week",
            ComicUrls.directoryResult(artistUrl, ComicSort.POPULAR_WEEK, 1)
        )
    }

    @Test
    fun pageAppendedAfterSortSegment() {
        assertEquals(
            tagUrl + "/popular-week?page=3",
            ComicUrls.directoryResult(tagUrl, ComicSort.POPULAR_WEEK, 3)
        )
        assertEquals(tagUrl + "?page=2", ComicUrls.directoryResult(tagUrl, ComicSort.LATEST, 2))
    }

    @Test
    fun trailingSlashTrimmed() {
        assertEquals(tagUrl + "/popular", ComicUrls.directoryResult(tagUrl + "/", ComicSort.POPULAR, 1))
    }
}
