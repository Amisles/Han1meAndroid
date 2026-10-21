package app.amisles.hanime.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub Releases 解析的单元测试。
 *
 * 用真实 API 的响应形状构造样本：字段名、嵌套层级都按 GitHub 文档来，
 * 避免「测试过了但线上解析不出来」。
 */
class GitHubReleaseParserTest {

    private fun release(
        tag: String,
        body: String = "notes",
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: String = ""
    ): String {
        return """
        {
          "tag_name": "$tag",
          "name": "$tag release",
          "body": "$body",
          "html_url": "https://github.com/Amisles/repo/releases/tag/$tag",
          "prerelease": $prerelease,
          "draft": $draft,
          "published_at": "2026-10-20T10:00:00Z",
          "assets": [${if (assets.isEmpty()) "" else assets}]
        }
        """.trimIndent()
    }

    private val apkAsset = """
        {
          "name": "hanime-1.7.0.apk",
          "size": 12345678,
          "browser_download_url": "https://github.com/Amisles/repo/releases/download/v1.7.0/hanime-1.7.0.apk"
        }
    """.trimIndent()

    @Test
    fun `发现比当前版本更新的发行版`() {
        val json = "[${release("v1.7.0")}]"
        val update = GitHubReleaseParser.parseLatest(json, "1.6.0")
        assertNotNull(update)
        assertEquals("1.7.0", update!!.versionName)
        assertEquals("v1.7.0", update.tagName)
    }

    @Test
    fun `当前已是最新时返回 null`() {
        val json = "[${release("v1.6.0")}]"
        assertNull(GitHubReleaseParser.parseLatest(json, "1.6.0"))
    }

    @Test
    fun `跳过比当前版本旧的发行版`() {
        val json = "[${release("v1.5.0")}, ${release("v1.4.0")}]"
        assertNull(GitHubReleaseParser.parseLatest(json, "1.6.0"))
    }

    @Test
    fun `多个新版本时取最高的那个`() {
        // 发布顺序与版本号顺序未必一致，不能简单取数组第一个
        val json = "[${release("v1.7.0")}, ${release("v1.9.2")}, ${release("v1.8.0")}]"
        val update = GitHubReleaseParser.parseLatest(json, "1.6.0")
        assertEquals("1.9.2", update!!.versionName)
    }

    @Test
    fun `默认忽略预发布版`() {
        val json = "[${release("v2.0.0-beta", prerelease = true)}]"
        assertNull(GitHubReleaseParser.parseLatest(json, "1.6.0"))
    }

    @Test
    fun `显式要求时接受预发布版`() {
        val json = "[${release("v2.0.0-beta", prerelease = true)}]"
        val update = GitHubReleaseParser.parseLatest(json, "1.6.0", includePrerelease = true)
        assertNotNull(update)
        assertTrue(update!!.isPrerelease)
    }

    @Test
    fun `总是忽略草稿`() {
        // draft 是未发布的草稿，任何人都看不到，不该拿来提示
        val json = "[${release("v9.9.9", draft = true)}]"
        assertNull(GitHubReleaseParser.parseLatest(json, "1.6.0"))
    }

    @Test
    fun `解析出 APK 资产直链与大小`() {
        val json = "[${release("v1.7.0", assets = apkAsset)}]"
        val update = GitHubReleaseParser.parseLatest(json, "1.6.0")
        assertNotNull(update)
        assertTrue(update!!.hasApk)
        assertTrue(update.apkUrl.endsWith(".apk"))
        assertEquals(12345678L, update.apkSizeBytes)
    }

    @Test
    fun `没有 APK 资产时 hasApk 为 false`() {
        val json = "[${release("v1.7.0")}]"
        val update = GitHubReleaseParser.parseLatest(json, "1.6.0")
        assertNotNull(update)
        assertEquals(false, update!!.hasApk)
    }

    @Test
    fun `忽略无法解析版本号的标签`() {
        val json = "[${release("nightly")}]"
        assertNull(GitHubReleaseParser.parseLatest(json, "1.6.0"))
    }

    @Test
    fun `非法 JSON 返回 null 而不抛异常`() {
        assertNull(GitHubReleaseParser.parseLatest("not json", "1.6.0"))
        assertNull(GitHubReleaseParser.parseLatest("", "1.6.0"))
    }

    @Test
    fun `空数组返回 null`() {
        assertNull(GitHubReleaseParser.parseLatest("[]", "1.6.0"))
    }
}
