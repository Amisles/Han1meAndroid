package app.amisles.hanime.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较的单元测试。
 *
 * 这段逻辑决定「要不要提示用户更新」，判错的代价是双向的：
 * 误报会让用户被一个假的「新版本」反复打扰，漏报会让用户长期停留在旧版本。
 */
class VersionCompareTest {

    @Test
    fun `解析常规三段版本号`() {
        assertEquals(listOf(1, 6, 0), VersionCompare.parse("1.6.0"))
    }

    @Test
    fun `解析带 v 前缀的标签`() {
        assertEquals(listOf(1, 6, 0), VersionCompare.parse("v1.6.0"))
        assertEquals(listOf(1, 6, 0), VersionCompare.parse("V1.6.0"))
    }

    @Test
    fun `解析两段版本号`() {
        assertEquals(listOf(1, 7), VersionCompare.parse("1.7"))
    }

    @Test
    fun `忽略预发布标签与 build metadata`() {
        assertEquals(listOf(1, 7, 0), VersionCompare.parse("1.7.0-beta.1"))
        assertEquals(listOf(1, 7, 0), VersionCompare.parse("1.7.0+build.42"))
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(VersionCompare.parse(""))
        assertNull(VersionCompare.parse("   "))
        assertNull(VersionCompare.parse("abc"))
        assertNull(VersionCompare.parse("1..2"))
        assertNull(VersionCompare.parse("1.6.x"))
        assertNull(VersionCompare.parse("v"))
    }

    @Test
    fun `主版本号更高时判定为新版本`() {
        assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `次版本号更高时判定为新版本`() {
        assertTrue(VersionCompare.isNewer("1.7.0", "1.6.9"))
    }

    @Test
    fun `修订号更高时判定为新版本`() {
        assertTrue(VersionCompare.isNewer("1.6.1", "1.6.0"))
    }

    @Test
    fun `相同版本不算新版本`() {
        assertFalse(VersionCompare.isNewer("1.6.0", "1.6.0"))
    }

    @Test
    fun `两段与三段等价`() {
        assertFalse(VersionCompare.isNewer("1.7", "1.7.0"))
        assertFalse(VersionCompare.isNewer("1.7.0", "1.7"))
    }

    @Test
    fun `旧版本不算新版本`() {
        assertFalse(VersionCompare.isNewer("1.5.0", "1.6.0"))
    }

    @Test
    fun `无法比较时不误报为新版本`() {
        // 宁可不提示，也不能把一个看不懂的标签当成「新版本」推给用户
        assertFalse(VersionCompare.isNewer("abc", "1.6.0"))
        assertFalse(VersionCompare.isNewer("1.6.0", "abc"))
    }

    @Test
    fun `带前缀的版本可以互相比较`() {
        assertTrue(VersionCompare.isNewer("v1.7.0", "1.6.0"))
        assertTrue(VersionCompare.isNewer("1.7.0", "v1.6.0"))
    }
}
