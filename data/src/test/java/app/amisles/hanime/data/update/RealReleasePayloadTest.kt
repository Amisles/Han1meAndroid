package app.amisles.hanime.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实的 GitHub API 响应**验证解析器。
 *
 * 下面的 JSON 取自 `GET /repos/Amisles/Han1meAndroid/releases`（字段已精简，
 * 但 tag_name / prerelease / assets / browser_download_url 全部保持原样）。
 *
 * 之所以要这条测试：之前的解析器测试用的是自造样本，而仓库坐标一度被写错，
 * 这类错误自造样本永远发现不了 —— 只有拿真实响应跑一遍，才能确认
 * 「字段名对不对、资产能不能挑出来、版本号能不能比较」。
 */
class RealReleasePayloadTest {

    private val payload = """[{"tag_name":"v1.6.0","name":"Han1meAndroid-v1.6.0","body":"## 新增 Added\r\n- **界面定制页**：新增独立的界面定制入口，可实时调整全局字号、缩略图宽高比、卡片圆角、列表缩略图宽度、栅格目标列宽与每区显示条数，并以示例卡片实时预览效果，每一项均可单独重置\r\n- **图片解码质量档**：省流 / 标准 / 高清三档，按实际显示尺寸的倍数解码 —— 省流档降低内存与流量占用\r\n- **首页分区级布局与顺序**：每个分区可单独设为自动 / 列表 / ","html_url":"https://github.com/Amisles/Han1meAndroid/releases/tag/v1.6.0","prerelease":false,"draft":false,"published_at":"2026-10-20T14:03:53Z","assets":[{"name":"Han1meAndroid-v1.6.0.apk","size":6310357,"browser_download_url":"https://github.com/Amisles/Han1meAndroid/releases/download/v1.6.0/Han1meAndroid-v1.6.0.apk"}]},{"tag_name":"v1.5.0","name":"Han1meAndroid-v1.5.0","body":"## 新增 Added\r\n- **搜索页标签多选筛选**：新增标签筛选面板，覆盖7 大类共 240 个官网标签，支持多选后一次性应用；开启「广泛匹配」后按任一命中标签返回结果\r\n- **搜索页发布日期筛选**：支持「过去 24 小时 / 2 天 / 1 周 / 1 个月 / 3 个月 / 1 年」快捷区间与按具体年份筛选\r\n- **搜索页时长筛选**：支持「1 / 5 / 10 / 20 / 30","html_url":"https://github.com/Amisles/Han1meAndroid/releases/tag/v1.5.0","prerelease":false,"draft":false,"published_at":"2026-09-25T09:29:53Z","assets":[{"name":"Han1meAndroid-v1.5.0.apk","size":6187942,"browser_download_url":"https://github.com/Amisles/Han1meAndroid/releases/download/v1.5.0/Han1meAndroid-v1.5.0.apk"}]},{"tag_name":"v1.4.2","name":"Hanime v1.4.2","body":"## 新增 Added\r\n- **详情页横屏自动全屏**：进入详情页播放后，设备横置即自动进入全屏，并按物理旋转方向（左 / 右横置）正确横置，避免进全屏瞬间画面上下颠倒\r\n- **搜索结果网格布局与列表 / 网格切换**：搜索结果新增网格视图，并支持列表 / 网格一键切换，浏览更高效\r\n- **详情页简介 / 评论切换动效**：简介与评论切换新增横向滑动动效与左右手势切换，操作更顺滑\r\n- **","html_url":"https://github.com/Amisles/Han1meAndroid/releases/tag/v1.4.2","prerelease":false,"draft":false,"published_at":"2026-09-10T12:57:25Z","assets":[{"name":"Han1meAndroid-v1.4.2.apk","size":5962329,"browser_download_url":"https://github.com/Amisles/Han1meAndroid/releases/download/v1.4.2/Han1meAndroid-v1.4.2.apk"}]},{"tag_name":"v1.4.1","name":"Hanime v1.4.1","body":"## 新增 Added\r\n- **下载存储路径选择器**：设置页「下载存储路径」改为应用内文件夹浏览器，支持逐级进入子目录、返回上级、新建文件夹并选择，彻底告别手动输入路径\r\n\r\n## 改进 Improved\r\n- **下载请求复用动态防盗链 Referer**：下载路径复用 `VideoAntiHotlink` 动态防盗链 `Referer`，避免源站防盗链导致下载失败，提升下载成功率\r\n\r\n#","html_url":"https://github.com/Amisles/Han1meAndroid/releases/tag/v1.4.1","prerelease":false,"draft":false,"published_at":"2026-09-04T12:25:00Z","assets":[{"name":"HanimeAndroid-v1.4.1.apk","size":5944109,"browser_download_url":"https://github.com/Amisles/Han1meAndroid/releases/download/v1.4.1/HanimeAndroid-v1.4.1.apk"}]},{"tag_name":"v1.4.0","name":"Hanime v1.4.0","body":"## 新增 Added\r\n- **播放器底部简易进度条**：底部控件新增可拖动的简易进度条，便于快速定位进度；同时修复点击控件误隐藏控制栏的问题，操作更跟手\r\n- **详情页全屏强制横屏**：进入全屏后强制横屏显示（移除了进入全屏后随设备旋转自动切换全屏的逻辑，改为手动控制）\r\n- **播放器视频直链防盗链兼容**：视频直链请求自动携带官网 `Referer`，修复源站视频无法播放的问题\r\n\r\n#","html_url":"https://github.com/Amisles/Han1meAndroid/releases/tag/v1.4.0","prerelease":false,"draft":false,"published_at":"2026-09-02T12:43:15Z","assets":[{"name":"Han1meAndroid-v1.4.0.apk","size":5923877,"browser_download_url":"https://github.com/Amisles/Han1meAndroid/releases/download/v1.4.0/Han1meAndroid-v1.4.0.apk"}]}]"""

    @Test
    fun `在当前版本 1_5_0 时能检出 1_6_0`() {
        val update = GitHubReleaseParser.parseLatest(payload, "1.5.0")
        assertNotNull(update)
        assertEquals("1.6.0", update!!.versionName)
        assertEquals("v1.6.0", update.tagName)
    }

    @Test
    fun `能挑出 APK 资产及其下载地址`() {
        val update = GitHubReleaseParser.parseLatest(payload, "1.5.0")
        assertNotNull(update)
        assertTrue(update!!.hasApk)
        assertTrue(update.apkUrl.endsWith("Han1meAndroid-v1.6.0.apk"))
        assertEquals(6310357L, update.apkSizeBytes)
    }

    @Test
    fun `已是最新版本时不提示`() {
        // 真实数据的最高版本就是 1.6.0
        assertNull(GitHubReleaseParser.parseLatest(payload, "1.6.0"))
    }

    @Test
    fun `主版本领先时能检出`() {
        val update = GitHubReleaseParser.parseLatest(payload, "0.9.0")
        assertNotNull(update)
        // 应取最高的 1.6.0，而不是数组第一个之外的低版本
        assertEquals("1.6.0", update!!.versionName)
    }

    @Test
    fun `versions 全是正式版，未被误判为预发布`() {
        val update = GitHubReleaseParser.parseLatest(payload, "1.4.0")
        assertNotNull(update)
        assertEquals(false, update!!.isPrerelease)
    }
}
