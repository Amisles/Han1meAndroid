package app.amisles.hanime

import app.amisles.hanime.ui.app.HanimeApp

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import app.amisles.hanime.BuildConfig
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.core.ui.R as CoreR
import app.amisles.hanime.core.ui.util.VolumeKeyPager
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.domain.repository.AppLockSettings
import app.amisles.hanime.domain.repository.AppearanceSettings
import app.amisles.hanime.domain.repository.CrashSettings
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.repository.UpdateRepository
import app.amisles.hanime.domain.preferences.UiHintStore
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.core.ui.components.LocalPrivacyMode
import app.amisles.hanime.ui.app.AppLockGate
import app.amisles.hanime.ui.theme.HanimeTheme
import app.amisles.hanime.domain.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

        const val ACTION_OPEN_SUBSCRIPTIONS = "app.amisles.hanime.action.OPEN_SUBSCRIPTIONS"

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

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var appearance: AppearanceSettings

    @Inject
    lateinit var crashSettings: CrashSettings

    @Inject
    lateinit var hints: UiHintStore

    @Inject
    lateinit var appLock: AppLockSettings

    /**
     * 画中画是独立窗口：进出小窗都会让 Activity 走 ON_PAUSE，遮罩必须让位，
     * 否则缩小后的窗口会被当作「已退到后台」一并盖住。
     */
    private var inPictureInPicture by mutableStateOf(false)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    /** 创建时按当前语言包装 Context；Application 的 Resources 覆盖不影响本 Activity，运行中换语言由 getResources 的覆盖刷新。 */
    override fun attachBaseContext(newBase: Context) {
        // 走 Application 的语言缓存：该回调位于 Activity 创建最早期，此时 Preferences 可能尚未
        // 初始化（系统可以在极早期拉起 Activity），直接读会落到未初始化的 lateinit 上。
        val lang = (newBase.applicationContext as? HanimeApplication)?.currentLanguage()
            // 兜底同样不读设置存储：Application 尚未就绪时它可能未初始化（lateinit 崩溃），
            // 而默认语言就是唯一合理取值。
            ?: SettingsRepository.LANGUAGE_ZH_CN
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

        // 走 Application 的语言快照（onCreate 里已预热）：它与 Application 级 Resources 同源，
        // 改读持久化值会多一次读盘，且可能与已应用的 Resources 不一致。
        val app = application as? HanimeApplication
        app?.applyLanguage(app.currentLanguage())

        setContent {
            val themeMode by appearance.themeModeFlow.collectAsStateWithLifecycle()
            val dynamicColor by appearance.dynamicColorFlow.collectAsStateWithLifecycle()
            val privacyMode by appearance.privacyModeFlow.collectAsStateWithLifecycle()
            val uiConfig by UiConfigStore.config.collectAsStateWithLifecycle()
            val homeSections by UiConfigStore.homeSections.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 切换语言不再重建 Activity：按当前语言就地换掉组合层的 Resources 与 Configuration，
            // 避免重建期间窗口表面尚未绘制而整屏变黑
            val language by appearance.appLanguageFlow.collectAsStateWithLifecycle()
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
                LocalAppPreferences provides appPreferences,
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
                LaunchedEffect(privacyMode) {
                    if (privacyMode) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                val inBackground = rememberAppInBackground()
                val veilActive = privacyMode && inBackground && !inPictureInPicture
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (veilActive) Modifier.blur(PRIVACY_VEIL_BLUR) else Modifier)
                    ) {
                        AppLockGate(appLock = appLock) {
                            HanimeApp(
                                updateRepository = updateRepository,
                                crashSettings = crashSettings,
                                hints = hints
                            )
                        }
                    }
                    if (veilActive) {
                        PrivacyOverviewVeil()
                    }
                }
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

private val PRIVACY_VEIL_BLUR = 28.dp

/** 退到后台时置位，用于在概览界面遮住画面。 */
@Composable
private fun rememberAppInBackground(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var inBackground by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> inBackground = true
                Lifecycle.Event.ON_RESUME -> inBackground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return inBackground
}

/**
 * 概览界面的隐私遮罩。
 *
 * Android 12 起内容已先行模糊，这层只负责压住残余轮廓；更低版本没有 Modifier.blur 的渲染效果，
 * 必须用不透明底色整屏盖住，否则概览缩略图仍能看清画面。
 */
@Composable
private fun PrivacyOverviewVeil() {
    val opaque = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = if (opaque) 1f else 0.6f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(CoreR.string.privacy_player_hidden),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** 首页右滑唤出抽屉：触发距离、触发速度与横向位移消抖阈值。 */
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

