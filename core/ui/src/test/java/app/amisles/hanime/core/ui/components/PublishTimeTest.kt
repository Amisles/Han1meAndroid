package app.amisles.hanime.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishTimeTest {

    @Test
    fun `parses traditional chinese relative terms`() {
        assertEquals(RelativeTime(RelativeTimeUnit.MINUTES, 55), PublishTime.parse("55分鐘前"))
        assertEquals(RelativeTime(RelativeTimeUnit.HOURS, 3), PublishTime.parse("3小時前"))
        assertEquals(RelativeTime(RelativeTimeUnit.DAYS, 5), PublishTime.parse("5天前"))
        assertEquals(RelativeTime(RelativeTimeUnit.WEEKS, 2), PublishTime.parse("2週前"))
        assertEquals(RelativeTime(RelativeTimeUnit.MONTHS, 1), PublishTime.parse("1個月前"))
        assertEquals(RelativeTime(RelativeTimeUnit.YEARS, 2), PublishTime.parse("2年前"))
    }

    @Test
    fun `parses simplified and alternate unit forms`() {
        assertEquals(RelativeTime(RelativeTimeUnit.MINUTES, 5), PublishTime.parse("5分钟前"))
        assertEquals(RelativeTime(RelativeTimeUnit.MINUTES, 5), PublishTime.parse("5分前"))
        assertEquals(RelativeTime(RelativeTimeUnit.HOURS, 12), PublishTime.parse("12小时前"))
        assertEquals(RelativeTime(RelativeTimeUnit.DAYS, 3), PublishTime.parse("3日前"))
        assertEquals(RelativeTime(RelativeTimeUnit.WEEKS, 1), PublishTime.parse("1周前"))
        assertEquals(RelativeTime(RelativeTimeUnit.MONTHS, 6), PublishTime.parse("6个月前"))
    }

    @Test
    fun `parses just now terms`() {
        assertEquals(RelativeTime(RelativeTimeUnit.JUST_NOW), PublishTime.parse("剛剛"))
        assertEquals(RelativeTime(RelativeTimeUnit.JUST_NOW), PublishTime.parse("刚刚"))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(RelativeTime(RelativeTimeUnit.HOURS, 3), PublishTime.parse(" 3小時前 "))
    }

    @Test
    fun `absolute dates and unknown formats return null for passthrough`() {
        assertNull(PublishTime.parse("2025/03/02"))
        assertNull(PublishTime.parse("昨天"))
        assertNull(PublishTime.parse(""))
        assertNull(PublishTime.parse("0分鐘前"))
    }
}
