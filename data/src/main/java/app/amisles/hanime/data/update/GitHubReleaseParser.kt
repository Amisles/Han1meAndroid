package app.amisles.hanime.data.update

import app.amisles.hanime.domain.model.AppUpdate
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析 GitHub Releases API 的响应，挑出「最新的、且适用于本机」的那个发行版。
 *
 * API 返回的是**发行版数组**（按 created_at 倒序，但发布顺序与版本号顺序未必一致：
 * 可能先发了 1.7.0 再补发 1.6.1 修复版），因此不能简单取第一个 —— 必须用版本号比较。
 */
internal object GitHubReleaseParser {

    /** 单个发行版中，我们关心的 APK 资产筛选条件。 */
    private fun JSONArray?.pickApk(): JSONObject? {
        if (this == null) return null
        for (i in 0 until length()) {
            val asset = optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            // 跳过明显不是给手机装的产物
            val lower = name.lowercase()
            if (lower.contains("unsigned") || lower.contains("sources")) continue
            if (asset.optString("browser_download_url", "").isBlank()) continue
            return asset
        }
        return null
    }

    /**
     * 从一次响应中选出「比 [currentVersion] 新且最靠前」的发行版。
     *
     * @param includePrerelease 是否接受 prerelease / draft。draft 一律排除（未发布，不该提示）。
     * @return 解析成功且有更新时返回 [AppUpdate]；解析失败返回 null。
     */
    fun parseLatest(
        json: String,
        currentVersion: String,
        includePrerelease: Boolean = false
    ): AppUpdate? {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
        var best: AppUpdate? = null
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            // draft 是未发布的草稿，任何人都看不到，也不该拿来提示
            if (obj.optBoolean("draft", false)) continue
            val isPrerelease = obj.optBoolean("prerelease", false)
            if (isPrerelease && !includePrerelease) continue

            val tag = obj.optString("tag_name", "").trim()
            if (tag.isEmpty()) continue
            val version = tag.removePrefix("v").removePrefix("V")
            // 无法解析版本号的发行版直接跳过：宁可漏提示，也不能把一个看不懂的标签推给用户
            if (VersionCompare.parse(version) == null) continue
            if (!VersionCompare.isNewer(version, currentVersion)) continue

            val assets = obj.optJSONArray("assets")
            val apk = assets?.pickApk()
            val candidate = AppUpdate(
                tagName = tag,
                versionName = version,
                title = obj.optString("name", "").ifBlank { tag },
                releaseNotes = obj.optString("body", ""),
                htmlUrl = obj.optString("html_url", ""),
                isPrerelease = isPrerelease,
                publishedAt = obj.optString("published_at", ""),
                apkUrl = apk?.optString("browser_download_url", "").orEmpty(),
                apkSizeBytes = apk?.optLong("size", 0L) ?: 0L
            )

            val current = best
            if (current == null || VersionCompare.isNewer(candidate.versionName, current.versionName)) {
                best = candidate
            }
        }
        return best
    }
}
