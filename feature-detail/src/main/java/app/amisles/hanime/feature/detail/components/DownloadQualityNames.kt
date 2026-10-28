package app.amisles.hanime.feature.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.DownloadQuality

/**
 * 画质名的本地化文案。
 *
 * 站点返回的画质名是固定的中文（「全高清畫質 (1080p)」等），直接展示会让
 * 非中文界面出现中文残留。这里按已解析出的 [DownloadQuality.resolution] 映射，
 * 分辨率未知时回退到站点原文，保证不丢信息。
 */
@Composable
public fun localizedQualityName(quality: DownloadQuality): String =
    when (quality.resolution) {
        "1080p" -> stringResource(R.string.download_quality_1080p)
        "720p" -> stringResource(R.string.download_quality_720p)
        "480p" -> stringResource(R.string.download_quality_480p)
        else -> quality.quality
    }
