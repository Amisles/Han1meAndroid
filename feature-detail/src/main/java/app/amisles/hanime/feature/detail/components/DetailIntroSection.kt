package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.VideoDetail

/**
 * 「简介」标签页内容：标题、作者行（头像 + 订阅按钮）、发布日期 / 体积、
 * 可展开的简介正文、操作按钮行（下载 / 收藏 / 分享）与标签。
 * 下载与分享的具体动作由调用方处理（需要页面级 context / 状态）。
 */
@Composable
internal fun DetailIntroSection(
    detail: VideoDetail,
    showDescription: Boolean,
    isFavorite: Boolean,
    isSubscribed: Boolean,
    isSubscribing: Boolean,
    onToggleDescription: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onAuthorPageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(
            text = detail.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (detail.author.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // 左侧：头像 + 作者名（点击进入作者页）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (detail.authorPageUrl.isNotEmpty()) {
                                onAuthorPageClick(detail.authorPageUrl)
                            } else {
                                onAuthorClick(detail.author)
                            }
                        }
                ) {
                    if (detail.authorAvatarUrl.isNotEmpty()) {
                        coil3.compose.AsyncImage(
                            model = detail.authorAvatarUrl,
                            contentDescription = stringResource(R.string.cd_author_avatar),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Text(
                        text = detail.author,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 右侧：订阅按钮（作者 ID 可解析时展示）
                if (detail.subscribeArtistId.isNotEmpty()) {
                    SubscribeButton(
                        isSubscribed = isSubscribed,
                        isSubscribing = isSubscribing,
                        onClick = onToggleSubscribe
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            if (detail.releaseDate.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.detail_release_date)}: ${detail.releaseDate}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (detail.fileSize.isNotEmpty()) {
                Text(
                    text = "${stringResource(R.string.detail_file_size)}: ${detail.fileSize}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (detail.description.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clickable { onToggleDescription() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showDescription)
                        stringResource(R.string.detail_collapse)
                    else
                        stringResource(R.string.detail_expand),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showDescription) "▲" else "▼",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showDescription) {
                Text(
                    text = detail.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .padding(bottom = 15.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            val favoriteTint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            DetailActionButton(
                icon = Icons.Default.Download,
                text = stringResource(R.string.detail_download),
                onClick = onDownloadClick,
                modifier = Modifier.weight(1f)
            )
            DetailActionButton(
                icon = Icons.Default.Favorite,
                text = stringResource(if (isFavorite) R.string.detail_unfavorite else R.string.detail_favorite),
                onClick = onToggleFavorite,
                modifier = Modifier.weight(1f),
                tint = favoriteTint
            )
            DetailActionButton(
                icon = Icons.Default.Share,
                text = stringResource(R.string.detail_share),
                onClick = onShareClick,
                modifier = Modifier.weight(1f)
            )
        }

        if (detail.tags.isNotEmpty()) {
            Text(
                text = stringResource(R.string.detail_tags),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            ExpandableTags(
                tags = detail.tags,
                onTagClick = onTagClick,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}
