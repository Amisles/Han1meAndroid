package app.amisles.hanime.feature.detail.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView

/** 评论区：包含输入框、加载中、错误、空、列表五种状态。 */
@Composable
internal fun CommentSection(
    comments: List<app.amisles.hanime.domain.model.Comment>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    repliesCache: Map<String, List<app.amisles.hanime.domain.model.Reply>>,
    loadingReplies: Set<String>,
    repliesError: Map<String, String?>,
    onLoadReplies: (String) -> Unit,
    expandedReplies: Set<String>,
    onToggleExpand: (String) -> Unit,
    isLogin: Boolean,
    isPostingComment: Boolean,
    postCommentError: String?,
    onPostComment: (String) -> Unit,
    onClearPostError: () -> Unit,
    onToggleLike: (app.amisles.hanime.domain.model.Comment) -> Unit,
    likingComments: Set<String>,
    activeReplyCommentId: String?,
    replyPrefill: String,
    isPostingReply: Boolean,
    replyError: String?,
    onStartReply: (String, String?) -> Unit,
    onSendReply: (String) -> Unit,
    onCancelReply: () -> Unit,
    onClearReplyError: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 评论输入框
        CommentInputBar(
            isLogin = isLogin,
            isPosting = isPostingComment,
            error = postCommentError,
            onPost = onPostComment,
            onClearError = onClearPostError,
            onNavigateToLogin = onNavigateToLogin
        )

        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            // 仅在没有可展示内容时才整块显示错误页；已有评论时改用顶部轻量提示，避免刷新失败覆盖已加载列表
            error != null && comments.isEmpty() -> {
                KaomojiErrorView(
                    message = error,
                    onRetry = onRetry
                )
            }
            comments.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.comment_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                if (error != null) {
                    RefreshFailedBar(message = error, onRetry = onRetry)
                }
                comments.forEach { comment ->
                    // 显式 key 绑定评论 id：CommentItem 内部的回复输入内容是按组合位置 remember 的，
                    // 新评论插入列表头部后各条位置整体后移，输入内容会错配到别的评论上。
                    // 注：本列表嵌在外层 LazyColumn 的单个 item 内，不能改为嵌套 LazyColumn
                    // （同向嵌套滚动会因无限高度约束崩溃）；完全虚拟化需把评论提升为外层 item，
                    // 与当前「简介/评论」AnimatedContent 切页结构冲突，属结构性改动。
                    key(comment.id) {
                        CommentItem(
                            comment = comment,
                            replies = repliesCache[comment.id],
                            isLoadingReplies = loadingReplies.contains(comment.id),
                            repliesError = repliesError[comment.id],
                            onLoadReplies = onLoadReplies,
                            isExpanded = expandedReplies.contains(comment.id),
                            onToggleExpand = { onToggleExpand(comment.id) },
                            onToggleLike = onToggleLike,
                            isLiking = likingComments.contains(comment.id),
                            isLogin = isLogin,
                            onNavigateToLogin = onNavigateToLogin,
                            onReply = { replyToUsername -> onStartReply(comment.id, replyToUsername) },
                            isReplying = activeReplyCommentId == comment.id,
                            replyPrefill = replyPrefill,
                            isPostingReply = isPostingReply,
                            replyError = replyError,
                            onSendReply = onSendReply,
                            onCancelReply = onCancelReply,
                            onClearReplyError = onClearReplyError
                        )
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .padding(horizontal = 15.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 已有评论数据时的「刷新失败」提示条：只占一行，提供重试入口，不覆盖已加载的列表。 */
@Composable
private fun RefreshFailedBar(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.common_retry),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
