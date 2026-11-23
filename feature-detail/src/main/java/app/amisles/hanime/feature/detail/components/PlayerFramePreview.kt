package app.amisles.hanime.feature.detail.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.domain.remote.VideoAntiHotlink
import java.util.LinkedHashMap

/**
 * 拖动进度时的画面预览取帧器。
 *
 * 同一个 [MediaMetadataRetriever] 实例跨帧复用，并按秒级分桶缓存缩略图：一次拖动会连续产生
 * 几十个目标位置，每帧都新建 retriever 要把整段索引重读一遍，表现为拖动手感变粘。
 *
 * 直链必须带上与播放器一致的 User-Agent 与防盗链 Referer，否则 CDN 直接回 403、取帧恒为空。
 * 连续取帧失败达到上限后才放弃该源：单次超时不该葬送整次播放的预览，而每次拖动都重试又会把拖动变成一次超时等待。
 */
internal object PlayerFramePreview {

    private const val BUCKET_MS = 1_000L
    private const val CACHE_SIZE = 16
    private const val MAX_WIDTH_PX = 320
    private const val MAX_FRAME_FAILURES = 3
    private const val HLS_SUFFIX = ".m3u8"

    private val cache = object : LinkedHashMap<Long, Bitmap>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean =
            size > CACHE_SIZE
    }

    private var retriever: MediaMetadataRetriever? = null
    private var retrieverSource = ""
    private var failedSource = ""
    private var consecutiveFailures = 0

    @Synchronized
    fun frameAt(source: String, positionMs: Long, referer: String): Bitmap? {
        if (source.isBlank() || positionMs < 0L) return null
        // HLS 清单不是可直接解码的媒体文件：retriever 只会在超时后返回 null，先拦掉省一次白等
        if (source.contains(HLS_SUFFIX, ignoreCase = true)) return null
        if (source == failedSource) return null
        val bucket = positionMs / BUCKET_MS
        cache[bucket]?.let { return it }
        val extractor = retrieverFor(source, referer) ?: return null
        val frame = runCatching {
            extractor.getFrameAtTime(
                bucket * BUCKET_MS * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        }.getOrNull()
        if (frame == null) {
            if (++consecutiveFailures >= MAX_FRAME_FAILURES) failedSource = source
            return null
        }
        consecutiveFailures = 0
        val scaled = scaleDown(frame)
        cache[bucket] = scaled
        return scaled
    }

    @Synchronized
    fun release() {
        runCatching { retriever?.release() }
        retriever = null
        retrieverSource = ""
        failedSource = ""
        consecutiveFailures = 0
        cache.clear()
    }

    private fun retrieverFor(source: String, referer: String): MediaMetadataRetriever? {
        if (retrieverSource == source) return retriever
        runCatching { retriever?.release() }
        retriever = null
        retrieverSource = ""
        cache.clear()
        failedSource = ""
        consecutiveFailures = 0
        val created = MediaMetadataRetriever()
        val opened = runCatching {
            if (source.startsWith("http://", ignoreCase = true) ||
                source.startsWith("https://", ignoreCase = true)
            ) {
                created.setDataSource(
                    source,
                    mapOf(
                        VideoAntiHotlink.HEADER_NAME to referer,
                        "User-Agent" to BrowserUserAgent.VALUE
                    )
                )
            } else {
                created.setDataSource(source.removePrefix("file://"))
            }
        }.isSuccess
        if (!opened) {
            runCatching { created.release() }
            failedSource = source
            return null
        }
        retriever = created
        retrieverSource = source
        return created
    }

    private fun scaleDown(frame: Bitmap): Bitmap {
        if (frame.width <= MAX_WIDTH_PX) return frame
        val height = (frame.height.toLong() * MAX_WIDTH_PX / frame.width).toInt().coerceAtLeast(1)
        val scaled = runCatching {
            frame.scale(MAX_WIDTH_PX, height)
        }.getOrDefault(frame)
        if (scaled !== frame) frame.recycle()
        return scaled
    }
}

@Composable
internal fun SeekPreviewOverlay(
    frame: ImageBitmap?,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(SEEK_PREVIEW_WIDTH)
                    .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                    .clip(RoundedCornerShape(6.dp))
            )
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = if (frame != null) 8.dp else 0.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private val SEEK_PREVIEW_WIDTH = 180.dp
