package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.R
import androidx.compose.material3.MaterialTheme

@Composable
fun FavoriteScreen(
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {}
) {
    val viewModel: FavoriteViewModel = hiltViewModel()
    val context = LocalContext.current
    val favorites by viewModel.favorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var deleteTargetTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedIds = emptySet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                    } else {
                        onBackClick()
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isSelectionMode) "取消" else "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isSelectionMode) "已选 ${selectedIds.size} 项" else stringResource(R.string.profile_favorites),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (!isSelectionMode && favorites.isNotEmpty()) {
                Text(
                    text = "管理",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { isSelectionMode = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else if (isSelectionMode) {
                Text(
                    text = "全选",
                    fontSize = 14.sp,
                    color = if (selectedIds.size == favorites.size) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            selectedIds = if (selectedIds.size == favorites.size) {
                                emptySet()
                            } else {
                                favorites.map { it.id }.toSet()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "❤️",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = stringResource(R.string.favorite_empty),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.favorite_empty_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites) { video ->
                    val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
                    val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }
                    val isSelected = video.id in selectedIds

                    FavoriteVideoItem(
                        video = video,
                        gradient = gradient,
                        emoji = emoji,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onVideoClick = {
                            if (isSelectionMode) {
                                selectedIds = if (isSelected) {
                                    selectedIds - video.id
                                } else {
                                    selectedIds + video.id
                                }
                            } else {
                                onVideoClick(video.videoUrl)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                deleteTargetId = video.id
                                deleteTargetTitle = video.title
                                showDeleteConfirm = true
                            }
                        },
                        onCheckedChange = { checked ->
                            selectedIds = if (checked) {
                                selectedIds + video.id
                            } else {
                                selectedIds - video.id
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (isSelectionMode && selectedIds.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选择 ${selectedIds.size} 项",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.common_delete),
                    fontSize = 14.sp,
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            deleteTargetId = null // 批量删除标记
                            deleteTargetTitle = "${selectedIds.size} 个收藏"
                            showDeleteConfirm = true
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showDeleteConfirm = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.favorite_delete_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "确定删除 \"$deleteTargetTitle\" 吗？",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDeleteConfirm = false }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.common_delete),
                        fontSize = 14.sp,
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (deleteTargetId != null) {
                                    // 单个删除
                                    viewModel.removeFavorite(deleteTargetId!!)
                                } else {
                                    // 批量删除
                                    viewModel.removeFavorites(selectedIds.toList())
                                    isSelectionMode = false
                                }
                                showDeleteConfirm = false
                            }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteVideoItem(
    video: FavoriteVideo,
    gradient: Pair<Color, Color>,
    emoji: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 8.dp)
            .background(if (isSelectionMode && isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onVideoClick,
                onLongClick = onLongClick
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(24.dp)
            )
        }

        VideoThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            emoji = emoji,
            gradient = gradient,
            duration = video.duration,
            likeRate = video.likeRate,
            viewCount = video.viewCount,
            crop = true,
            modifier = Modifier
                .width(110.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.author.isNotEmpty()) {
                Text(
                    text = video.author,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (video.duration.isNotEmpty()) {
                    Text(
                        text = video.duration,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (video.likeRate.isNotEmpty()) {
                    Text(
                        text = video.likeRate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (video.viewCount.isNotEmpty()) {
                    Text(
                        text = video.viewCount,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
