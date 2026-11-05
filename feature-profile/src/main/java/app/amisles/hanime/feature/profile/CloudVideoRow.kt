package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.components.PrivacyAwareAsyncImage
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.domain.model.HanimeVideo

@Composable
internal fun CloudVideoRow(video: HanimeVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .surfaceEdge(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            PrivacyAwareAsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.author.isNotBlank()) {
                Text(
                    text = video.author,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
