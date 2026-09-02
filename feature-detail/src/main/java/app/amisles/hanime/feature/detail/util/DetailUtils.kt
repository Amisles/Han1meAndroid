package app.amisles.hanime.feature.detail.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.domain.model.VideoDetail

/**
 * 根据持久化画质偏好挑选初始播放源：偏好非空且存在对应分辨率时使用，否则回退默认源。
 */
internal fun pickInitialSourceUrl(detail: VideoDetail, preferredQuality: String): String {
    if (preferredQuality.isNotBlank()) {
        val matched = detail.videoSources.firstOrNull { it.resolution == preferredQuality }
        if (matched != null) return matched.url
    }
    return detail.defaultSourceUrl
}

/**
 * 调用系统分享面板分享视频（标题 + 链接）。
 */
internal fun shareVideo(context: Context, title: String, url: String) {
    val shareText = if (url.isNotEmpty()) "$title\n$url" else title
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.cd_share_video))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/**
 * 视频卡片占位配色：按视频 id 稳定散列取渐变与 emoji，保证同一视频每次进入配色一致。
 */
internal fun videoGradient(id: String): Pair<Color, Color> =
    gradients.getOrElse(id.hashCode() % gradients.size) { gradients[0] }

internal fun videoEmoji(id: String): String =
    emojis.getOrElse(id.hashCode() % emojis.size) { emojis[0] }
