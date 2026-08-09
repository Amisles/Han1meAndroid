package app.amisles.hanime

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.preferences.ThemeMode
import app.amisles.hanime.ui.components.BottomNav
import app.amisles.hanime.feature.detail.DetailScreen
import app.amisles.hanime.feature.download.DownloadScreen
import app.amisles.hanime.feature.profile.FavoriteScreen
import app.amisles.hanime.feature.profile.HistoryScreen
import app.amisles.hanime.feature.home.HomeScreen
import app.amisles.hanime.feature.profile.LoginScreen
import app.amisles.hanime.feature.profile.ProfileScreen
import app.amisles.hanime.feature.search.SearchScreen
import app.amisles.hanime.feature.search.genreApiValues
import app.amisles.hanime.feature.search.sortOptions
import app.amisles.hanime.feature.settings.AboutScreen
import app.amisles.hanime.feature.settings.AuthorScreen
import app.amisles.hanime.ui.screens.PlaylistDetailScreen
import app.amisles.hanime.ui.screens.PlaylistListPageScreen
import app.amisles.hanime.feature.settings.SettingsScreen
import app.amisles.hanime.ui.screens.VideoListPageScreen
import app.amisles.hanime.feature.download.BatchDownloadScreen
import app.amisles.hanime.ui.theme.HanimeBackground
import app.amisles.hanime.ui.theme.HanimeBackgroundLight
import app.amisles.hanime.ui.theme.HanimeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 每个 Activity 必须独立包装 Context 才能让切换语言后的资源生效。
     * Application 级别的 attachBaseContext 仅影响 Application Context，
     * 不影响 Activity 所持有的 Resources/Configuration。
     * 当用户在 Settings 切换语言后，Activity 调用 recreate()，
     * attachBaseContext 会重新读取 SP 并包装新的 Locale。
     */
    override fun attachBaseContext(newBase: Context) {
        val lang = runCatching {
            newBase.getSharedPreferences("hanime_app_prefs", Context.MODE_PRIVATE)
                .getString("app_language", Preferences.LANGUAGE_ZH_CN)
                ?: Preferences.LANGUAGE_ZH_CN
        }.getOrDefault(Preferences.LANGUAGE_ZH_CN)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by Preferences.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 根据主题模式动态调整系统栏图标颜色
            SideEffect {
                val bgArgb = if (darkTheme) HanimeBackground else HanimeBackgroundLight
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(bgArgb.hashCode())
                    } else {
                        SystemBarStyle.light(bgArgb.hashCode(), bgArgb.hashCode())
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(bgArgb.hashCode())
                    } else {
                        SystemBarStyle.light(bgArgb.hashCode(), bgArgb.hashCode())
                    }
                )
            }
            HanimeTheme(themeMode = themeMode) {
                HanimeApp()
            }
        }
    }
}

@Composable
fun HanimeApp() {

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val showBottomBar = !currentRoute.startsWith("detail") &&
        currentRoute != "about" &&
        currentRoute != "login" &&
        currentRoute != "settings" &&
        currentRoute != "batchDownload" &&
        !currentRoute.startsWith("author") &&
        !currentRoute.startsWith("videoListPage") &&
        !currentRoute.startsWith("playlistListPage") &&
        !currentRoute.startsWith("playlistDetail")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                BottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            if (!navController.popBackStack(route, inclusive = false)) {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.navigationBarsPadding(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("home") {
                HomeScreen(
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    },
                    onSearchClick = { keyword ->
                        navController.navigate("search?keyword=${Uri.encode(keyword)}") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGenreSearch = { genre ->
                        navController.navigate("search?genre=${Uri.encode(genre)}") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate("search") {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAuthorClick = { author ->
                        navController.navigate("search?keyword=${Uri.encode(author)}")
                    },
                    onViewMore = { sectionTitle ->
                        val sortMatch = sortOptions.firstOrNull { it.label == sectionTitle }
                        when {
                            sortMatch != null -> {
                                navController.navigate("search?sort=${Uri.encode(sortMatch.apiValue)}")
                            }
                            genreApiValues.containsKey(sectionTitle) -> {
                                navController.navigate("search?genre=${Uri.encode(genreApiValues[sectionTitle])}")
                            }
                            else -> {
                                navController.navigate("search?keyword=${Uri.encode(sectionTitle)}")
                            }
                        }
                    }
                )
            }
            composable(
                route = "search?keyword={keyword}&genre={genre}&sort={sort}",
                arguments = listOf(
                    navArgument("keyword") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("genre") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("sort") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val keyword = backStackEntry.arguments?.getString("keyword")
                val genre = backStackEntry.arguments?.getString("genre")
                val sort = backStackEntry.arguments?.getString("sort")
                SearchScreen(
                    initialKeyword = keyword,
                    initialGenre = genre,
                    initialSort = sort,
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    },
                    onAuthorClick = { author ->
                        navController.navigate("search?keyword=${Uri.encode(author)}")
                    }
                )
            }
            composable("download") {
                DownloadScreen(
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onNavigate = { route ->
                        navController.navigate(route)
                    }
                )
            }
            composable("favorite") {
                FavoriteScreen(
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    }
                )
            }
            composable("history") {
                HistoryScreen(
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    }
                )
            }
            composable("about") {
                AboutScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigate = { route ->
                        if (route == "about") {
                            navController.navigate("about")
                        }
                    }
                )
            }
            composable("batchDownload") {
                BatchDownloadScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("login") {
                LoginScreen(
                    onBackClick = { navController.popBackStack() },
                    onLoginSuccess = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable(
                route = "author?authorPageUrl={authorPageUrl}",
                arguments = listOf(navArgument("authorPageUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val authorPageUrl = backStackEntry.arguments?.getString("authorPageUrl")
                AuthorScreen(
                    authorPageUrl = authorPageUrl ?: "",
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    },
                    onViewAllVideos = { url ->
                        navController.navigate("videoListPage?url=${Uri.encode(url)}")
                    },
                    onViewAllPlaylists = { url ->
                        navController.navigate("playlistListPage?url=${Uri.encode(url)}")
                    },
                    onPlaylistClick = { url ->
                        navController.navigate("playlistDetail?url=${Uri.encode(url)}")
                    }
                )
            }
            composable(
                route = "videoListPage?url={url}",
                arguments = listOf(navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url")
                VideoListPageScreen(
                    url = url ?: "",
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    }
                )
            }
            composable(
                route = "playlistListPage?url={url}",
                arguments = listOf(navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url")
                PlaylistListPageScreen(
                    url = url ?: "",
                    onBackClick = { navController.popBackStack() },
                    onPlaylistClick = { playlistUrl ->
                        navController.navigate("playlistDetail?url=${Uri.encode(playlistUrl)}")
                    }
                )
            }
            composable(
                route = "playlistDetail?url={url}",
                arguments = listOf(navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url")
                PlaylistDetailScreen(
                    url = url ?: "",
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    }
                )
            }
            composable(
                route = "detail?videoUrl={videoUrl}",
                arguments = listOf(navArgument("videoUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val videoUrl = backStackEntry.arguments?.getString("videoUrl")
                DetailScreen(
                    videoUrl = videoUrl,
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { newVideoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(newVideoUrl)}")
                    },
                    onTagClick = { tag ->
                        navController.navigate("search?keyword=${Uri.encode(tag)}")
                    },
                    onAuthorClick = { author ->
                        navController.navigate("search?keyword=${Uri.encode(author)}")
                    },
                    onAuthorPageClick = { authorPageUrl ->
                        navController.navigate("author?authorPageUrl=${Uri.encode(authorPageUrl)}")
                    },
                    onNavigateToLogin = {
                        navController.navigate("login")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }
        }
    }
}
