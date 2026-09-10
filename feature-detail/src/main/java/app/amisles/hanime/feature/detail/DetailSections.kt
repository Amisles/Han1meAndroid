package app.amisles.hanime.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.feature.detail.comment.CommentSection
import app.amisles.hanime.feature.detail.components.DetailIntroSection
import app.amisles.hanime.feature.detail.components.DetailPlaylistHeader
import app.amisles.hanime.feature.detail.components.DetailPlaylistVideos
import app.amisles.hanime.feature.detail.components.DetailRelatedVideoCard
import app.amisles.hanime.feature.detail.components.DetailTabBar

/**
 * 详情页「其余组件」（Tab 条 / 简介 / 播放集合 / 相关推荐 / 评论）所需的只读状态。
 * 原本散落在 DetailScreen 组合内的局部变量，随 detailRestItems 一起抽取后集中传递。
 */
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
    val replyError: String?
)

/**
 * 「其余组件」的用户交互回调。评论 tab 的懒加载判断等依赖页面级状态的逻辑
 * 由 DetailScreen 实现后传入，组件层只负责触发。
 */
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

/**
 * 详情页「其余组件」：手机单列与平板右栏共用的 LazyColumn 内容。
 */
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
        // 与搜索结果一致：先按 id 去重再作为 Lazy key，避免站点返回重复条目时 key 冲突崩溃（审查 O3）
        items(detail.relatedVideos.distinctBy { it.id }, key = { it.id }) { video ->
            DetailRelatedRow(
                video = video,
                currentTab = state.selectedTab,
                onSelectTab = actions.onTabSelected,
                onVideoClick = actions.onVideoClick,
                onAuthorClick = actions.onAuthorClick
            )
        }
    }

    item {
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/** 左右滑动切页的触发阈值 */
private val DetailTabSwipeThreshold = 60.dp

/**
 * 「简介 / 评论」左右滑动切页手势。
 */
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
 * 相关推荐单条：卡片本体 + 外层的左右滑动切页手势。
 * 手势加在外层是为了让「简介」页的滑动区域覆盖到相关推荐（该区块不参与滑动动效，见 [detailRestItems]）。
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
    // 切页阈值：抽屉手势用 120dp，这里内容在列表内部，取 60dp 更跟手
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

/**
 * 「评论」页：评论输入框 + 加载中 / 错误 / 空 / 列表状态。
 */
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
