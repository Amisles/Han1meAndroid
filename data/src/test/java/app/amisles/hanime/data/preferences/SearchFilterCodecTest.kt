package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.model.SearchFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFilterCodecTest {

    @Test
    fun `round trips every field`() {
        val state = SearchFilterState(
            genre = "li_fan",
            sort = "new_release",
            tags = listOf("NTR", "中文字幕"),
            broad = true,
            date = "過去 24 小時",
            duration = "30 分鐘 +"
        )
        assertEquals(state, SearchFilterCodec.decode(SearchFilterCodec.encode(state)))
    }

    @Test
    fun `empty state round trips to empty state`() {
        assertEquals(SearchFilterState(), SearchFilterCodec.decode(SearchFilterCodec.encode(SearchFilterState())))
    }

    @Test
    fun `blank and malformed input degrade to empty state`() {
        assertEquals(SearchFilterState(), SearchFilterCodec.decode(null))
        assertEquals(SearchFilterState(), SearchFilterCodec.decode(""))
        assertEquals(SearchFilterState(), SearchFilterCodec.decode("9\u001Fx"))
        assertEquals(SearchFilterState(), SearchFilterCodec.decode("1\u001Fonly-two-fields"))
    }

    @Test
    fun `blank genre and sort decode as null`() {
        val decoded = SearchFilterCodec.decode(SearchFilterCodec.encode(SearchFilterState(date = "d")))
        assertNull(decoded.genre)
        assertNull(decoded.sort)
        assertEquals("d", decoded.date)
    }
}
