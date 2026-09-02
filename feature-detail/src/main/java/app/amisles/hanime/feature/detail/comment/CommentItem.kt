package app.amisles.hanime.feature.detail.comment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R

/**
 * 单条评论（含展开/收起回复）
 */
@Composable
internal fun CommentItem(
    comment: app.amisles.hanime.domain.model.Comment,
    replies: List<app.amisles.hanime.domain.model.Reply>?,
    isLoadingReplies: Boolean,
    repliesError: String?,
    onLoadReplies: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleLike: (app.amisles.hanime.domain.model.Comment) -> Unit,
    isLiking: Boolean,
    isLogin: Boolean,
    onNavigateToLogin: () -> Unit,
    onReply: (String?) -> Unit,
    isReplying: Boolean,
    replyPrefill: String,
    isPostingReply: Boolean,
    replyError: String?,
    onSendReply: (String) -> Unit,
    onCancelReply: () -> Unit,
    onClearReplyError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            coil3.compose.AsyncImage(
                model = comment.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = comment.username,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = comment.time,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = comment.content,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val liked = comment.likeStatus == 1
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !isLiking) { onToggleLike(comment) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = stringResource(R.string.comment_like),
                            modifier = Modifier.size(14.dp),
                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = comment.likeCount.toString(),
                            fontSize = 12.sp,
                            color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 回复按钮
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                if (isLogin) onReply(null) else onNavigateToLogin()
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.comment_reply),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 展开/收起回复按钮（放在点赞同一行，红色主题色）
                    if (comment.replyCount > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onToggleExpand()
                                    if (!isExpanded && replies == null && !isLoadingReplies) {
                                        onLoadReplies(comment.id)
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExpanded) {
                                    stringResource(R.string.comment_hide_replies)
                                } else {
                                    stringResource(R.string.comment_view_replies, comment.replyCount)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 内联回复输入框（仅在该评论的回复目标激活时显示）
                if (isReplying) {
                    ReplyInputBar(
                        prefill = replyPrefill,
                        isPosting = isPostingReply,
                        error = replyError,
                        onSend = onSendReply,
                        onCancel = onCancelReply,
                        onClearError = onClearReplyError
                    )
                }
            }
        }

        // 展开时显示回复列表
        if (isExpanded && comment.replyCount > 0) {
            ReplyList(
                replies = replies,
                isLoading = isLoadingReplies,
                error = repliesError,
                onRetry = { onLoadReplies(comment.id) },
                onReplyToReply = { username -> onReply(username) },
                isLogin = isLogin,
                onNavigateToLogin = onNavigateToLogin
            )
        }
    }
}
