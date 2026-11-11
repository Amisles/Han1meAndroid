package app.amisles.hanime.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

private val tabGraphRoutes = mapOf(
    "home" to "tab_home",
    "search" to "tab_search",
    "download" to "tab_download"
)

/**
 * 回到根锚点后压入目标路由，供「跳搜索」与「详情页回首页」共用。
 *
 * 锚点必须取根图 start 的叶子目的地（home）：Tab 包装图与全部二级页都在它之上，
 * 以它为 popUpTo 目标可以丢弃其上任何页面而不动首页自身的栈；若锚定 tab_home
 * 包装图，pop 会连首页叶子一起弹出，首页的滚动位置与 ViewModel 随之丢失。
 * saveState = false：跳搜索是一次全新搜索，被弹页面不复用。
 * route 为 "home" 时必须转 navigateToTab：直接压入裸 home 叶子会在包装图之上
 * 再叠一份 home，back 一次仍落在首页，表现为返回键失灵。
 */
internal fun NavController.navigateToRoot(route: String) {
    if (route == "home") {
        navigateToTab(route)
        return
    }
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
 * 每个 Tab 有自己的包装图：离开时包装图连同其栈一起被 popUpTo 弹出并按包装图 id
 * 归档（saveState），再次进入时 restoreState 取回，列表滚动位置与 ViewModel 得以
 * 跨 Tab 切换存活。扁平图做不到这一点——所有 Tab 共用同一个 popUpTo 目标 id，
 * 保存与恢复互相覆盖，表现为「点 Tab 没有反应」或「回到上一次的详情页」。
 * 锚点取根图 start 的叶子（home）而非包装图，首页叶子因此永不出栈、状态常驻；
 * launchSingleTop 对包装图按其叶子目的地判重，回切 home 不会叠加新条目。
 */
internal fun NavController.navigateToTab(route: String) {
    if (currentDestination?.route?.baseRoute() == route) return
    navigate(tabGraphRoutes[route] ?: route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
