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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
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
import app.amisles.hanime.core.ui.components.SearchField
import app.amisles.hanime.domain.model.FavoriteGroup

/** 收藏页搜索框：本地即时过滤，不发网络请求。 */
@Composable
internal fun FavoriteSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.favorite_search_hint),
        singleLine = true,
        onClear = { onQueryChange("") },
        modifier = modifier
    )
}

/**
 * 分组筛选条：首项为「全部」，其后是用户自建分组，末项是「新建」。
 * 选中态用 primary / onPrimary，未选中用 surfaceVariant / onSurfaceVariant，深浅色均可用。
 */
@Composable
internal fun FavoriteGroupBar(
    groups: List<FavoriteGroup>,
    selectedGroupId: String?,
    onSelect: (String?) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GroupChip(
            label = stringResource(R.string.favorite_group_all),
            selected = selectedGroupId == null,
            onClick = { onSelect(null) }
        )
        groups.forEach { group ->
            GroupChip(
                label = group.name,
                selected = selectedGroupId == group.id,
                onClick = { onSelect(group.id) }
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(onClick = onCreateGroup)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.favorite_group_new),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.favorite_group_new),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 收藏条目标签行：展示自定义标签，点击可编辑。 */
@Composable
internal fun FavoriteTagsRow(
    tags: List<String>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tag,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 分组名称输入对话框，用于新建与重命名；样式与收藏管理弹窗保持一致。 */
@Composable
internal fun GroupNameDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
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
                Text(
                    text = stringResource(R.string.favorite_group_name_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SearchField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = stringResource(R.string.favorite_group_name_hint),
                    singleLine = true,
                    showSearchIcon = false,
                    onSearch = { if (value.isNotBlank()) onConfirm(value) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Text(
                    text = stringResource(R.string.common_confirm),
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
