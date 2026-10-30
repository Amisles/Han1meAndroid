package app.amisles.hanime.navigation

import androidx.navigation.NavController

/**
 * 回到栈根后压入目标路由，供「切 Tab」与「跳搜索」共用。
 *
 * 不能用 saveState / restoreState：扁平图下 popUpTo 的目标恒为 startDestination，
 * 保存与恢复共用同一个 destination id —— pop 时把被弹出的页面存到该 id 下，
 * restore 紧接着又把它取回来，表现为「点 Tab 没有反应」或「回到上一次的详情页」。
 */
internal fun NavController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

/** 跳转到搜索页。 */
internal fun NavController.navigateToSearch(route: String) = navigateToRoot(route)

/** 切换底部导航 / 导航栏的 Tab；目标已是当前 Tab 时不动。 */
internal fun NavController.navigateToTab(route: String) {
    if (currentDestination?.route?.baseRoute() == route) return
    navigateToRoot(route)
}
