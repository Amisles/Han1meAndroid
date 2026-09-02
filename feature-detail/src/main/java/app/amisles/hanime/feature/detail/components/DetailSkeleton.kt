package app.amisles.hanime.feature.detail.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 详情页骨架屏：加载时模拟详情页布局的占位。
 * 手机单列与平板分栏共用 [DetailSkeletonBody] 的详情占位内容，仅外层容器不同。
 */
@Composable
internal fun DetailSkeletonScreen() {
    val transition = rememberInfiniteTransition(label = "detail-skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detail-skeleton-alpha"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 播放器区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        )

        DetailSkeletonBody(alpha = alpha)
        Spacer(modifier = Modifier.height(8.dp))

        // 相关推荐标题骨架
        Box(
            modifier = Modifier
                .padding(horizontal = 15.dp, vertical = 4.dp)
                .width(100.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )

        // 相关推荐视频骨架（3 条）
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 90.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .alpha(alpha)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .alpha(alpha)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .alpha(alpha)
                    )
                }
            }
        }
    }
}

/**
 * 平板分栏骨架屏：左侧 3/4 视频区占位（垂直居中），右侧 1/4 详情占位。
 * 用于平板设备进入详情页的加载期，区别于手机样式的单列骨架。
 */
@Composable
internal fun TabletDetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "tablet-detail-skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tablet-detail-skeleton-alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 左侧：视频区占位（垂直居中）
        Box(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                DetailSkeletonBody(alpha = alpha)
            }
        }
    }
}

/**
 * 两个骨架屏共用的详情占位内容：标题 / 作者 / 元信息 / 操作按钮 / 标签行。
 * [alpha] 为闪烁动画的当前透明度。
 */
@Composable
private fun DetailSkeletonBody(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(15.dp)) {
    // 标题
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .alpha(alpha)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .alpha(alpha)
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 作者行
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 元信息行
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 操作按钮行
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 标签行骨架
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )
        }
    }
    }
}
