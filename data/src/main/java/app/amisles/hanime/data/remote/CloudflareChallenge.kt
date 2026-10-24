package app.amisles.hanime.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** 站点要求完成 Cloudflare 人机校验。[host] 是被拦下的站点，校验只能在该域名下完成。 */
class CloudflareChallengeException(val host: String) :
    IOException("站点要求完成人机验证，可在「设置 → 人机验证」中手动完成")

/** 一次校验请求。[id] 让同一域名再次触发时也能重新弹出。 */
data class CloudflareChallengeRequest(val host: String, val id: Long)

/**
 * 人机校验的全局状态。
 *
 * 检测在 [NetworkService] 完成，弹窗由 MainActivity 统一承载，成功后各页面自行刷新：
 * 这样任何请求触发校验都能引导用户，不必给每个页面单独接一条链路。
 */
object CloudflareChallenge {

    private val _request = MutableStateFlow<CloudflareChallengeRequest?>(null)
    val request: StateFlow<CloudflareChallengeRequest?> = _request.asStateFlow()

    /** 校验通过的次数，页面据此重试。 */
    private val _solvedTick = MutableStateFlow(0)
    val solvedTick: StateFlow<Int> = _solvedTick.asStateFlow()

    /** 已自动弹过的域名：一次会话内同一域名只打扰一次，其余交给设置页的手动入口。 */
    private val autoShownHosts = mutableSetOf<String>()

    private var nextId = 0L

    fun requestOnFailure(host: String) {
        synchronized(autoShownHosts) {
            if (!autoShownHosts.add(host)) return
        }
        request(host)
    }

    fun request(host: String) {
        if (host.isBlank()) return
        val id = synchronized(this) { ++nextId }
        _request.value = CloudflareChallengeRequest(host, id)
    }

    fun dismiss() {
        _request.value = null
    }

    fun markSolved() {
        _request.value = null
        _solvedTick.value++
    }
}
