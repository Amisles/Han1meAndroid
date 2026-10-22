package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SearchField
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                SectionLabel(stringResource(R.string.favorite_group_label))
                Spacer(modifier = Modifier.height(8.dp))
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

                Spacer(modifier = Modifier.height(18.dp))

                SectionLabel(stringResource(R.string.favorite_tags_label))
                Spacer(modifier = Modifier.height(8.dp))
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
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(start = 8.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
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
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable { onTagsChange(tags - tag) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                SearchField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = stringResource(R.string.favorite_tags_hint),
                    singleLine = true,
                    showSearchIcon = false,
                    onSearch = {
                        val tag = input.trim()
                        if (tag.isNotEmpty() && tag !in tags) {
                            onTagsChange(tags + tag)
                            input = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        val tag = input.trim()
                        if (tag.isNotEmpty() && tag !in tags) {
                            onTagsChange(tags + tag)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.favorite_tags_add),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(
                    text = stringResource(R.string.common_save),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
