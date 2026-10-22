package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 收藏卡片上的标签行。
 *
 * 用 primary 的 12% 透明底 + primary 文字，深浅色主题下都有足够对比度；
 * 单行横向滚动而非换行，避免标签多时把卡片高度撑开、破坏列表节奏。
 */
@Composable
internal fun FavoriteTagChips(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = tag,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
