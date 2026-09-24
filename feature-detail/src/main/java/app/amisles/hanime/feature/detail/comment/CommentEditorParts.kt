package app.amisles.hanime.feature.detail.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R

/**
 * 评论与回复共用的输入行：多行输入框 + 发送按钮。输入内容由调用方持有，
 * 发送后的清空也由调用方在 [onSend] 里完成。
 */
@Composable
internal fun CommentEditorRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderRes: Int,
    isPosting: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = stringResource(placeholderRes),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            singleLine = false,
            maxLines = 3,
            enabled = !isPosting,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        IconButton(
            onClick = {
                if (value.isNotBlank() && !isPosting) {
                    onSend(value)
                }
            },
            enabled = !isPosting && value.isNotBlank()
        ) {
            if (isPosting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.comment_post_button),
                    tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
