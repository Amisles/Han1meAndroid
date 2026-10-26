package app.amisles.hanime.core.ui.components

import androidx.compose.runtime.Composable
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.HanimeVideo

/** 竖版封面（里番 / 泡面番）的宽高比，与横版缩略图区分渲染。 */
const val PORTRAIT_COVER_ASPECT_RATIO = 134f / 197f

/**
 * 卡片封面比例：竖版封面用固定竖图比例，其余沿用界面配置。
 *
 * 卡片与缩略图共用这一个来源，避免各处分别判断 isPortraitCover 而漏改。
 */
@Composable
fun videoCoverAspectRatio(video: HanimeVideo): Float =
    if (video.isPortraitCover) PORTRAIT_COVER_ASPECT_RATIO else LocalUiConfig.current.thumbnailAspectRatio
