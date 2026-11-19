package app.amisles.hanime.feature.detail.components

import androidx.compose.ui.unit.dp

// ABR 升档前需连续稳定的 STATE_READY 次数
internal const val STABLE_TICKS_FOR_UPGRADE = 4

// 睡眠定时进入倒计时提示的窗口：最后这段时间才逐秒刷新
internal const val SLEEP_COUNTDOWN_WINDOW_MS = 30_000L

// 进度预览的取帧去抖：拖动过程中位置每帧都在变，等手指停稳再取，避免把取帧请求排成一队
internal const val SEEK_PREVIEW_DEBOUNCE_MS = 80L

internal const val ZOOM_EPSILON = 0.01f

// 缩放还原按钮相对画面中心的下移量：贴住中心会压住暂停按钮，再往下会撞到底部控件
internal val RESET_ZOOM_OFFSET = 46.dp

// 长按触发加速的时长：短于它只会当作点击或滑动，避免误触把倍速改掉
internal const val LONG_PRESS_BOOST_DELAY_MS = 1000L

// 双击判定的时间窗口。单击动作要等窗口结束再执行，否则每次双击快进都会顺带切换控制栏
internal const val DOUBLE_TAP_WINDOW_MS = 300L
