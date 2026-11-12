package app.amisles.hanime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val RAIL_ITEM_WIDTH = 64.dp
private val RAIL_ITEM_CORNER = 12.dp
private val RAIL_ITEM_SPACING = 12.dp
private val RAIL_ITEM_VERTICAL_PADDING = 10.dp
private val RAIL_BOTTOM_PADDING = 24.dp
private val RAIL_LABEL_GAP = 4.dp

/** 平板（Expanded / Medium 宽度）左侧竖向导航栏，替代手机端底部导航；复用 [screens] 条目。 */
@Composable
fun NavRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Spacer(modifier = Modifier.weight(1f))
        screens.forEachIndexed { index, screen ->
            if (index > 0) Spacer(modifier = Modifier.height(RAIL_ITEM_SPACING))
            RailItem(
                screen = screen,
                selected = currentRoute.startsWith(screen.route),
                showLabel = showLabels,
                onClick = { onNavigate(screen.route) }
            )
        }
        Spacer(modifier = Modifier.height(RAIL_BOTTOM_PADDING))
    }
}

@Composable
private fun RailItem(
    screen: Screen,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(RAIL_ITEM_WIDTH)
            .clip(RoundedCornerShape(RAIL_ITEM_CORNER))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = RAIL_ITEM_VERTICAL_PADDING)
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = stringResource(screen.labelResId),
            tint = contentColor
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(RAIL_LABEL_GAP))
            Text(
                text = stringResource(screen.labelResId),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}
