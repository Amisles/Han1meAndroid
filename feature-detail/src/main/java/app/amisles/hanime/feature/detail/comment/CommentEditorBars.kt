package app.amisles.hanime.feature.detail.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R

/**
 * 评论输入栏：未登录时提示点击登录；已登录时显示输入框和发送按钮。
 */
@Composable
internal fun CommentInputBar(
    isLogin: Boolean,
    isPosting: Boolean,
    error: String?,
    onPost: (String) -> Unit,
    onClearError: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp)
    ) {
        if (!isLogin) {
            // 未登录：点击跳转登录页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onNavigateToLogin() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.comment_login_required),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            CommentEditorRow(
                value = inputText,
                onValueChange = {
                    inputText = it
                    if (error != null) onClearError()
                },
                placeholderRes = R.string.comment_input_hint,
                isPosting = isPosting,
                onSend = {
                    onPost(it)
                    inputText = ""
                }
            )
        }

        // 错误提示
        if (error != null) {
            Text(
                text = error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

/**
 * 内联回复输入框：缩进对齐到回复列表，支持 @用户名 预填、发送、取消与错误提示。
 */
@Composable
internal fun ReplyInputBar(
    prefill: String,
    isPosting: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit
) {
    var inputText by remember(prefill) { mutableStateOf(prefill) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        CommentEditorRow(
            value = inputText,
            onValueChange = {
                inputText = it
                if (error != null) onClearError()
            },
            placeholderRes = R.string.comment_reply_hint,
            isPosting = isPosting,
            onSend = {
                onSend(it)
                inputText = ""
            }
        )

        // 取消按钮 + 错误提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.common_cancel),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isPosting) { onCancel() }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
