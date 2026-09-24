package app.amisles.hanime.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** 平板（Expanded / Medium 宽度）左侧竖向导航栏，替代手机端底部导航；复用 [screens] 条目。 */
@Composable
fun NavRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        screens.forEach { screen ->
            val selected = currentRoute.startsWith(screen.route)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(screen.route) },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = screen.icon,
                        contentDescription = stringResource(screen.labelResId)
                    )
                },
                label = {
                    androidx.compose.material3.Text(
                        text = stringResource(screen.labelResId),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}
