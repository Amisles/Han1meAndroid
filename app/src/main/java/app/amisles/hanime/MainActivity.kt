package app.amisles.hanime

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.amisles.hanime.BuildConfig
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.CrashLogger
import app.amisles.hanime.core.common.util.CrashRecord
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.core.common.util.VolumeKeyPager
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.remote.CloudflareChallenge
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.domain.repository.UpdateRepository
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.NavigationStyle
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.feature.settings.network.CloudflareChallengeDialog
import app.amisles.hanime.core.ui.components.LocalPrivacyMode
import app.amisles.hanime.core.ui.components.LocalVideoLocalState
import app.amisles.hanime.ui.components.BottomNav
import app.amisles.hanime.ui.components.VideoQuickActionsHost
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.ui.viewmodel.VideoLocalStateViewModel
import app.amisles.hanime.ui.viewmodel.VideoQuickActionsViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.amisles.hanime.ui.components.NavRail
import app.amisles.hanime.feature.profile.ProfileScreen
import app.amisles.hanime.feature.settings.UpdateAvailableDialog
import app.amisles.hanime.ui.theme.HanimeTheme
import app.amisles.hanime.ui.theme.PreferencesAppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import app.amisles.hanime.navigation.baseRoute
import app.amisles.hanime.navigation.navigateToSearch
import app.amisles.hanime.navigation.navigateToTab
import app.amisles.hanime.navigation.NAV_TRANSITION_DURATION_MS
import app.amisles.hanime.navigation.NAV_TRANSITION_EXIT_DURATION_MS
import app.amisles.hanime.navigation.HanimeNavGraph
import app.amisles.hanime.navigation.shouldShowBottomBar
import app.amisles.hanime.navigation.searchRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** 由 ShareReceiverActivity 携带视频地址打开详情页。 */
        const val ACTION_OPEN_VIDEO = "app.amisles.hanime.action.OPEN_VIDEO"

        /** 由 DownloadService 的完成通知携带成品文件路径，直达本地播放页。 */
        const val ACTION_PLAY_LOCAL = "app.amisles.hanime.action.PLAY_LOCAL"
        const val EXTRA_OPEN_FILE_PATH = "extra_open_file_path"

        /** 由 DownloadService 的失败通知拉起，直达下载页并定位到出错任务。 */
        const val ACTION_OPEN_DOWNLOADS = "app.amisles.hanime.action.OPEN_DOWNLOADS"
        const val EXTRA_HIGHLIGHT_TASK_ID = "extra_highlight_task_id"

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (VolumeKeyPager.handler?.invoke(true) == true) return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (VolumeKeyPager.handler?.invoke(false) == true) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** 更新仓储：HanimeApp 需要它在启动时发起静默检查。 */
    @Inject
    lateinit var updateRepository: UpdateRepository

    /** 创建时按当前语言包装 Context；Application 的 Resources 覆盖不影响本 Activity，运行中换语言由 getResources 的覆盖刷新。 */
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

    // Activity 自己的 Resources 也要随所选语言刷新：Compose 的对话框 / 弹层用 LocalView 的 Context
    // （即本 Activity）另开窗口，窗口内取资源走的是这份 Resources，而不是组合层提供的 LocalContext。
    @Volatile
    private var localizedResources: Resources? = null

    override fun getResources(): Resources = localizedResources ?: super.getResources()

    private fun applyLanguageResources(language: String) {
        localizedResources = LocaleHelper.resourcesContext(baseContext, language).resources
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 走 Application 的语言快照（onCreate 里已预热），不要直接读 Preferences.appLanguage：
        // 那是加密存储的 getter，在 Activity 创建路径上读会把 Keystore 解密拉回主线程，
        // 等于绕开了 HanimeApplication 专门为此建立的缓存。
        val app = application as? HanimeApplication
        app?.applyLanguage(app.currentLanguage())

        setContent {
            val themeMode by Preferences.themeModeFlow.collectAsStateWithLifecycle()
            val dynamicColor by Preferences.dynamicColorFlow.collectAsStateWithLifecycle()
            val privacyMode by Preferences.privacyModeFlow.collectAsStateWithLifecycle()
            val uiConfig by UiConfigStore.config.collectAsStateWithLifecycle()
            val homeSections by UiConfigStore.homeSections.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 切换语言不再重建 Activity：按当前语言就地换掉组合层的 Resources 与 Configuration，
            // 避免重建期间窗口表面尚未绘制而整屏变黑
            val language by Preferences.appLanguageFlow.collectAsStateWithLifecycle()
            val localizedContext = remember(language) {
                LocalizedResourceContext(this, LocaleHelper.resourcesContext(this, language))
            }
            val platformConfiguration = LocalConfiguration.current
            val localizedConfiguration = remember(platformConfiguration, language) {
                Configuration(platformConfiguration).apply {
                    setLocales(LocaleList(LocaleHelper.localeOf(language)))
                }
            }
            LaunchedEffect(language) {
                app?.applyLanguage(language)
                applyLanguageResources(language)
            }
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalAppPreferences provides PreferencesAppPreferences,
                LocalPrivacyMode provides privacyMode
            ) {
            HanimeTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                uiConfig = uiConfig,
                homeSectionConfig = homeSections
            ) {
                val barColor = MaterialTheme.colorScheme.background
                // 系统栏取当前主题背景色：动态取色开启时与内容背景保持一致
                LaunchedEffect(barColor, darkTheme) {
                    val bgArgb = barColor.toArgb()
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
                HanimeApp(updateRepository = updateRepository)
            }
            }
        }
        attachJankStats()
    }

    /** 仅 debug 挂载：掉帧数据经 AppLogger 落盘，而它在 release 是空实现；放开门禁只会留下每帧回调的开销，采不到任何数据。 */
    private fun attachJankStats() {
        if (!BuildConfig.DEBUG) return
        JankStats.createAndTrack(
            window,
            object : JankStats.OnFrameListener {
                override fun onFrame(frameData: FrameData) {
                    if (frameData.isJank) {
                        AppLogger.d("JankStats", "janky frame ${frameData.frameDurationUiNanos} ns")
                    }
                }
            }
        )
    }
}

/** 首页右滑唤出抽屉：触发距离、触发速度与横向位移消抖阈值。 */
private const val HOME_SWIPE_OPEN_DP = 96f
private const val HOME_SWIPE_VELOCITY_DP_PER_S = 600f
private const val HOME_SWIPE_SLOP_DP = 8f

@Composable
fun HanimeApp(
    updateRepository: UpdateRepository,
) {

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 必须取「基础路由」而非 destination.route 原值：带参目的地的 destination.route 返回的是
    // 完整路由模板（如 "search?keyword={keyword}&genre={genre}&sort={sort}&ts={ts}"），而底部导航 /
    // 导航栏传入的是短路由 "search"，两者永不相等，会让 Tab 的「已在当前页则不动」守卫失效。
    val entryRoute = navBackStackEntry?.destination?.route?.baseRoute()
    // currentBackStackEntryAsState 的首个值恒为 null（Activity 重建后同样如此），用 "home" 兜底
    // 会让底部导航栏在设置页这类隐藏它的页面上先滑入再收回；改为沿用重建前最后已知的路由
    var lastKnownRoute by rememberSaveable { mutableStateOf("") }
    val currentRoute = entryRoute ?: lastKnownRoute.ifEmpty { "home" }
    SideEffect { if (entryRoute != null) lastKnownRoute = entryRoute }

    val showBottomBar = shouldShowBottomBar(currentRoute)

    val sizeInfo = currentWindowSizeInfo()
    val uiCfg = UiConfigStore.config.collectAsStateWithLifecycle().value
    val useRail = uiCfg.resolvedNavStyle(sizeInfo.isTablet) == NavigationStyle.RAIL

    val activity = LocalActivity.current as? ComponentActivity
    val landscapeAllowed = sizeInfo.isTablet || currentRoute.startsWith("detail") ||
        currentRoute.startsWith("localPlayer") || currentRoute.startsWith("comicReader")
    LaunchedEffect(currentRoute, sizeInfo.isTablet) {
        val act = activity ?: return@LaunchedEffect
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
    val videoLocalStateVm: VideoLocalStateViewModel = hiltViewModel()
    val videoLocalState by videoLocalStateVm.state.collectAsStateWithLifecycle()
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
    // onNewIntent 处理应用已在前台的情况；handledIntentTag 存入实例状态，Activity 重建
    // （切语言 / uiMode 变化）后同一 intent 不会被再次消费。
    var handledIntentTag by rememberSaveable { mutableStateOf("") }
    var pendingShareIntent by remember { mutableStateOf(activity?.intent) }
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { intent -> pendingShareIntent = intent }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    LaunchedEffect(pendingShareIntent) {
        val intent = pendingShareIntent ?: return@LaunchedEffect
        val tag = listOf(intent.action, intent.data, intent.extras?.keySet()?.sorted()?.joinToString(",") { key -> "$key=${intent.extras?.get(key)}" })
            .joinToString("|")
        if (handledIntentTag == tag) return@LaunchedEffect
        when (intent.action) {
            MainActivity.ACTION_OPEN_VIDEO -> {
                handledIntentTag = tag
                pendingShareIntent = null
                intent.data?.toString()?.takeIf { it.isNotBlank() }?.let { url ->
                    navController.navigate("detail?videoUrl=${Uri.encode(url)}")
                }
            }
            MainActivity.ACTION_PLAY_LOCAL -> {
                handledIntentTag = tag
                pendingShareIntent = null
                intent.getStringExtra(MainActivity.EXTRA_OPEN_FILE_PATH)?.takeIf { it.isNotBlank() }?.let { path ->
                    navController.navigate("localPlayer?filePath=${Uri.encode(path)}")
                }
            }
            MainActivity.ACTION_OPEN_DOWNLOADS -> {
                handledIntentTag = tag
                pendingShareIntent = null
                val taskId = intent.getIntExtra(MainActivity.EXTRA_HIGHLIGHT_TASK_ID, -1)
                navController.navigate(if (taskId >= 0) "download?highlight=$taskId" else "download")
            }
        }
    }
    // 左滑关闭：拖拽期间的位移，以及松手时跳过的抽屉宽度占比；
    // 关闭动画从松手位置继续，而不是回到完全展开再重播一遍
    var drawerDragOffset by remember { mutableFloatStateOf(0f) }
    var drawerCloseSkip by remember { mutableFloatStateOf(0f) }
    val rawProgress by animateFloatAsState(
        targetValue = if (profileOpen) 1f else 0f,
        animationSpec = tween(durationMillis = NAV_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "profileDrawer"
    )
    // 关闭续接必须是乘法：松手当帧 rawProgress 仍为 1，此时抽屉已视觉位移 skip 比例，
    // progress = rawProgress * (1 - skip) 恰好等于松手位置（1 - skip），随 rawProgress
    // 归零平滑收尾。旧实现 (rawProgress + skip).coerceAtMost(1f) 数学上反向：松手当帧
    // 被 clamp 回 1（弹回全开）、停留 skip×进场时长、收尾再由 skip 复位造成第二次跳变。
    val progress = rawProgress * (1f - drawerCloseSkip)
    LaunchedEffect(profileOpen, rawProgress) {
        if (!profileOpen && rawProgress == 0f) drawerCloseSkip = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showBottomBar && useRail) {
                NavRail(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTab(route) },
                    showLabels = uiCfg.navBarShowLabels
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
                        enter = slideInVertically(animationSpec = tween(NAV_TRANSITION_DURATION_MS)) { it } +
                            fadeIn(animationSpec = tween(NAV_TRANSITION_DURATION_MS)),
                        exit = slideOutVertically(animationSpec = tween(NAV_TRANSITION_EXIT_DURATION_MS)) { it } +
                            fadeOut(animationSpec = tween(NAV_TRANSITION_EXIT_DURATION_MS))
                    ) {
                        BottomNav(
                            currentRoute = currentRoute,
                            onNavigate = { route -> navController.navigateToTab(route) },
                            showLabels = uiCfg.navBarShowLabels
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
                CompositionLocalProvider(LocalVideoLocalState provides videoLocalState) {
                    HanimeNavGraph(
                        navController = navController,
                        onOpenProfile = { profileOpen = true },
                        onVideoLongClick = onVideoLongClick,
                        onFavoriteAll = { videos -> quickActionsVm.favoriteAll(videos) },
                        modifier = if (useRail || currentRoute.startsWith("localPlayer")) {
                            navModifier
                        } else {
                            navModifier.swipeToOpenDrawer(
                                touchSlopPx = swipeTouchSlopPx,
                                openThresholdPx = swipeOpenThresholdPx,
                                velocityThresholdPx = swipeVelocityThresholdPx,
                                onOpen = swipeOpenProfile
                            )
                        }
                    )
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

    // 抽屉内容首次展开后常驻组合，后续展开只做位移动画；关闭态整层已平移出屏，收不到触摸。
    // 每次展开都重建 ProfileScreen 会让打开抽屉的首帧承受整页组合，与页面转场抢同一帧预算。
    var drawerComposed by remember { mutableStateOf(false) }
    LaunchedEffect(profileOpen) {
        if (profileOpen) drawerComposed = true
    }
    val onDrawerNavigate = remember(navController) {
        { route: String ->
            navController.navigate(route)
            profileOpen = false
        }
    }
    if (drawerComposed) {
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
        }
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
            ProfileScreen(onNavigate = onDrawerNavigate)
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
                dismissedUpdateVersion = pendingUpdate.versionName
                navController.navigate("update")
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

/**
 * 只替换 Resources 的 Context 包装。
 *
 * 直接提供语言包装后的 Context 会丢掉 Activity 的窗口令牌，弹窗与 Popup 将无法挂到 Activity 窗口上；
 * 这里保留 Activity 作为 base，仅把 Resources / Assets 换成对应语言的实例。
 */
private class LocalizedResourceContext(base: Context, localized: Context) : ContextWrapper(base) {
    private val localizedResources = localized.resources
    private val localizedAssets = localized.assets

    override fun getResources(): Resources = localizedResources

    override fun getAssets(): AssetManager = localizedAssets
}

