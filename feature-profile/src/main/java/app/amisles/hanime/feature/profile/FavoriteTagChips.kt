package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_OVERLAY_TAGS = 2

/**
 * 缩略图上的标签角标。
 *
 * 叠在图片上而非排在文字列里：卡片高度由文字行数决定，与是否有标签无关，
 * 列表因此保持等高的节奏。最多展示 [MAX_OVERLAY_TAGS] 个，其余以 +N 收口，
 * 避免窄缩略图上标签把画面盖住。
 *
 * 底色用固定的半透明黑 + 白字：缩略图内容不可控，任何跟随主题的前景色
 * 都可能在浅色封面上糊掉，压在图片上的角标必须自带对比度。
 */
@Composable
internal fun FavoriteTagOverlay(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val visible = tags.take(MAX_OVERLAY_TAGS)
    val hidden = tags.size - visible.size

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        visible.forEach { tag ->
            TagBadge(text = tag)
        }
        if (hidden > 0) {
            TagBadge(text = "+$hidden")
        }
    }
}

@Composable
private fun TagBadge(text: String) {
    Text(
        text = text,
        fontSize = 9.sp,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}
