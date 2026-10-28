package app.amisles.hanime.feature.profile

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.components.FullScreenOverlayDialog
import app.amisles.hanime.core.ui.theme.HanimeDanger
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.core.ui.R
import androidx.compose.material3.MaterialTheme

private val ROW_THUMB_WIDTH = 140.dp

@Composable
fun FavoriteScreen(
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {}
) {
    val viewModel: FavoriteViewModel = hiltViewModel()
    val context = LocalContext.current
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // null 表示「全部分组」；空串表示「未分组」
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingVideoId by remember { mutableStateOf("") }
    var editingVideoTitle by remember { mutableStateOf("") }
    var editingTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingGroupId by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    val visibleFavorites = remember(favorites, selectedGroupId, searchQuery) {
        val keyword = searchQuery.trim()
        favorites.filter { video ->
            val groupOk = when (selectedGroupId) {
                null -> true
                else -> video.groupId == selectedGroupId
            }
            val queryOk = keyword.isEmpty() ||
                video.title.contains(keyword, ignoreCase = true) ||
                video.author.contains(keyword, ignoreCase = true) ||
                video.tags.any { it.contains(keyword, ignoreCase = true) }
            groupOk && queryOk
        }
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var deleteTargetTitle by remember { mutableStateOf("") }
    val batchDeleteTitle = stringResource(R.string.favorite_batch_count, selectedIds.size)

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
                    contentDescription = stringResource(
                        if (isSelectionMode) R.string.common_cancel else R.string.common_back
                    ),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isSelectionMode) {
                    stringResource(R.string.history_selected_count, selectedIds.size)
                } else {
                    stringResource(R.string.profile_favorites)
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (!isSelectionMode && favorites.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.common_manage),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { isSelectionMode = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else if (isSelectionMode) {
                // 全选只作用于当前过滤视图（分组 / 搜索）内的条目，与列表渲染同源：
                // 若用全量 favorites，过滤视图外的收藏会被一并选中删除——用户按可见集合
                // 理解「全选」，屏幕外的条目不该陪葬。取消全选同理只移除可见条目，
                // 此前因切换分组/搜索而保留的显式选中项不受影响。
                val allVisibleSelected = visibleFavorites.isNotEmpty() &&
                    visibleFavorites.all { it.id in selectedIds }
                Text(
                    text = stringResource(R.string.common_select_all),
                    fontSize = 14.sp,
                    color = if (allVisibleSelected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val visibleIds = visibleFavorites.map { it.id }.toSet()
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        FavoriteSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        FavoriteGroupBar(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onSelect = { selectedGroupId = it },
            onCreateGroup = { showCreateGroup = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

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
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .size(48.dp)
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
                if (visibleFavorites.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.favorite_search_empty),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(visibleFavorites, key = { it.id }) { video ->
                    val isSelected = video.id in selectedIds

                    FavoriteVideoItem(
                        video = video,
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
                                editingVideoId = video.id
                                editingVideoTitle = video.title
                                editingTags = video.tags
                                editingGroupId = video.groupId
                                showEditDialog = true
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

    if (showCreateGroup) {
        GroupNameDialog(
            title = stringResource(R.string.favorite_group_new),
            onDismiss = { showCreateGroup = false },
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateGroup = false
            }
        )
    }

    if (showEditDialog) {
        FavoriteEditDialog(
            title = editingVideoTitle,
            tags = editingTags,
            groups = groups,
            selectedGroupId = editingGroupId,
            onDismiss = { showEditDialog = false },
            onTagsChange = { editingTags = it },
            onGroupChange = { editingGroupId = it },
            onSave = {
                viewModel.setTags(editingVideoId, editingTags)
                viewModel.moveToGroup(editingVideoId, editingGroupId)
                showEditDialog = false
            }
        )
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
                    text = stringResource(R.string.search_selected_count, selectedIds.size),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.common_delete),
                    fontSize = 14.sp,
                    color = HanimeDanger,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            deleteTargetId = null // 批量删除标记
                            // 文案在组合期取值：clickable 的 lambda 不是组合型作用域，不能直接调 stringResource
                            deleteTargetTitle = batchDeleteTitle
                            showDeleteConfirm = true
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        FullScreenOverlayDialog(onDismiss = { showDeleteConfirm = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
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
                    text = stringResource(R.string.favorite_delete_confirm_message, deleteTargetTitle),
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
                        color = HanimeDanger,
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
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 3.dp)
            .background(if (isSelectionMode && isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .surfaceEdge(RoundedCornerShape(8.dp))
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

        Box(
            modifier = Modifier
                .width(ROW_THUMB_WIDTH)
                .aspectRatio(16f / 9f)
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true,
                targetWidth = ROW_THUMB_WIDTH,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            )
            // 标签叠在缩略图底部：不占用文字列高度，有标签与无标签的卡片高度一致
            if (video.tags.isNotEmpty()) {
                FavoriteTagOverlay(
                    tags = video.tags,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (video.author.isNotEmpty()) {
                Text(
                    text = video.author,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (video.duration.isNotEmpty()) {
                    Text(
                        text = video.duration,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (video.likeRate.isNotEmpty()) {
                    Text(
                        text = video.likeRate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (video.viewCount.isNotEmpty()) {
                    Text(
                        text = video.viewCount,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
