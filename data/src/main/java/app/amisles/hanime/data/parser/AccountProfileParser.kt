package app.amisles.hanime.data.parser

import android.util.Log
import app.amisles.hanime.domain.model.AccountProfile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账户资料编辑页解析器（官网 /user/{id}/edit）。
 *
 * 页面结构（来自账号资料.html）：
 *  - `<meta name="csrf-token" content="...">`：CSRF Token，更新档案时随表单提交。
 *  - 「编辑个人档案」表单：`input[name=name]`（用户名称）与 `input[name=email]`（电邮地址），
 *    其 value 即为当前已保存值，用于回填输入框。
 *  - 另含「更改密码」表单（type=password），本次不解析、不在客户端实现。
 */
@Singleton
class AccountProfileParser @Inject constructor() {

    fun parseEditPage(html: String, baseUrl: String): AccountProfile? {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val csrfToken = doc.selectFirst("meta[name=\"csrf-token\"]")?.attr("content")?.trim().orEmpty()
        val name = doc.selectFirst("input[name=name]")?.attr("value")?.trim().orEmpty()
        val email = doc.selectFirst("input[name=email]")?.attr("value")?.trim().orEmpty()
        Log.i("AccountDebug", "<<< Parsed account profile: name=$name, email=$email, csrf=${csrfToken.take(6)}…")
        if (csrfToken.isBlank() || name.isBlank()) return null
        return AccountProfile(name = name, email = email, csrfToken = csrfToken)
    }
}
