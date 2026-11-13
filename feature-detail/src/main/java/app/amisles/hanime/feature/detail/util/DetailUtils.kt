package app.amisles.hanime.feature.detail.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.VideoDetail

/** 根据持久化画质偏好挑选初始播放源：偏好非空且存在对应分辨率时使用，否则回退默认源。 */
internal fun pickInitialSourceUrl(detail: VideoDetail, preferredQuality: String): String {
    if (preferredQuality.isNotBlank()) {
        val matched = detail.videoSources.firstOrNull { it.resolution == preferredQuality }
        if (matched != null) return matched.url
    }
    return detail.defaultSourceUrl
}

/** 调用系统分享面板分享视频：标题 + 应用深链 + 网页地址，装有本应用的一方点深链可直达。 */
public fun shareVideo(context: Context, title: String, url: String) {
    val deepLink = watchDeepLink(url)
    val shareText = buildString {
        if (title.isNotEmpty()) append(title).append('\n')
        if (deepLink != null) append(deepLink).append('\n')
        if (url.isNotEmpty()) append(url)
    }.trim()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.cd_share_video))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // 极端裁剪的 ROM 上可能不存在任何可处理 ACTION_SEND 的应用，用 runCatching 避免抛 ActivityNotFoundException
    runCatching { context.startActivity(chooser) }
}

private fun watchDeepLink(url: String): String? =
    if (url.startsWith("http://") || url.startsWith("https://")) {
        "hanime://video?url=" + Uri.encode(url)
    } else {
        null
    }
