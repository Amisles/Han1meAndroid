package app.amisles.hanime

import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.data.remote.VideoAntiHotlink
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Coil 图片请求专用的 OkHttpClient。
 *
 * **为什么需要它**：图片 CDN 与站点同受 Cloudflare 人机校验约束，按 User-Agent 判定浏览器。
 * Coil 的默认网络层是裸的 OkHttpClient，发出的是 `okhttp/x.y.z`，于是每个缩略图请求都被判 403
 * —— 表现为「所有未命中磁盘缓存的封面空白」，而历史 / 收藏 / 继续观看因为复用了库里存的
 * 同一 URL、命中图片缓存看起来仍然正常（换站点或清缓存后才会整片空白）。
 *
 * 站点 HTML 请求早已通过 [BrowserUserAgent] 统一 UA（cf_clearance 与 UA 绑定），
 * 图片通道一直漏了这一步，这里补齐 UA 与防盗链 Referer。
 */
internal object ImageHttpClient {

    /** 懒加载单例：全进程复用同一连接池与线程池，避免每张图新建客户端。 */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(withImageHeaders(chain.request())) }
            .build()
    }

    /**
     * 给图片请求补上浏览器 UA 与防盗链 Referer。
     *
     * UA 固定；Referer 每次请求现取 —— 站点基址可在设置页随时改成镜像站，
     * 缓存住就会像视频直链那样把旧域名发出去被判盗链（见 VideoAntiHotlink 的同类修正）。
     */
    fun withImageHeaders(request: Request): Request =
        request.newBuilder()
            .header("User-Agent", BrowserUserAgent.VALUE)
            .header(VideoAntiHotlink.REFERER_HEADER, VideoAntiHotlink.referer)
            .build()
}
