package app.amisles.hanime.data.parser

import org.jsoup.Jsoup

object LoginParser {

    fun parseCsrfToken(html: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)
        val meta = doc.selectFirst("meta[name=\"csrf-token\"]")
        if (meta != null) {
            val content = meta.attr("content")
            if (content.isNotBlank()) return content
        }
        val input = doc.selectFirst("input[name=\"_token\"]")
        if (input != null) {
            val v = input.attr("value")
            if (v.isNotBlank()) return v
        }
        val match = Regex("name=\"_token\"\\s+value=\"([^\"]+)\"").find(html)
        return match?.groupValues?.getOrNull(1)
    }

    fun parseLoginFailed(html: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)
        val alertBox = doc.selectFirst(".alert-danger, .alert-error, .invalid-feedback, div.alert[class*=danger]")
        if (alertBox != null) {
            val text = alertBox.text().trim()
            if (text.isNotBlank()) return text.take(200)
        }
        val helpText = doc.selectFirst("span.help-block, .text-danger, #email-error, #password-error")
        if (helpText != null) {
            val t = helpText.text().trim()
            if (t.isNotBlank()) return t.take(200)
        }
        return null
    }

    fun parseUserIdFromSetCookies(setCookies: List<String>): String {
        for (sc in setCookies) {
            val kv = sc.substringBefore(';')
            if (!kv.contains('=')) continue
            val name = kv.substringBefore('=').trim()
            val value = kv.substringAfter('=').trim()
            if (name.contains("user_id", ignoreCase = true) ||
                name.contains("uid", ignoreCase = true)) return value
        }
        return ""
    }
}