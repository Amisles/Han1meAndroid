package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary

@Composable
fun VideoCard(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {}
) {
    val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
    val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }

    Column(
        modifier = Modifier
            .width(200.dp)
            .height(210.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                emoji = emoji,
                gradient = gradient,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp)
        ) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HanimeTextPrimary,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.weight(1f))
            if (video.author.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.author,
                        fontSize = 11.sp,
                        color = HanimePrimary,
                        modifier = Modifier.clickable { onAuthorClick(video.author) }
                    )
                    if (video.publishTime.isNotEmpty()) {
                        Text(
                            text = " • ${video.publishTime}",
                            fontSize = 11.sp,
                            color = HanimeTextSecondary
                        )
                    }
                }
            }
        }
    }
}