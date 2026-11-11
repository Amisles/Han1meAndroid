package app.amisles.hanime.core.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * 列表静止后按 URL 去重预热缩略图。
 *
 * memoryCachePolicy 必须保持 DISABLED：磁盘缓存按 URL 存原始字节、与解码尺寸无关，
 * 预取的小尺寸只控制本次解码成本，后续真实请求按实际尺寸命中磁盘即可；若放开内存
 * 缓存，预取产物会以固定解码尺寸驻留，与真实请求的精确尺寸键不匹配，永不命中还白占堆。
 */
object ThumbnailPrefetcher {

    private const val PREFETCH_BATCH = 8
    private const val MAX_TRACKED_URLS = 512

    private val requested =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    fun prefetch(context: Context, urls: List<String>) {
        val toFetch = urls.asSequence()
            .filter { it.isNotBlank() }
            .filter { requested.add(it) }
            .take(PREFETCH_BATCH)
            .toList()
        if (toFetch.isEmpty()) return
        if (requested.size > MAX_TRACKED_URLS) requested.clear()
        val loader: ImageLoader = SingletonImageLoader.get(context)
        toFetch.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(PREFETCH_DECODE_PX, PREFETCH_DECODE_PX)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .listener(onError = { _, _ -> requested.remove(url) })
                .build()
            loader.enqueue(request)
        }
    }
}

private const val PREFETCH_DECODE_PX = 256
