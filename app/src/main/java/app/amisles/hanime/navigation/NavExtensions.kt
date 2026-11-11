package app.amisles.hanime.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

private val tabGraphRoutes = mapOf(
    "home" to "home",
    "search" to "tab_search",
    "download" to "tab_download"
)

/**
 * 回到根锚点后压入目标路由，供「跳搜索」与「详情页回首页」共用。
 *
 * 锚点必须取根图 start 的叶子目的地（home）：search/download 的包装图与全部二级页
 * 都在它之上，以它为 popUpTo 目标可以丢弃其上任何页面而不动首页自身的栈；若锚定
 * 包装图，pop 会连首页叶子一起弹出，首页的滚动位置与 ViewModel 随之丢失。
 * saveState = false：跳搜索是一次全新搜索，被弹页面不复用。
 * launchSingleTop 按叶子目的地判重，route 为 home 时压入的叶子恰与锚点相同，
 * 不会在栈上叠加第二个首页。
 */
internal fun NavController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

/** 跳转到搜索页。 */
internal fun NavController.navigateToSearch(route: String) = navigateToRoot(route)

/**
 * 切换底部导航 / 导航栏的 Tab；目标已是当前 Tab 时不动。
 *
 * search / download 各自有包装图：离开时包装图连同其栈一起被 popUpTo 弹出并按
 * 包装图 id 归档（saveState），再次进入时 restoreState 取回，列表滚动位置与
 * ViewModel 得以跨 Tab 切换存活。扁平图做不到这一点——所有 Tab 共用同一个
 * popUpTo 目标 id，保存与恢复互相覆盖，表现为「点 Tab 没有反应」或「回到上一次
 * 的详情页」。home 不设包装图，直接以根图 start 叶子作锚点：它永不出栈，状态
 * 常驻，且从首页按返回键不会多出一层包装图条目。
 */
internal fun NavController.navigateToTab(route: String) {
    if (currentDestination?.route?.baseRoute() == route) return
    navigate(tabGraphRoutes[route] ?: route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
