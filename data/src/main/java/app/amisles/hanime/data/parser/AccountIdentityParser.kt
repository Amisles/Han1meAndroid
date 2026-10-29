package app.amisles.hanime.data.parser

import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 识别当前登录用户的数字 ID。
 *
 * 登录 Cookie 里没有用户 ID（站点只下发会话与 remember 令牌），而账户资料页必须先知道 ID 才能
 * 拼出 /user/{id}/edit。登录后的页面头部会渲染本人的「编辑个人档案」入口，该链接只属于当前用户，
 * 因此作为首选判据；meta / 隐藏域 / JS 变量作为兜底。
 */
@Singleton
class AccountIdentityParser @Inject constructor() {

    fun parseCurrentUserId(html: String): String? {
        if (html.isBlank()) return null
        ACCOUNT_EDIT_LINK.find(html)?.groupValues?.getOrNull(1)?.toNumericId()?.let { return it }
        val doc = Jsoup.parse(html)
        for (name in META_NAMES) {
            doc.selectFirst("meta[name=\"$name\"]")?.attr("content")?.toNumericId()?.let { return it }
        }
        for (name in INPUT_NAMES) {
            doc.selectFirst("input[name=\"$name\"]")?.attr("value")?.toNumericId()?.let { return it }
        }
        doc.selectFirst("[data-user-id]")?.attr("data-user-id")?.toNumericId()?.let { return it }
        for (pattern in JS_PATTERNS) {
            pattern.find(html)?.groupValues?.getOrNull(1)?.toNumericId()?.let { return it }
        }
        return null
    }

    /** 只接受十进制正整数：0 是未登录占位，负数与非数字一律视为无效。 */
    private fun String?.toNumericId(): String? {
        val value = this?.trim().orEmpty()
        if (value.isEmpty() || !value.all { it.isDigit() }) return null
        return value.toLongOrNull()?.takeIf { it > 0 }?.toString()
    }

    private companion object {
        val ACCOUNT_EDIT_LINK = Regex("""/user/(\d+)/edit""")
        val META_NAMES = listOf("user-id", "user_id", "userId", "auth-user-id", "current-user-id")
        val INPUT_NAMES = listOf("comment-user-id", "user-id", "user_id", "userId")
        val JS_PATTERNS = listOf(
            Regex("""(?:window\.)?(?:userId|user_id|currentUserId|authUserId|auth_user_id|current_user_id)\s*[:=]\s*"?(\d+)""")
        )
    }
}
