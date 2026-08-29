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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.preferences.ThemeMode
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.ui.components.BottomNav
import app.amisles.hanime.ui.components.NavRail
import app.amisles.hanime.feature.detail.DetailScreen
import app.amisles.hanime.feature.download.DownloadScreen
import app.amisles.hanime.feature.profile.FavoriteScreen
import app.amisles.hanime.feature.profile.HistoryScreen
import app.amisles.hanime.feature.home.HomeScreen
import app.amisles.hanime.feature.profile.LoginScreen
import app.amisles.hanime.feature.profile.ProfileScreen
import app.amisles.hanime.feature.profile.AccountProfileScreen
import app.amisles.hanime.feature.profile.SubscriptionsScreen
import app.amisles.hanime.feature.search.SearchScreen
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.feature.search.sortOptions
import app.amisles.hanime.feature.settings.AboutScreen
import app.amisles.hanime.feature.settings.AuthorScreen
import app.amisles.hanime.ui.screens.PlaylistDetailScreen
import app.amisles.hanime.ui.screens.PlaylistListPageScreen
import app.amisles.hanime.feature.settings.SettingsScreen
import app.amisles.hanime.feature.settings.DiagnosticsScreen
import app.amisles.hanime.ui.screens.VideoListPageScreen
import app.amisles.hanime.feature.download.BatchDownloadScreen
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeBackgroundLight
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
        // 注意：Preferences 已在 HanimeApplication.onCreate 中以 EncryptedSharedPreferences 初始化，
        // 此处若再用明文 getSharedPreferences("hanime_app_prefs") 打开同一文件，会与加密存储冲突，
        // 导致 Preferences 的“明文→加密”迁移每次启动都误触发，把已保存的主题/语言等覆盖为默认值。
        // 因此直接读取已初始化的 Preferences 中的语言即可（init 先于 Activity.attachBaseContext 执行）。
        val lang = runCatching { Preferences.appLanguage }.getOrDefault(Preferences.LANGUAGE_ZH_CN)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by Preferences.themeModeFlow.collectAsStateWithLifecycle()
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
        !currentRoute.startsWith("subscriptions") &&
        !currentRoute.startsWith("author") &&
        !currentRoute.startsWith("videoListPage") &&
        !currentRoute.startsWith("playlistListPage") &&
        !currentRoute.startsWith("playlistDetail") &&
        !currentRoute.startsWith("accountEdit")

    val useRail = currentWindowSizeInfo().useNavigationRail

    val density = LocalDensity.current
    var profileOpen by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (profileOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "profileDrawer"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showBottomBar && useRail) {
                NavRail(
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
            Scaffold(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBottomBar && !useRail) {
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
                        val categoryMatch = categories.firstOrNull { it.label == sectionTitle }
                        when {
                            sortMatch != null -> {
                                navController.navigate("search?sort=${Uri.encode(sortMatch.apiValue)}")
                            }
                            categoryMatch != null -> {
                                navController.navigate("search?genre=${Uri.encode(categoryMatch.apiValue)}")
                            }
                            else -> {
                                navController.navigate("search?keyword=${Uri.encode(sectionTitle)}")
                            }
                        }
                    },
                    onProfileClick = { profileOpen = true }
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
            composable(
                route = "subscriptions?query={query}",
                arguments = listOf(navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                SubscriptionsScreen(
                    initialQuery = query,
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoUrl ->
                        navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                    },
                    onNavigateToLogin = {
                        navController.navigate("login")
                    }
                )
            }
            composable("accountEdit") {
                AccountProfileScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToLogin = {
                        navController.navigate("login")
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
                        when (route) {
                            "about" -> navController.navigate("about")
                            "diagnostics" -> navController.navigate("diagnostics")
                        }
                    }
                )
            }
            composable("diagnostics") {
                DiagnosticsScreen(
                    onBackClick = { navController.popBackStack() }
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

    // ---- 我的页面抽屉：左侧滑出，覆盖整个 Scaffold（含底部导航）----
    val showOverlay = profileOpen || progress > 0.001f
    if (showOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { profileOpen = false }
        )
        var dragAccum by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.8f)
                .graphicsLayer {
                    val base = -(1f - progress) * size.width
                    translationX = base + if (dragAccum < 0f) dragAccum else 0f
                }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            dragAccum += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            val threshold = with(density) { 120.dp.toPx() }
                            if (dragAccum < -threshold) {
                                profileOpen = false
                            }
                            dragAccum = 0f
                        }
                    )
                }
        ) {
            ProfileScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                    profileOpen = false
                }
            )
        }
    }
}
}
