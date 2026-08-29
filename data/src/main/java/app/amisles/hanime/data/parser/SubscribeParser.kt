package app.amisles.hanime.data.parser

import android.util.Log
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.SubscribeResult
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订阅作者接口响应解析器。
 *
 * 官网 /subscribe 返回 JSON：
 * {
 *   "subscribeBtn": "<更新后的订阅表单 HTML>",
 *   "csrf_token": "..."
 * }
 *
 * 新订阅状态从 subscribeBtn 内的 input[name="subscribe-status"] 解析（"" = 未订阅，"1" = 已订阅），
 * 与服务端渲染逻辑一致；csrf_token 回传新的 CSRF Token 供后续请求刷新。
 */
@Singleton
class SubscribeParser @Inject constructor() {

    fun parse(json: String): SubscribeResult? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val subscribeBtnHtml = obj.optString("subscribeBtn", "")
            val subscribeStatus = if (subscribeBtnHtml.isNotEmpty()) {
                Jsoup.parse(subscribeBtnHtml)
                    .selectFirst("input[name=\"subscribe-status\"]")
                    ?.attr("value")?.trim() ?: ""
            } else {
                ""
            }
            val csrfToken = obj.optString("csrf_token", "")
            AppLogger.d("SubscribeParser", "subscribeStatus=$subscribeStatus")
            SubscribeResult(subscribeStatus = subscribeStatus, csrfToken = csrfToken)
        } catch (e: JSONException) {
            Log.i("SubscribeDebug", "!!! SubscribeParser JSONException: ${e.message} (response ${json.length} chars)")
            AppLogger.logError("SubscribeParser", "Failed to parse subscribe response: ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            Log.i("SubscribeDebug", "!!! SubscribeParser NullPointerException: ${e.message} (response ${json.length} chars)")
            AppLogger.logError("SubscribeParser", "Failed to parse subscribe response: ${e.message}", e)
            null
        }
    }
}
