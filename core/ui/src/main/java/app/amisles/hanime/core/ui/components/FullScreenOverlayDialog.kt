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
 * 全屏遮罩式弹窗容器：半透明遮罩 + 居中自定义卡片内容。
 *
 * 用 [Dialog] 而非自绘遮罩，是为了让弹窗正确响应系统返回键 —— 自绘 Box 遮罩不参与返回键分发，
 * 用户按返回会直接退出整个页面，而预期是「先关弹窗」。
 * [DialogProperties.usePlatformDefaultWidth] 置 false 以保留整屏遮罩 + 内容自定宽度的观感。
 *
 * @param onDismiss 返回键或点击遮罩空白处时的关闭回调
 * @param content 居中显示的卡片内容，宽度由内容自行决定
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
