package app.amisles.hanime.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.feature.comic.ComicDetailScreen
import app.amisles.hanime.feature.comic.ComicDirectoryResultScreen
import app.amisles.hanime.feature.comic.ComicDirectoryScreen
import app.amisles.hanime.feature.comic.ComicReaderScreen
import app.amisles.hanime.feature.comic.ComicScreen
import app.amisles.hanime.feature.detail.DetailScreen
import app.amisles.hanime.feature.detail.LocalPlayerScreen
import app.amisles.hanime.feature.download.BatchDownloadScreen
import app.amisles.hanime.feature.download.DownloadScreen
import app.amisles.hanime.feature.home.HomeScreen
import app.amisles.hanime.feature.profile.AccountProfileScreen
import app.amisles.hanime.feature.profile.CloudFavoritesScreen
import app.amisles.hanime.feature.profile.FavoriteScreen
import app.amisles.hanime.feature.profile.HistoryScreen
import app.amisles.hanime.feature.profile.LoginScreen
import app.amisles.hanime.feature.profile.SubscriptionsScreen
import app.amisles.hanime.feature.profile.UserPlaylistDetailScreen
import app.amisles.hanime.feature.profile.WatchStatsScreen
import app.amisles.hanime.feature.search.SearchScreen
import app.amisles.hanime.feature.settings.AboutScreen
import app.amisles.hanime.feature.settings.AuthorScreen
import app.amisles.hanime.feature.settings.BackupScreen
import app.amisles.hanime.feature.settings.CrashLogScreen
import app.amisles.hanime.feature.settings.CustomizeScreen
import app.amisles.hanime.feature.settings.DiagnosticsScreen
import app.amisles.hanime.feature.settings.LauncherIconScreen
import app.amisles.hanime.feature.settings.SettingsScreen
import app.amisles.hanime.feature.settings.UpdateScreen
import app.amisles.hanime.feature.home.list.PlaylistDetailScreen
import app.amisles.hanime.feature.home.list.PlaylistListPageScreen
import app.amisles.hanime.feature.home.list.VideoListPageScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import app.amisles.hanime.domain.model.HanimeVideo

/** 路由转场进场时长（毫秒）。首帧成本下移后，短促淡入即可掩盖残余开销。 */
internal const val NAV_TRANSITION_DURATION_MS = 220
/** 退场时长略短于进场：让目标页更快接管画面，减少两页同时绘制的帧数。 */
internal const val NAV_TRANSITION_EXIT_DURATION_MS = 160

@Composable
internal fun HanimeNavGraph(
    navController: NavHostController,
    onOpenProfile: () -> Unit,
    onVideoLongClick: (HanimeVideo) -> Unit,
    onFavoriteAll: (List<HanimeVideo>) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS)) +
                slideInHorizontally(
                    animationSpec = tween(NAV_TRANSITION_DURATION_MS),
                    initialOffsetX = { it / 10 }
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(NAV_TRANSITION_EXIT_DURATION_MS))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS)) +
                slideInHorizontally(
                    animationSpec = tween(NAV_TRANSITION_DURATION_MS),
                    initialOffsetX = { -it / 10 }
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(NAV_TRANSITION_EXIT_DURATION_MS)) +
                slideOutHorizontally(
                    animationSpec = tween(NAV_TRANSITION_DURATION_MS),
                    targetOffsetX = { it / 10 }
                )
        }
    ) {
        composable("home") {
            HomeScreen(
                onVideoClick = { videoUrl ->
                    navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}") },
                onSearchClick = { keyword ->
                    navController.navigateToSearch(searchRoute(keyword = keyword)) },
                onGenreSearch = { genre ->
                    navController.navigateToSearch(searchRoute(genre = genre)) },
                onNavigateToSearch = {
                    navController.navigateToSearch(searchRoute()) },
                onAuthorClick = { author ->
                    navController.navigateToSearch(searchRoute(keyword = author)) },
                onViewMore = { section ->
                    val sortApiValue = sortApiValueOf(section)
                    val genreApiValue = if (sortApiValue == null) genreApiValueOf(section) else null
                    if (sortApiValue != null || genreApiValue != null) {
                        navController.navigateToSearch(
                            if (sortApiValue != null) searchRoute(sort = sortApiValue)
                            else searchRoute(genre = genreApiValue)
                        )
                    } else if (section.moreUrl.isNotBlank()) {
                        moreUrlPageRoute(section.moreUrl)?.let { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    } },
                onProfileClick = { onOpenProfile() },
                onComicClick = { navController.navigate("comics") },
                onVideoLongClick = onVideoLongClick,
                onOpenWatchStats = { navController.navigate("watchStats") }
            )
        }
        composable("comics") {
            ComicScreen(
                onBackClick = { navController.popBackStack() },
                onComicClick = { comicId ->
                    navController.navigate("comicDetail?comicId=" + Uri.encode(comicId))
                },
                onTagsClick = { navController.navigate("comicTags") },
                onArtistsClick = { navController.navigate("comicArtists") }
            )
        }
        composable("comicTags") {
            ComicDirectoryScreen(
                kind = ComicDirectoryKind.TAG,
                onBackClick = { navController.popBackStack() },
                onEntryClick = { entry ->
                    navController.navigate(
                        comicDirectoryRoute(ComicDirectoryKind.TAG, entry.name, entry.url)
                    )
                }
            )
        }
        composable("comicArtists") {
            ComicDirectoryScreen(
                kind = ComicDirectoryKind.ARTIST,
                onBackClick = { navController.popBackStack() },
                onEntryClick = { entry ->
                    navController.navigate(
                        comicDirectoryRoute(ComicDirectoryKind.ARTIST, entry.name, entry.url)
                    )
                }
            )
        }
        composable(
            route = "comicDirectory?kind={kind}&name={name}&url={url}",
            arguments = listOf(
                navArgument("kind") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            ComicDirectoryResultScreen(
                kind = ComicDirectoryKind.entries.firstOrNull {
                    it.name == args?.getString("kind")
                } ?: ComicDirectoryKind.TAG,
                title = args?.getString("name").orEmpty(),
                url = args?.getString("url").orEmpty(),
                onBackClick = { navController.popBackStack() },
                onComicClick = { comicId ->
                    navController.navigate("comicDetail?comicId=" + Uri.encode(comicId))
                }
            )
        }
        composable(
            route = "comicDetail?comicId={comicId}",
            arguments = listOf(navArgument("comicId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val comicId = backStackEntry.arguments?.getString("comicId").orEmpty()
            ComicDetailScreen(
                comicId = comicId,
                onBackClick = { navController.popBackStack() },
                onReadClick = { id, page, title, coverUrl ->
                    navController.navigate(
                        "comicReader?comicId=" + Uri.encode(id) +
                            "&page=" + page +
                            "&title=" + Uri.encode(title) +
                            "&cover=" + Uri.encode(coverUrl)
                    )
                },
                onComicClick = { id ->
                    navController.navigate("comicDetail?comicId=" + Uri.encode(id)) {
                        popUpTo("comicDetail?comicId={comicId}") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "comicReader?comicId={comicId}&page={page}&title={title}&cover={cover}",
            arguments = listOf(
                navArgument("comicId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 1
                },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("cover") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            ComicReaderScreen(
                comicId = args?.getString("comicId").orEmpty(),
                startPage = args?.getInt("page") ?: 1,
                title = args?.getString("title").orEmpty(),
                coverUrl = args?.getString("cover").orEmpty(),
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = "search?keyword={keyword}&genre={genre}&sort={sort}&ts={ts}",
            arguments = listOf(
                navArgument("keyword") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null },
                navArgument("genre") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null },
                navArgument("sort") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null },
                // 仅用于区分「同参数的新一次点按」，不参与搜索条件
                navArgument("ts") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val keyword = backStackEntry.arguments?.getString("keyword")
            val genre = backStackEntry.arguments?.getString("genre")
            val sort = backStackEntry.arguments?.getString("sort")
            val ts = backStackEntry.arguments?.getString("ts")
            SearchScreen(
                initialKeyword = keyword,
                initialGenre = genre,
                initialSort = sort,
                initialToken = ts,
                onVideoClick = { videoUrl ->
                    navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}") },
                onAuthorClick = { author ->
                    navController.navigateToSearch(searchRoute(keyword = author))
                },
                onVideoLongClick = onVideoLongClick
            )
        }
        composable(
            route = "download?highlight={highlight}",
            arguments = listOf(navArgument("highlight") {
                type = NavType.IntType
                defaultValue = -1
            })
        ) { backStackEntry ->
            DownloadScreen(
                highlightTaskId = backStackEntry.arguments?.getInt("highlight") ?: -1,
                onPlayLocalVideo = { filePath ->
                    navController.navigate("localPlayer?filePath=${Uri.encode(filePath)}")
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
                    navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}") },
                onNavigateToLogin = {
                    navController.navigate("login")
                },
                onVideoLongClick = onVideoLongClick
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
        composable("cloudFavorite") {
            CloudFavoritesScreen(
                onBackClick = { navController.popBackStack() },
                onPlaylistClick = { url ->
                    navController.navigate("userPlaylist?url=${Uri.encode(url)}")
                },
                onLocalFavoritesClick = { navController.navigate("favorite") },
                onLoginClick = { navController.navigate("login") },
                onVideoClick = { videoUrl ->
                    navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                }
            )
        }
        composable(
            route = "userPlaylist?url={url}",
            arguments = listOf(navArgument("url") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            UserPlaylistDetailScreen(
                url = backStackEntry.arguments?.getString("url").orEmpty(),
                onBackClick = { navController.popBackStack() },
                onVideoClick = { videoUrl ->
                    navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}")
                }
            )
        }
        composable("watchStats") {
            WatchStatsScreen(
                onBackClick = { navController.popBackStack() }
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
        composable("update") {
            UpdateScreen(
                onBackClick = { navController.popBackStack() }
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
                        "customize" -> navController.navigate("customize")
                        "launcherIcon" -> navController.navigate("launcherIcon")
                        "backup" -> navController.navigate("backup")
                        "crashLog" -> navController.navigate("crashLog")
                    }
                }
            )
        }
        composable("diagnostics") {
            DiagnosticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("crashLog") {
            CrashLogScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("backup") {
            BackupScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("customize") {
            CustomizeScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("launcherIcon") {
            LauncherIconScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = "batchDownload?authorId={authorId}",
            arguments = listOf(navArgument("authorId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            BatchDownloadScreen(
                initialAuthorId = backStackEntry.arguments?.getString("authorId").orEmpty(),
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
                    navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}") },
                onViewAllVideos = { url ->
                    navController.navigate("videoListPage?url=${Uri.encode(url)}") },
                onViewAllPlaylists = { url ->
                    navController.navigate("playlistListPage?url=${Uri.encode(url)}") },
                onPlaylistClick = { url ->
                    navController.navigate("playlistDetail?url=${Uri.encode(url)}")
                },
                onBatchDownload = { authorId ->
                    navController.navigate("batchDownload?authorId=${Uri.encode(authorId)}")
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
                },
                onVideoLongClick = onVideoLongClick
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
                },
                onVideoLongClick = onVideoLongClick,
                onFavoriteAll = { videos -> onFavoriteAll(videos) }
            )
        }
        composable(
            route = "detail?videoUrl={videoUrl}",
            enterTransition = {
                fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS)) +
                    scaleIn(
                        animationSpec = tween(NAV_TRANSITION_DURATION_MS),
                        initialScale = 0.94f
                    )
            },
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
                onNavigateHome = { navController.navigateToRoot("home") },
                onVideoClick = { newVideoUrl ->
                    navController.navigate("detail?videoUrl=${Uri.encode(newVideoUrl)}") },
                onVideoLongClick = onVideoLongClick,
                onAutoPlayNext = { nextUrl ->
                    // 自动连播是「同屏换片」，替换当前条目，避免返回时逐集回退
                    navController.navigate("detail?videoUrl=${Uri.encode(nextUrl)}") {
                        popUpTo("detail?videoUrl={videoUrl}") { inclusive = true }
                    } },
                // 详情页里点标签 / 作者是「从当前视频跳出去看结果」：必须入栈，
                // 用 navigateToSearch 会先回到栈根，返回时直接落到首页而不是原详情页
                onTagClick = { tag ->
                    navController.navigate(searchRoute(keyword = tag)) },
                onAuthorClick = { author ->
                    navController.navigate(searchRoute(keyword = author)) },
                onAuthorPageClick = { authorPageUrl ->
                    navController.navigate("author?authorPageUrl=${Uri.encode(authorPageUrl)}") },
                onNavigateToLogin = {
                    navController.navigate("login") },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }
        // 本地视频播放页：普通入栈导航，系统返回即回到下载页
        composable(
            route = "localPlayer?filePath={filePath}",
            arguments = listOf(navArgument("filePath") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            LocalPlayerScreen(
                filePath = backStackEntry.arguments?.getString("filePath"),
                onBackClick = { navController.popBackStack() },
                onNavigateHome = { navController.navigateToRoot("home") }
            )
        }
    }
}
