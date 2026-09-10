package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全屏遮罩式弹窗容器：半透明遮罩 + 居中的自定义卡片内容。
 *
 * 存在的意义是让「自绘遮罩弹窗」也能正确响应系统返回键。
 * 直接用 `Box(fillMaxSize).background(遮罩).clickable { 关闭 }` 拼出来的遮罩不参与返回键分发，
 * 用户在确认删除 / 选择画质时按返回会直接退出整个页面，而预期是「先关弹窗」。
 * [Dialog] 默认 `dismissOnBackPress = true`，会把返回键交给 [onDismiss]，语义与用户预期一致。
 *
 * [DialogProperties.usePlatformDefaultWidth] 置 false，保留原先「整屏遮罩 + 内容自定宽度卡片」的观感。
 * 注意：未开启 `decorFitsSystemWindows = false`，因此遮罩止于状态栏 / 导航栏，属可接受的轻微视觉差异。
 *
 * @param onDismiss 返回键或点击遮罩空白处时的关闭回调，由调用方复位自己的弹窗状态
 * @param content 居中显示的卡片内容，宽度由内容自行决定（如 `fillMaxWidth(0.8f)`）
 */
@Composable
fun FullScreenOverlayDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
