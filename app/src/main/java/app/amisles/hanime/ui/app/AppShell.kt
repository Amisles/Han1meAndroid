package app.amisles.hanime.ui.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.amisles.hanime.MainActivity
import app.amisles.hanime.core.common.AppFeatures
import app.amisles.hanime.core.ui.components.LocalVideoLocalState
import app.amisles.hanime.core.ui.theme.NavigationStyle
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.util.AppSnackbar
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.preferences.UiHintStore
import app.amisles.hanime.domain.repository.CrashSettings
import app.amisles.hanime.domain.repository.UpdateRepository
import app.amisles.hanime.navigation.HanimeNavGraph
import app.amisles.hanime.navigation.NAV_TRANSITION_DURATION_MS
import app.amisles.hanime.navigation.NAV_TRANSITION_EXIT_DURATION_MS
import app.amisles.hanime.navigation.baseRoute
import app.amisles.hanime.navigation.navigateToSearch
import app.amisles.hanime.navigation.navigateToTab
import app.amisles.hanime.ui.components.BottomNav
import app.amisles.hanime.ui.components.NavRail
import app.amisles.hanime.ui.components.VideoQuickActionsHost
import app.amisles.hanime.ui.viewmodel.VideoLocalStateViewModel
import app.amisles.hanime.ui.viewmodel.VideoQuickActionsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用级外壳：只负责导航骨架、抽屉、快捷操作与全局浮层编排。
 *
 * 页面内容由 [HanimeNavGraph] 提供；启动检查、更新提示、崩溃与引导等一次性流程在
 * AppOverlays.kt 的宿主里各自管理状态，避免继续堆积到根组合函数。
 */
@Composable
fun HanimeApp(
    updateRepository: UpdateRepository,
    crashSettings: CrashSettings,
    hints: UiHintStore
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 必须取「基础路由」：带参目的地的 route 是完整模板，与底部导航的短路由永不相等。
    val entryRoute = navBackStackEntry?.destination?.route?.baseRoute()
    var lastKnownRoute by rememberSaveable { mutableStateOf("") }
    val currentRoute = entryRoute ?: lastKnownRoute.ifEmpty { "home" }
    SideEffect { if (entryRoute != null) lastKnownRoute = entryRoute }

    val showBottomBar = app.amisles.hanime.navigation.shouldShowBottomBar(currentRoute)
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
    val onVideoLongClick: (HanimeVideo) -> Unit = { video -> favoriteSheetVideo = video }
    val currentRouteState = rememberUpdatedState(currentRoute)
    val swipeOpenProfile: () -> Unit = {
        if (currentRouteState.value == "home") profileOpen = true
    }
    val swipeTouchSlopPx = with(density) { HOME_SWIPE_SLOP_DP.dp.toPx() }
    val swipeOpenThresholdPx = with(density) { HOME_SWIPE_OPEN_DP.dp.toPx() }
    val swipeVelocityThresholdPx = with(density) { HOME_SWIPE_VELOCITY_DP_PER_S.dp.toPx() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        var undoTimeout: Job? = null
        AppSnackbar.events.collect { event ->
            undoTimeout?.cancel()
            undoTimeout = launch {
                delay(5_000L)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = event.text,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
        }
    }

    var onboardingDone by rememberSaveable {
        mutableStateOf(!AppFeatures.ONBOARDING_ENABLED || hints.onboardingDone)
    }
    val deepLinkLaunch = remember {
        val action = activity?.intent?.action
        action == MainActivity.ACTION_OPEN_VIDEO ||
            action == MainActivity.ACTION_PLAY_LOCAL ||
            action == MainActivity.ACTION_OPEN_DOWNLOADS
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
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                bottomBar = {
                    // 用 AnimatedVisibility 而非条件渲染：底部栏瞬时消失会让内容内边距在同一帧突变。
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
                        modifier = if (useRail || immersiveRoute) {
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

        VideoQuickActionsHost(
            sheetVideo = favoriteSheetVideo,
            onDismiss = { favoriteSheetVideo = null },
            onSearchRelated = { keyword ->
                favoriteSheetVideo = null
                navController.navigateToSearch(app.amisles.hanime.navigation.searchRoute(keyword = keyword))
            },
            viewModel = quickActionsVm
        )

        ProfileDrawerHost(
            open = profileOpen,
            onClose = { profileOpen = false },
            onNavigate = { route ->
                navController.navigate(route)
                profileOpen = false
            }
        )

        CloudflareChallengeHost()
        CrashNoticeHost(crashSettings) { navController.navigate("crashLog") }
        OnboardingGate(
            done = onboardingDone,
            currentRoute = currentRoute,
            deepLinkLaunch = deepLinkLaunch,
            onDone = {
                hints.markOnboardingDone()
                onboardingDone = true
            },
            onNavigate = { route -> navController.navigate(route) }
        )
        UpdatePromptHost(
            updateRepository = updateRepository,
            onboardingDone = onboardingDone,
            onOpenUpdate = { navController.navigate("update") }
        )
    }

    HandleAppIntents(navController)
}
