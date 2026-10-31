package app.amisles.hanime.navigation

import androidx.navigation.NavController

internal fun NavController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

internal fun NavController.navigateToSearch(route: String) = navigateToRoot(route)

internal fun NavController.navigateToTab(route: String): Boolean {
    if (currentDestination?.route?.baseRoute() == route) return false
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    return true
}
