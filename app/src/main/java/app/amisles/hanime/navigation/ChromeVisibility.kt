package app.amisles.hanime.navigation

private val hiddenRoutes = setOf(
    "about",
    "update",
    "login",
    "settings",
    "launcherIcon",
    "backup",
    "crashLog",
    "comics",
    "comicTags",
    "comicArtists",
    "watchStats",
    "batchDownload",
    "onboarding"
)

private val hiddenPrefixes = listOf(
    "detail",
    "comicDirectory",
    "subscriptions",
    "author",
    "videoListPage",
    "playlistListPage",
    "playlistDetail",
    "accountEdit",
    "localPlayer",
    "comicDetail",
    "comicReader"
)

internal fun shouldShowBottomBar(route: String): Boolean =
    route !in hiddenRoutes && hiddenPrefixes.none { route.startsWith(it) }
