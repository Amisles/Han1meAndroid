package app.amisles.hanime.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索建议的匹配规则。
 *
 * 两条不变量：前缀命中排在包含命中之前；同档内保持数据源顺序（历史=最近优先，标签=目录顺序）。
 */
class SearchSuggestionsTest {

    @Test
    fun historyKeepsPrefixMatchesFirstAndRecencyWithinRank() {
        val result = SearchSuggestions.history("ab", listOf("abc", "abcd", "xabc", "ABC", "zzz"))
        assertEquals(listOf("abc", "abcd", "xabc"), result)
    }

    @Test
    fun historyExcludesExactMatch() {
        val result = SearchSuggestions.history("ABC", listOf("abc", "abcx", "xabc"))
        assertEquals(listOf("abcx", "xabc"), result)
    }

    @Test
    fun historyDeduplicatesCaseVariants() {
        val result = SearchSuggestions.history("a", listOf("Abc", "abc", "ABD"))
        assertEquals(listOf("Abc", "ABD"), result)
    }

    @Test
    fun historyIgnoresBlankInput() {
        assertTrue(SearchSuggestions.history("   ", listOf("abc")).isEmpty())
    }

    @Test
    fun historyIsCapped() {
        val long = (1..8).map { "ab$it" }
        assertEquals(SearchSuggestions.MAX_HISTORY, SearchSuggestions.history("ab", long).size)
    }

    @Test
    fun tagsMatchLocalizedLabel() {
        val tags = listOf(
            SuggestionTag("無碼", "Uncensored"),
            SuggestionTag("中文字幕", "Chinese subtitles"),
            SuggestionTag("1080p", "1080p")
        )
        val result = SearchSuggestions.tags("chin", tags)
        assertEquals(listOf("中文字幕"), result.map { it.value })
    }

    @Test
    fun tagsMatchRawSiteValue() {
        val tags = listOf(
            SuggestionTag("無碼", "Uncensored"),
            SuggestionTag("中文字幕", "Chinese subtitles")
        )
        val result = SearchSuggestions.tags("無碼", tags)
        assertEquals(listOf("無碼"), result.map { it.value })
    }

    @Test
    fun tagsPreferPrefixOverContains() {
        val tags = listOf(
            SuggestionTag("x", "contains-me"),
            SuggestionTag("y", "me-prefix")
        )
        val result = SearchSuggestions.tags("me", tags)
        assertEquals(listOf("y", "x"), result.map { it.value })
    }

    @Test
    fun tagsAreCapped() {
        val many = (1..10).map { SuggestionTag("v$it", "tag$it") }
        assertEquals(SearchSuggestions.MAX_TAGS, SearchSuggestions.tags("tag", many).size)
    }

    @Test
    fun tagsIgnoreBlankInput() {
        assertTrue(SearchSuggestions.tags("  ", listOf(SuggestionTag("x", "y"))).isEmpty())
    }
}
