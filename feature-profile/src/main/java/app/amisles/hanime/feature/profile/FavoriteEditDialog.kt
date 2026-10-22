package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.FavoriteGroup

/**
 * 收藏条目编辑：选择分组 + 增删自定义标签。
 *
 * 标签在对话框内即时增删（本地状态），点「保存」才一次性落库，
 * 避免每敲一个字符就写一次数据库。
 */
@Composable
internal fun FavoriteEditDialog(
    title: String,
    tags: List<String>,
    groups: List<FavoriteGroup>,
    selectedGroupId: String,
    onDismiss: () -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onGroupChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.favorite_group_label),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GroupChoiceChip(
                        label = stringResource(R.string.favorite_group_none),
                        selected = selectedGroupId.isEmpty(),
                        onClick = { onGroupChange("") }
                    )
                    groups.forEach { group ->
                        GroupChoiceChip(
                            label = group.name,
                            selected = selectedGroupId == group.id,
                            onClick = { onGroupChange(group.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.favorite_tags_label),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable { onTagsChange(tags - tag) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        val tag = input.trim()
                        if (tag.isNotEmpty() && tag !in tags) {
                            onTagsChange(tags + tag)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank()
                ) {
                    Text(stringResource(R.string.favorite_tags_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun GroupChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
