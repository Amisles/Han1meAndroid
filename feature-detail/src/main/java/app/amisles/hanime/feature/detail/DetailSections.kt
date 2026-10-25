package app.amisles.hanime.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.components.VideoThumbnail
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
                        onVideoClick = actions.onVideoClick,
                        onAuthorClick = actions.onAuthorClick
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

/** 推荐区的一行网格：由调用方按行 emit 成独立 Lazy item，保证复用与懒加载。 */
@Composable
private fun DetailRelatedGridRow(
    row: List<HanimeVideo>,
    columns: Int,
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
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
                onAuthorClick = onAuthorClick,
                modifier = Modifier.weight(1f)
            )
        }
        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

/** 网格版推荐卡：封面铺满卡片宽度，标题固定两行，作者行可选。 */
@Composable
private fun DetailRelatedGridCard(
    video: HanimeVideo,
    onClick: () -> Unit,
    onAuthorClick: (String) -> Unit,
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
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true,
                targetWidth = maxWidth,
                aspectRatioOverride = ratio
            )
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
        if (video.author.isNotEmpty()) {
            Text(
                text = video.author,
                fontSize = cfg.gridMetaFontSize,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 6.dp)
                    .clickable { onAuthorClick(video.author) }
            )
        }
    }
}

