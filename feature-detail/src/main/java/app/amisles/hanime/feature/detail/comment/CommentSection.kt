package app.amisles.hanime.feature.detail.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView

/**
 * 评论区：包含输入框、加载中、错误、空、列表五种状态
 */
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
            error != null -> {
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
                comments.forEach { comment ->
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
