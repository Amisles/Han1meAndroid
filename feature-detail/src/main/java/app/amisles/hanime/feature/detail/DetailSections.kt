package app.amisles.hanime.feature.detail

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.feature.detail.comment.CommentSection
import app.amisles.hanime.feature.detail.comment.ReplyTarget
import app.amisles.hanime.feature.detail.components.DetailIntroSection
import app.amisles.hanime.feature.detail.components.DetailPlaylistHeader
import app.amisles.hanime.feature.detail.components.DetailPlaylistVideos
import app.amisles.hanime.feature.detail.components.DetailRelatedVideoCard
import app.amisles.hanime.feature.detail.components.DetailTabBar
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision

/** 详情页「其余组件」（Tab 条 / 简介 / 播放集合 / 相关推荐 / 评论）所需的只读状态。 */
internal data class DetailRestState(
    // 0 = 简介（除评论外的全部信息），1 = 评论
    val selectedTab: Int,
    val showDescription: Boolean,
    val isFavorite: Boolean,
    val isSubscribed: Boolean,
    val isSubscribing: Boolean,
    val comments: List<Comment>,
    val isLoadingComments: Boolean,
    val commentsError: String?,
    val repliesCache: Map<String, List<Reply>>,
    val loadingReplies: Set<String>,
    val repliesError: Map<String, String?>,
    val expandedReplies: Set<String>,
    val isLogin: Boolean,
    val isPostingComment: Boolean,
    val postCommentError: String?,
    val likingComments: Set<String>,
    val activeReplyTarget: ReplyTarget?,
    val isPostingReply: Boolean,
    val replyError: String?,
    val relatedGrid: Boolean,
    val relatedColumns: Int
)

/** 「其余组件」的交互回调；依赖页面级状态的判断由 DetailScreen 实现后传入，组件层只负责触发。 */
internal data class DetailRestActions(
    val onTabSelected: (Int) -> Unit,
    val onToggleDescription: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onShareClick: () -> Unit,
    val onToggleSubscribe: () -> Unit,
    val onTagClick: (String) -> Unit,
    val onAuthorClick: (String) -> Unit,
    val onAuthorPageClick: (String) -> Unit,
    val onVideoClick: (String) -> Unit,
    val onLoadComments: (force: Boolean) -> Unit,
    val onLoadReplies: (String) -> Unit,
    val onToggleReplies: (String) -> Unit,
    val onPostComment: (String) -> Unit,
    val onClearPostCommentError: () -> Unit,
    val onToggleCommentLike: (Comment) -> Unit,
    val onStartReply: (String, String?) -> Unit,
    val onSubmitReply: (String) -> Unit,
    val onCancelReply: () -> Unit,
    val onClearReplyError: () -> Unit,
    val onNavigateToLogin: () -> Unit
)

/** 详情页「其余组件」：手机单列与平板右栏共用的 LazyColumn 内容。 */
internal fun LazyListScope.detailRestItems(
    detail: VideoDetail,
    state: DetailRestState,
    actions: DetailRestActions
) {
    // 「简介 / 评论」分段选择条 + 内容区（整体支持左右滑动切换）
    item(key = "detail_tab_block") {
        DetailTabBlock(detail = detail, state = state, actions = actions)
    }

    if (state.selectedTab == 0) {
        val related = detail.relatedVideos.distinctBy { it.id }
        if (state.relatedGrid) {
            val columns = state.relatedColumns
            related.chunked(columns).forEachIndexed { rowIndex, row ->
                item(key = "detail_related_row_" + rowIndex) {
                    DetailRelatedGridRow(
                        row = row,
                        columns = columns,
                        currentTab = state.selectedTab,
                        onSelectTab = actions.onTabSelected,
                        onVideoClick = actions.onVideoClick
                    )
                }
            }
        } else {
            // 先按 id 去重再作为 Lazy key，避免站点返回重复条目时 key 冲突崩溃
            items(related, key = { it.id }) { video ->
                DetailRelatedRow(
                    video = video,
                    currentTab = state.selectedTab,
                    onSelectTab = actions.onTabSelected,
                    onVideoClick = actions.onVideoClick,
                    onAuthorClick = actions.onAuthorClick
                )
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/** 左右滑动切页的触发阈值 */
private val DetailTabSwipeThreshold = 60.dp

/** 「简介 / 评论」左右滑动切页手势。 */
private fun Modifier.detailTabSwipe(
    currentTab: Int,
    thresholdPx: Float,
    onSelectTab: (Int) -> Unit
): Modifier = this.pointerInput(currentTab) {
    var dragAccum = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragAccum = 0f },
        onDragCancel = { dragAccum = 0f },
        onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
        onDragEnd = {
            when {
                // 向左滑 → 评论；向右滑 → 简介
                dragAccum < -thresholdPx -> onSelectTab(1)
                dragAccum > thresholdPx -> onSelectTab(0)
            }
            dragAccum = 0f
        }
    )
}

/**
 * 相关推荐单条：卡片本体 + 外层的左右滑动切页手势。手势加在外层，
 * 是为了让「简介」页的滑动区域覆盖到相关推荐。
 */
@Composable
private fun DetailRelatedRow(
    video: HanimeVideo,
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val thresholdPx = with(LocalDensity.current) { DetailTabSwipeThreshold.toPx() }
    Box(
        modifier = Modifier.detailTabSwipe(
            currentTab = currentTab,
            thresholdPx = thresholdPx,
            onSelectTab = onSelectTab
        )
    ) {
        DetailRelatedVideoCard(
            video = video,
            onVideoClick = onVideoClick,
            onAuthorClick = onAuthorClick
        )
    }
}

@Composable
internal fun DetailTabBlock(
    detail: VideoDetail,
    state: DetailRestState,
    actions: DetailRestActions
) {
    val density = LocalDensity.current
    // 切页阈值取 60dp 比抽屉手势（120dp）更跟手
    val swipeThresholdPx = remember(density) { with(density) { DetailTabSwipeThreshold.toPx() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .detailTabSwipe(
                currentTab = state.selectedTab,
                thresholdPx = swipeThresholdPx,
                onSelectTab = actions.onTabSelected
            )
    ) {
        DetailTabBar(
            selectedTab = state.selectedTab,
            onTabSelected = actions.onTabSelected
        )

        AnimatedContent(
            targetState = state.selectedTab,
            transitionSpec = {
                // 简介 → 评论（目标在右）时新内容自右侧滑入，反向则自左侧滑入
                val direction = if (targetState > initialState) 1 else -1
                val durationMillis = 280
                (slideInHorizontally(animationSpec = tween(durationMillis)) { fullWidth ->
                    direction * fullWidth
                } + fadeIn(animationSpec = tween(durationMillis))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(durationMillis)) { fullWidth ->
                        -direction * fullWidth
                    } + fadeOut(animationSpec = tween(durationMillis)))
            },
            label = "detail_tab_content"
        ) { tab ->
            if (tab == 0) {
                DetailIntroPage(detail = detail, state = state, actions = actions)
            } else {
                DetailCommentsPage(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun DetailIntroPage(
    detail: VideoDetail,
    state: DetailRestState,
    actions: DetailRestActions
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailIntroSection(
            detail = detail,
            showDescription = state.showDescription,
            isFavorite = state.isFavorite,
            isSubscribed = state.isSubscribed,
            isSubscribing = state.isSubscribing,
            onToggleDescription = actions.onToggleDescription,
            onToggleSubscribe = actions.onToggleSubscribe,
            onDownloadClick = actions.onDownloadClick,
            onToggleFavorite = actions.onToggleFavorite,
            onShareClick = actions.onShareClick,
            onTagClick = actions.onTagClick,
            onAuthorClick = actions.onAuthorClick,
            onAuthorPageClick = actions.onAuthorPageClick
        )

        val playlist = detail.playlist
        if (playlist != null && playlist.videos.isNotEmpty()) {
            DetailPlaylistHeader(playlist = playlist, onAuthorClick = actions.onAuthorClick)
            DetailPlaylistVideos(playlist = playlist, onVideoClick = actions.onVideoClick)
        }
    }
}

/** 「评论」页：评论输入框 + 加载中 / 错误 / 空 / 列表状态。 */
@Composable
private fun DetailCommentsPage(
    state: DetailRestState,
    actions: DetailRestActions
) {
    CommentSection(
        comments = state.comments,
        isLoading = state.isLoadingComments,
        error = state.commentsError,
        onRetry = { actions.onLoadComments(true) },
        repliesCache = state.repliesCache,
        loadingReplies = state.loadingReplies,
        repliesError = state.repliesError,
        onLoadReplies = actions.onLoadReplies,
        expandedReplies = state.expandedReplies,
        onToggleExpand = actions.onToggleReplies,
        isLogin = state.isLogin,
        isPostingComment = state.isPostingComment,
        postCommentError = state.postCommentError,
        onPostComment = actions.onPostComment,
        onClearPostError = actions.onClearPostCommentError,
        onToggleLike = actions.onToggleCommentLike,
        likingComments = state.likingComments,
        activeReplyCommentId = state.activeReplyTarget?.commentId,
        replyPrefill = state.activeReplyTarget?.replyToUsername?.let { "@$it " } ?: "",
        isPostingReply = state.isPostingReply,
        replyError = state.replyError,
        onStartReply = actions.onStartReply,
        onSendReply = actions.onSubmitReply,
        onCancelReply = actions.onCancelReply,
        onClearReplyError = actions.onClearReplyError,
        onNavigateToLogin = actions.onNavigateToLogin
    )
}


/** 按卡片实测宽度限制解码尺寸，避免整页竖版封面按原图解码。 */
private fun thumbnailRequest(
    context: Context,
    density: Density,
    url: String,
    width: Dp,
    ratio: Float
): ImageRequest {
    val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val heightPx = (widthPx / ratio).toInt().coerceAtLeast(1)
    return ImageRequest.Builder(context)
        .data(url)
        .size(Dimension.Pixels(widthPx), Dimension.Pixels(heightPx))
        .precision(Precision.INEXACT)
        .build()
}

/** 推荐区的一行网格：由调用方按行 emit 成独立 Lazy item，保证复用与懒加载。 */
@Composable
private fun DetailRelatedGridRow(
    row: List<HanimeVideo>,
    columns: Int,
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    onVideoClick: (String) -> Unit
) {
    val cfg = LocalUiConfig.current
    val gap = cfg.gridGap
    val thresholdPx = with(LocalDensity.current) { DetailTabSwipeThreshold.toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = gap / 2)
            .detailTabSwipe(
                currentTab = currentTab,
                thresholdPx = thresholdPx,
                onSelectTab = onSelectTab
            ),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        row.forEach { video ->
            DetailRelatedGridCard(
                video = video,
                onClick = { onVideoClick(video.videoUrl) },
                modifier = Modifier.weight(1f)
            )
        }
        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

/** 网格版推荐卡：封面铺满卡片宽度，标题固定两行。 */
@Composable
private fun DetailRelatedGridCard(
    video: HanimeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cfg = LocalUiConfig.current
    val shape = RoundedCornerShape(cfg.cardCornerRadius)
    val ratio = if (video.isPortraitCover) 134f / 197f else cfg.thumbnailAspectRatio
    Column(
        modifier = modifier
            .clip(shape)
            .clickable { onClick() }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                val context = LocalContext.current
                val density = LocalDensity.current
                AsyncImage(
                    model = remember(video.thumbnailUrl, maxWidth, ratio, density) {
                        thumbnailRequest(context, density, video.thumbnailUrl, maxWidth, ratio)
                    },
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            video.duration.takeIf { it.isNotEmpty() }?.let { duration ->
                ThumbnailBadge(
                    text = duration,
                    fontSize = cfg.badgeFontSize,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                )
            }
            if (video.likeRate.isNotEmpty() || video.viewCount.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (video.likeRate.isNotEmpty()) {
                        ThumbnailBadge(
                            text = video.likeRate,
                            fontSize = cfg.badgeFontSize,
                            icon = true
                        )
                    }
                    if (video.viewCount.isNotEmpty()) {
                        ThumbnailBadge(text = video.viewCount, fontSize = cfg.badgeFontSize)
                    }
                }
            }
        }
        Text(
            text = video.title,
            fontSize = cfg.gridTitleFontSize,
            lineHeight = cfg.gridTitleLineHeight,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cfg.cardTitleTopPadding)
        )
    }
}

/** 封面角标：半透明黑底 + 白字，深浅色下均能压住任意亮度的缩略图。 */
@Composable
private fun ThumbnailBadge(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    icon: Boolean = false
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
