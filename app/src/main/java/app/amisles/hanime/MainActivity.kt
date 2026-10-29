package app.amisles.hanime

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.amisles.hanime.core.common.util.CrashLogger
import app.amisles.hanime.core.common.util.CrashRecord
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.remote.CloudflareChallenge
import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.domain.repository.UpdateRepository
import app.amisles.hanime.feature.settings.openReleasePage
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.ui.CloudflareChallengeDialog
import app.amisles.hanime.ui.components.BottomNav
import app.amisles.hanime.ui.components.VideoQuickActionsHost
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.ui.viewmodel.VideoQuickActionsViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.amisles.hanime.ui.components.NavRail
import app.amisles.hanime.feature.detail.DetailScreen
import app.amisles.hanime.feature.detail.LocalPlayerScreen
import app.amisles.hanime.feature.download.DownloadScreen
import app.amisles.hanime.feature.profile.FavoriteScreen
import app.amisles.hanime.feature.profile.HistoryScreen
import app.amisles.hanime.feature.home.HomeScreen
import app.amisles.hanime.feature.profile.LoginScreen
import app.amisles.hanime.feature.profile.ProfileScreen
import app.amisles.hanime.feature.profile.AccountProfileScreen
import app.amisles.hanime.feature.profile.SubscriptionsScreen
import app.amisles.hanime.feature.profile.WatchStatsScreen
import app.amisles.hanime.feature.search.SearchScreen
import app.amisles.hanime.feature.search.sortOptions
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.feature.settings.AboutScreen
import app.amisles.hanime.feature.settings.AuthorScreen
import app.amisles.hanime.feature.comic.ComicDetailScreen
import app.amisles.hanime.feature.comic.ComicDirectoryResultScreen
import app.amisles.hanime.feature.comic.ComicDirectoryScreen
import app.amisles.hanime.feature.comic.ComicReaderScreen
import app.amisles.hanime.feature.comic.ComicScreen
import app.amisles.hanime.feature.settings.BackupScreen
import app.amisles.hanime.feature.settings.CrashLogScreen
import app.amisles.hanime.ui.screens.PlaylistDetailScreen
import app.amisles.hanime.ui.screens.PlaylistListPageScreen
import app.amisles.hanime.feature.settings.SettingsScreen
import app.amisles.hanime.feature.settings.UpdateScreen
import app.amisles.hanime.feature.settings.UpdateAvailableDialog
import app.amisles.hanime.feature.settings.CustomizeScreen
import app.amisles.hanime.feature.settings.DiagnosticsScreen
import app.amisles.hanime.ui.screens.VideoListPageScreen
import app.amisles.hanime.feature.download.BatchDownloadScreen
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeBackgroundLight
import app.amisles.hanime.ui.theme.HanimeTheme
import app.amisles.hanime.ui.theme.PreferencesAppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** 由 ShareReceiverActivity 携带视频地址打开详情页。 */
        const val ACTION_OPEN_VIDEO = "app.amisles.hanime.action.OPEN_VIDEO"

        /** 由 DownloadService 的完成通知携带成品文件路径，直达本地播放页。 */
        const val ACTION_PLAY_LOCAL = "app.amisles.hanime.action.PLAY_LOCAL"
        const val EXTRA_OPEN_FILE_PATH = "extra_open_file_path"
    }

    /** 更新仓储：HanimeApp 需要它在启动时发起静默检查。 */
    @Inject
    lateinit var updateRepository: UpdateRepository

    /** 每个 Activity 需独立包装 Context 才能使切换语言后的资源生效；Application 级别不影响本 Activity 的 Resources。 */
    override fun attachBaseContext(newBase: Context) {
        // 必须读取已初始化的 Preferences，不可再用明文 getSharedPreferences 打开同一文件：
        // 会与 EncryptedSharedPreferences 冲突并反复误触发「明文→加密」迁移，覆盖已存的主题/语言。
        // 走 Application 的语言缓存：该回调位于 Activity 创建最早期，不应在这里做加密存储解密。
        val lang = (newBase.applicationContext as? HanimeApplication)?.currentLanguage()
            // 兜底同样不读 Preferences：Application 尚未就绪时它可能未初始化（lateinit 崩溃），
            // 而默认语言就是唯一合理取值。
            ?: Preferences.LANGUAGE_ZH_CN
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)

        // 走 Application 的语言快照（onCreate 里已预热），不要直接读 Preferences.appLanguage：
        // 那是加密存储的 getter，在 Activity 创建路径上读会把 Keystore 解密拉回主线程，
        // 等于绕开了 HanimeApplication 专门为此建立的缓存。
        val app = application as? HanimeApplication
        app?.applyLanguage(app.currentLanguage())

        setContent {
            val themeMode by Preferences.themeModeFlow.collectAsStateWithLifecycle()
            val uiConfig by UiConfigStore.config.collectAsStateWithLifecycle()
            val homeSections by UiConfigStore.homeSections.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 依据主题模式动态调整系统栏图标颜色
            LaunchedEffect(darkTheme) {
                val bgArgb = (if (darkTheme) HanimeBackground else HanimeBackgroundLight).toArgb()
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(bgArgb)
                    } else {
                        SystemBarStyle.light(bgArgb, bgArgb)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(bgArgb)
                    } else {
                        SystemBarStyle.light(bgArgb, bgArgb)
                    }
                )
            }
            CompositionLocalProvider(LocalAppPreferences provides PreferencesAppPreferences) {
            HanimeTheme(
                themeMode = themeMode,
                uiConfig = uiConfig,
                homeSectionConfig = homeSections
            ) {
                HanimeApp(updateRepository = updateRepository)
            }
            }
        }
    }
}

/** 路由转场进场时长（毫秒）。首帧成本下移后，短促淡入即可掩盖残余开销。 */
private const val TRANSITION_DURATION_MS = 220

/** 退场时长略短于进场：让目标页更快接管画面，减少两页同时绘制的帧数。 */
private const val TRANSITION_EXIT_DURATION_MS = 160

/** 首页右滑唤出抽屉：触发距离、触发速度与横向位移消抖阈值。 */
private const val HOME_SWIPE_OPEN_DP = 96f
private const val HOME_SWIPE_VELOCITY_DP_PER_S = 600f
private const val HOME_SWIPE_SLOP_DP = 8f

/** 取路由的基础路径：只保留 '?' 之前的一段，使带参目的地能与底部导航 / 导航栏的短路由比较。 */
private fun String.baseRoute(): String = substringBefore('?')

internal fun moreUrlSortToken(moreUrl: String): String? {
    val at = moreUrl.indexOf("/sort/")
    if (at < 0) return null
    return moreUrl.substring(at + "/sort/".length)
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .takeIf { it.isNotEmpty() }
        ?.let(::percentDecode)
}

internal fun sortApiValueOf(section: HomeSection): String? =
    moreUrlSortToken(section.moreUrl)
        ?: sortOptions.firstOrNull { it.label == section.title }?.apiValue

internal fun moreUrlGenreToken(moreUrl: String): String? {
    val rest = listOf("/genre/", "/genres/")
        .firstNotNullOfOrNull { marker ->
            val at = moreUrl.indexOf(marker)
            if (at < 0) null else moreUrl.substring(at + marker.length)
        }
        ?: return null
    return rest
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .takeIf { it.isNotEmpty() }
        ?.let(::percentDecode)
}

internal fun genreApiValueOf(section: HomeSection): String? {
    val token = moreUrlGenreToken(section.moreUrl)
    if (token != null) {
        return categories.firstOrNull { it.label == token || it.apiValue == token }?.apiValue
    }
    return categories.firstOrNull { it.label == section.title }?.apiValue
}

/** 构造搜索页路由；ts 让每次点按都成为一次可区分的新意图，不受 restoreState 复用影响。 */
internal fun searchRoute(keyword: String? = null, genre: String? = null, sort: String? = null): String {
    val params = buildList {
        if (!keyword.isNullOrBlank()) add("keyword=" + Uri.encode(keyword))
        if (!genre.isNullOrBlank()) add("genre=" + Uri.encode(genre))
        if (!sort.isNullOrBlank()) add("sort=" + Uri.encode(sort))
        add("ts=" + System.currentTimeMillis())
    }
    return "search?" + params.joinToString("&")
}

internal fun moreUrlPageRoute(moreUrl: String): String? {
    val path = moreUrl.substringAfter("://", moreUrl)
        .substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    val head = segments.firstOrNull() ?: return null
    val tail = segments.getOrNull(1)
    val encoded = Uri.encode(moreUrl)
    return when (head) {
        "tag", "tags" -> tail?.let { "search?keyword=${Uri.encode(percentDecode(it))}" }
        "author", "authors" -> "author?authorPageUrl=$encoded"
        "playlist", "playlists" -> "playlistDetail?url=$encoded"
        "sort", "search", "genre", "genres" -> "videoListPage?url=$encoded"
        else -> null
    }
}

internal fun comicDirectoryRoute(kind: ComicDirectoryKind, name: String, url: String): String =
    "comicDirectory?kind=" + kind.name + "&name=" + Uri.encode(name) + "&url=" + Uri.encode(url)

internal fun percentDecode(raw: String): String {
    if ('%' !in raw) return raw
    val bytes = java.io.ByteArrayOutputStream()
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%' && i + 2 < raw.length) {
            val hex = raw.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.write(hex)
                i += 3
                continue
            }
        }
        val encoded = c.toString().toByteArray(Charsets.UTF_8)
        bytes.write(encoded, 0, encoded.size)
        i++
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}

/**
 * 回到栈根后压入目标路由，供「切 Tab」与「跳搜索」共用。
 *
 * 不能用 saveState / restoreState：扁平图下 popUpTo 的目标恒为 startDestination，
 * 保存与恢复共用同一个 destination id —— pop 时把被弹出的页面存到该 id 下，
 * restore 紧接着又把它取回来，表现为「点 Tab 没有反应」或「回到上一次的详情页」。
 */
private fun NavController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

/** 跳转到搜索页。 */
private fun NavController.navigateToSearch(route: String) = navigateToRoot(route)

/** 切换底部导航 / 导航栏的 Tab；目标已是当前 Tab 时不动。 */
private fun NavController.navigateToTab(route: String) {
    if (currentDestination?.route?.baseRoute() == route) return
    navigateToRoot(route)
}

@Composable
fun HanimeApp(
    updateRepository: UpdateRepository,
) {

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 必须取「基础路由」而非 destination.route 原值：带参目的地的 destination.route 返回的是
    // 完整路由模板（如 "search?keyword={keyword}&genre={genre}&sort={sort}"），而底部导航 /
    // 导航栏传入的是短路由 "search"，两者永不相等，会让 Tab 的「已在当前页则不动」守卫失效。
    val currentRoute = navBackStackEntry?.destination?.route?.baseRoute() ?: "home"

    val showBottomBar = !currentRoute.startsWith("detail") &&
        currentRoute != "about" &&
        currentRoute != "update" &&
        currentRoute != "login" &&
        currentRoute != "settings" &&
        currentRoute != "backup" &&
        currentRoute != "crashLog" &&
        currentRoute != "comics" &&
        currentRoute != "comicTags" &&
        currentRoute != "comicArtists" &&
        !currentRoute.startsWith("comicDirectory") &&
        currentRoute != "watchStats" &&
        currentRoute != "batchDownload" &&
        !currentRoute.startsWith("subscriptions") &&
        !currentRoute.startsWith("author") &&
        !currentRoute.startsWith("videoListPage") &&
        !currentRoute.startsWith("playlistListPage") &&
        !currentRoute.startsWith("playlistDetail") &&
        !currentRoute.startsWith("accountEdit") &&
        !currentRoute.startsWith("localPlayer") &&
        !currentRoute.startsWith("comicDetail") &&
        !currentRoute.startsWith("comicReader")

    val sizeInfo = currentWindowSizeInfo()
    val useRail = sizeInfo.useNavigationRail

    // 打开发行版页面 / 安装包直链需要 Context；在组合期取一次即可
    val appContext = LocalContext.current

    val activity = LocalContext.current as? ComponentActivity
    val landscapeAllowed = sizeInfo.isTablet || currentRoute.startsWith("detail") ||
        currentRoute.startsWith("localPlayer") || currentRoute.startsWith("comicReader")
    LaunchedEffect(currentRoute) {
        val act = activity ?: return@LaunchedEffect
        if (sizeInfo.isTablet) return@LaunchedEffect
        runCatching {
            act.requestedOrientation = if (landscapeAllowed) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    val density = LocalDensity.current
    var profileOpen by remember { mutableStateOf(false) }
    val quickActionsVm: VideoQuickActionsViewModel = hiltViewModel()
    var favoriteSheetVideo by remember { mutableStateOf<HanimeVideo?>(null) }
    val onVideoLongClick: (HanimeVideo) -> Unit = { video ->
        favoriteSheetVideo = video
    }
    // 经 rememberUpdatedState 读取：swipeToOpenDrawer 的 pointerInput 只以像素常量为 key，
    // 手势协程在首次组合时启动并捕获这里的闭包，直接读 currentRoute 会永远拿到启动时的旧值，
    // 「仅首页唤出抽屉」的守卫在切到其他页后全部失效
    val currentRouteState = rememberUpdatedState(currentRoute)
    val swipeOpenProfile: () -> Unit = {
        if (currentRouteState.value == "home") profileOpen = true
    }
    val swipeTouchSlopPx = with(density) { HOME_SWIPE_SLOP_DP.dp.toPx() }
    val swipeOpenThresholdPx = with(density) { HOME_SWIPE_OPEN_DP.dp.toPx() }
    val swipeVelocityThresholdPx = with(density) { HOME_SWIPE_VELOCITY_DP_PER_S.dp.toPx() }

    // ── 启动时的静默更新检查 ────────────────────────────────────────────────
    // 只在一处发起：HanimeApp 在整个进程内只组合一次，放到各个 Screen 里会重复触发。
    // 检查本身受 6 小时节流约束，且失败完全静默 —— 用户不该因为「连不上 GitHub」看到任何提示。
    val availableUpdate by updateRepository.availableUpdate.collectAsStateWithLifecycle()
    val autoCheckUpdate by updateRepository.autoCheckEnabledFlow.collectAsStateWithLifecycle()
    val currentSkippedVersion by updateRepository.skippedVersionFlow.collectAsStateWithLifecycle()
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(autoCheckUpdate) {
        if (autoCheckUpdate) {
            runCatching { updateRepository.checkForUpdate(force = false) }
        }
    }

    // ── 外链进入：分享 / 深链携带的视频地址 ──────────────────────────────────
    // onNewIntent 处理应用已在前台的情况；handledIntent 保证同一 intent 只消费一次。
    var handledIntent by remember { mutableStateOf<Any?>(null) }
    var pendingShareIntent by remember { mutableStateOf(activity?.intent) }
    LaunchedEffect(activity) {
        activity?.addOnNewIntentListener { intent -> pendingShareIntent = intent }
    }
    LaunchedEffect(pendingShareIntent) {
        val intent = pendingShareIntent ?: return@LaunchedEffect
        if (handledIntent == intent) return@LaunchedEffect
        when (intent.action) {
            MainActivity.ACTION_OPEN_VIDEO -> {
                handledIntent = intent
                pendingShareIntent = null
                intent.data?.toString()?.takeIf { it.isNotBlank() }?.let { url ->
                    navController.navigate("detail?videoUrl=${Uri.encode(url)}")
                }
            }
            MainActivity.ACTION_PLAY_LOCAL -> {
                handledIntent = intent
                pendingShareIntent = null
                intent.getStringExtra(MainActivity.EXTRA_OPEN_FILE_PATH)?.takeIf { it.isNotBlank() }?.let { path ->
                    navController.navigate("localPlayer?filePath=${Uri.encode(path)}")
                }
            }
        }
    }
    // 左滑关闭：拖拽期间的位移，以及松手时跳过的抽屉宽度占比；
    // 关闭动画从松手位置继续，而不是回到完全展开再重播一遍
    var drawerDragOffset by remember { mutableFloatStateOf(0f) }
    var drawerCloseSkip by remember { mutableFloatStateOf(0f) }
    val rawProgress by animateFloatAsState(
        targetValue = if (profileOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "profileDrawer"
    )
    // 关闭续接必须是乘法：松手当帧 rawProgress 仍为 1，此时抽屉已视觉位移 skip 比例，
    // progress = rawProgress * (1 - skip) 恰好等于松手位置（1 - skip），随 rawProgress
    // 归零平滑收尾。旧实现 (rawProgress + skip).coerceAtMost(1f) 数学上反向：松手当帧
    // 被 clamp 回 1（弹回全开）、停留 skip×320ms、收尾再由 skip 复位造成第二次跳变。
    val progress = rawProgress * (1f - drawerCloseSkip)
    LaunchedEffect(profileOpen, rawProgress) {
        if (!profileOpen && rawProgress == 0f) drawerCloseSkip = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showBottomBar && useRail) {
                NavRail(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTab(route) }
                )
            }
            Scaffold(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    // 用 AnimatedVisibility 而非条件渲染：底部栏瞬时消失会让 NavHost 的
                    // 内容内边距在同一帧由 ~80dp 突变为 0，与目标页首帧叠加成一次明显掉帧。
                    AnimatedVisibility(
                        visible = showBottomBar && !useRail,
                        enter = slideInVertically(animationSpec = tween(TRANSITION_DURATION_MS)) { it } +
                            fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)),
                        exit = slideOutVertically(animationSpec = tween(TRANSITION_EXIT_DURATION_MS)) { it } +
                            fadeOut(animationSpec = tween(TRANSITION_EXIT_DURATION_MS))
                    ) {
                        BottomNav(
                            currentRoute = currentRoute,
                            onNavigate = { route -> navController.navigateToTab(route) }
                        )
                    }
                }
            ) { innerPadding ->
                // 沉浸式路由（本地播放页）不预留底部导航栏与系统栏内边距，使页面的「垂直居中」相对整块屏幕
                val immersiveRoute = currentRoute.startsWith("localPlayer")
                val navModifier = if (immersiveRoute) {
                    Modifier
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                }
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = if (useRail) {
                        navModifier
                    } else {
                        navModifier.swipeToOpenDrawer(
                            touchSlopPx = swipeTouchSlopPx,
                            openThresholdPx = swipeOpenThresholdPx,
                            velocityThresholdPx = swipeVelocityThresholdPx,
                            onOpen = swipeOpenProfile
                        )
                    },
                    enterTransition = {
                        fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) +
                            slideInHorizontally(
                                animationSpec = tween(TRANSITION_DURATION_MS),
                                initialOffsetX = { it / 10 }
                            )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(TRANSITION_EXIT_DURATION_MS))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)) +
                            slideInHorizontally(
                                animationSpec = tween(TRANSITION_DURATION_MS),
                                initialOffsetX = { -it / 10 }
                            )
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(TRANSITION_EXIT_DURATION_MS)) +
                            slideOutHorizontally(
                                animationSpec = tween(TRANSITION_DURATION_MS),
                                targetOffsetX = { it / 10 }
                            )
                    }
                ) {
                    composable("home") {
                        HomeScreen(
                            onVideoClick = { videoUrl ->
                                navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}") },
                            // 保持 saveState / launchSingleTop / restoreState 不变：
                            // 它们是底部 Tab 状态保存机制的一部分，navigateToTab 依赖这套配对。
                            //
                            // 附带 ts 时间戳：条目可能被 launchSingleTop / restoreState 复用，
                            // 此时参数相同会被 ViewModel 的指纹判为「已注入」而跳过。
                            // 时间戳让每次「点大图」都是一次可区分的新意图，
                            // 而「从详情页返回」参数不变，仍会被正确跳过。
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
                            onProfileClick = { profileOpen = true },
                            onComicClick = { navController.navigate("comics") },
                            onVideoLongClick = onVideoLongClick
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
                    composable("download") {
                        DownloadScreen(
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
                                navController.navigate("detail?videoUrl=${Uri.encode(videoUrl)}") },
                            onViewAllVideos = { url ->
                                navController.navigate("videoListPage?url=${Uri.encode(url)}") },
                            onViewAllPlaylists = { url ->
                                navController.navigate("playlistListPage?url=${Uri.encode(url)}") },
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
                            onFavoriteAll = { videos -> quickActionsVm.favoriteAll(videos) }
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
                                navController.navigate("detail?videoUrl=${Uri.encode(newVideoUrl)}") },
                            onVideoLongClick = onVideoLongClick,
                            onAutoPlayNext = { nextUrl ->
                                // 自动连播是「同屏换片」，替换当前条目，避免返回时逐集回退
                                navController.navigate("detail?videoUrl=${Uri.encode(nextUrl)}") {
                                    popUpTo("detail?videoUrl={videoUrl}") { inclusive = true }
                                } },
                            onTagClick = { tag ->
                                navController.navigateToSearch(searchRoute(keyword = tag)) },
                            onAuthorClick = { author ->
                                navController.navigateToSearch(searchRoute(keyword = author)) },
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
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

    // ---- 我的页面抽屉：左侧滑出，覆盖整个 Scaffold（含底部导航）----
    val showOverlay by remember { derivedStateOf { profileOpen || progress > 0.001f } }

    VideoQuickActionsHost(
        sheetVideo = favoriteSheetVideo,
        onDismiss = { favoriteSheetVideo = null },
        onSearchRelated = { keyword ->
            favoriteSheetVideo = null
            navController.navigateToSearch(searchRoute(keyword = keyword))
        },
        viewModel = quickActionsVm
    )

    // 抽屉展开或关闭动画期间拦截返回键，先收起抽屉而非退出 Activity；
    // 命中条件与遮罩层一致（progress > 0 表示仍在关闭动画中），避免动画期间把返回键漏给系统。
    BackHandler(enabled = showOverlay) {
        profileOpen = false
    }

    if (showOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 在绘制 lambda 内读 progress：只失效绘制阶段，既不触发重组也不额外分配图层
                .drawBehind { drawRect(color = Color.Black, alpha = 0.5f * progress) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { profileOpen = false }
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.8f)
                .graphicsLayer {
                    val base = -(1f - progress) * size.width
                    translationX = base + drawerDragOffset
                }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    val closeThreshold = with(density) { 120.dp.toPx() }
                    detectHorizontalDragGestures(
                        onDragStart = {
                            drawerDragOffset = 0f
                            drawerCloseSkip = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            drawerDragOffset = (drawerDragOffset + dragAmount).coerceAtMost(0f)
                            change.consume()
                        },
                        onDragEnd = {
                            val dragged = -drawerDragOffset
                            if (dragged > closeThreshold) {
                                drawerCloseSkip = (dragged / size.width).coerceAtMost(1f)
                                drawerDragOffset = 0f
                                profileOpen = false
                            } else {
                                drawerDragOffset = 0f
                                drawerCloseSkip = 0f
                            }
                        },
                        onDragCancel = {
                            drawerDragOffset = 0f
                            drawerCloseSkip = 0f
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
    // ── 人机校验弹窗 ──────────────────────────────────────────────────────
    // 请求被 Cloudflare 拦成挑战页时由网络层登记，这里统一承载：任意页面触发都能引导用户，
    // 不必给每个页面单独接一条链路。校验通过后由各页面监听 solvedTick 自行重试。
    val challenge by CloudflareChallenge.request.collectAsStateWithLifecycle()
    challenge?.let { pending ->
        key(pending.id) {
            CloudflareChallengeDialog(
                host = pending.host,
                onDismiss = { CloudflareChallenge.dismiss() },
                onSolved = { CloudflareChallenge.markSolved() }
            )
        }
    }

    // ── 上次异常退出提示 ──────────────────────────────────────────────────
    // 每次崩溃只提示一次：用崩溃文件名作已读标记，文件名带时间戳天然唯一。
    var crashNotice by remember { mutableStateOf<CrashRecord?>(null) }
    LaunchedEffect(Unit) {
        // takeUnseen 会逐个全量读取崩溃文件（最多 10 份 × 256KB）并读一次加密偏好，属磁盘 IO；
        // 与 CrashLogViewModel 对同一 API 的用法对齐，避免冷启动首帧后阻塞主线程
        crashNotice = withContext(Dispatchers.IO) {
            CrashLogger.takeUnseen(Preferences.acknowledgedCrashFile)
        }
    }
    crashNotice?.let { record ->
        AlertDialog(
            onDismissRequest = {
                Preferences.setAcknowledgedCrashFile(record.file.name)
                crashNotice = null
            },
            title = { Text(stringResource(R.string.crash_notice_title)) },
            text = {
                Text(
                    text = stringResource(R.string.crash_notice_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Preferences.setAcknowledgedCrashFile(record.file.name)
                    crashNotice = null
                    navController.navigate("crashLog")
                }) {
                    Text(stringResource(R.string.crash_notice_view))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Preferences.setAcknowledgedCrashFile(record.file.name)
                    crashNotice = null
                }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── 启动时的更新提示 ──────────────────────────────────────────────────
    // 三条件同时成立才弹：有可用更新、该版本未被用户跳过、且用户没有在本次运行里点过「稍后」。
    // dismissedUpdateVersion 用 rememberSaveable，旋转屏幕后不会重复弹出打扰。
    // 延后约 1.2s 再弹：更新检查是网络请求，若在冷启动的第一秒就盖住首页，
    // 会让用户觉得「应用一打开就弹窗」。等首屏稳定后再提示，观感上更接近「打开后才提醒你」。
    var updatePromptReady by remember { mutableStateOf(false) }
    LaunchedEffect(availableUpdate) {
        if (availableUpdate != null && !updatePromptReady) {
            kotlinx.coroutines.delay(1_200L)
            updatePromptReady = true
        }
    }
    val pendingUpdate = availableUpdate
    if (updatePromptReady &&
        pendingUpdate != null &&
        pendingUpdate.versionName != currentSkippedVersion &&
        pendingUpdate.versionName != dismissedUpdateVersion
    ) {
        UpdateAvailableDialog(
            update = pendingUpdate,
            onUpdate = {
                // 有安装包直链时直接下载，否则退到发行版页面让用户自行选择
                val url = pendingUpdate.apkUrl.ifBlank { pendingUpdate.htmlUrl }
                openReleasePage(appContext, url)
                dismissedUpdateVersion = pendingUpdate.versionName
            },
            onSkipVersion = {
                updateRepository.skipVersion(pendingUpdate.versionName)
                dismissedUpdateVersion = pendingUpdate.versionName
            },
            onDismiss = { dismissedUpdateVersion = pendingUpdate.versionName }
        )
    }

    }
}

/**
 * 首页手势：单指从页面任意位置向右滑，位移或速度达到阈值即唤出「我的」抽屉。
 *
 * 判定为抽屉手势后持续消费事件，避免首页列表跟着位移；反向滑与竖滑直接放行给子级，
 * 不影响原有列表滚动与下拉刷新。
 */
private fun Modifier.swipeToOpenDrawer(
    touchSlopPx: Float,
    openThresholdPx: Float,
    velocityThresholdPx: Float,
    onOpen: () -> Unit
): Modifier = pointerInput(touchSlopPx, openThresholdPx, velocityThresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var distanceX = 0f
        var lastX = down.position.x
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var triggered = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (change.isConsumed) return@awaitEachGesture
            val deltaX = change.position.x - lastX
            lastX = change.position.x
            distanceX += deltaX
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            if (!triggered) {
                // 位移必须明显压倒纵向偏移，避免与列表滚动抢手势
                if (distanceX > touchSlopPx && abs(change.position.y - down.position.y) < distanceX) {
                    triggered = true
                    onOpen()
                }
                if (distanceX < -touchSlopPx) return@awaitEachGesture
            }
            if (triggered) change.consume()
        }
        if (triggered) return@awaitEachGesture
        if (distanceX < openThresholdPx &&
            velocityTracker.calculateVelocity().x >= velocityThresholdPx
        ) {
            onOpen()
        }
    }
}
