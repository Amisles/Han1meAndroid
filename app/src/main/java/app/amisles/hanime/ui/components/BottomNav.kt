package app.amisles.hanime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.amisles.hanime.R
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Search : Screen("search", "搜索", Icons.Default.Search)
    object Download : Screen("download", "下载", Icons.Default.Download)
    object Profile : Screen("profile", "我的", Icons.Default.Person)
}

val screens = listOf(Screen.Home, Screen.Search, Screen.Download, Screen.Profile)

@Composable
fun BottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HanimeBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 不执行任何操作，仅消费触摸事件 */ }
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        screens.forEach { screen ->
            val isSelected = currentRoute.startsWith(screen.route)
            Icon(
                imageVector = screen.icon,
                contentDescription = screen.label,
                tint = if (isSelected) HanimePrimary else HanimeTextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onNavigate(screen.route) }
            )
        }
    }
}
