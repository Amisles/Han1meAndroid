package app.amisles.hanime.feature.detail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.amisles.hanime.domain.model.Comment
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
 *
 * 各区块的 UI 已拆分到 components / comment 包，此处只保留区块编排与 item 结构，
 * 与拆分前 detailRestItems lambda 的 item 顺序、key 完全一致。
 */
internal fun LazyListScope.detailRestItems(
    detail: VideoDetail,
    state: DetailRestState,
    actions: DetailRestActions
) {
    // 「简介 / 评论」分段选择条
    item(key = "detail_tab_bar") {
        DetailTabBar(
            selectedTab = state.selectedTab,
            onTabSelected = actions.onTabSelected
        )
    }

    if (state.selectedTab == 0) {
        item {
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
        }

        val playlist = detail.playlist
        if (playlist != null && playlist.videos.isNotEmpty()) {
            item {
                DetailPlaylistHeader(playlist = playlist, onAuthorClick = actions.onAuthorClick)
            }

            item {
                DetailPlaylistVideos(playlist = playlist, onVideoClick = actions.onVideoClick)
            }
        }

        items(detail.relatedVideos) { video ->
            DetailRelatedVideoCard(
                video = video,
                onVideoClick = actions.onVideoClick,
                onAuthorClick = actions.onAuthorClick
            )
        }
    } else {
        item(key = "comments_section") {
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
    }
    item {
        Spacer(modifier = Modifier.height(80.dp))
    }
}
