package app.amisles.hanime.data.subscription

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 订阅更新检测。
 *
 * 站点没有「订阅更新」接口，只能定时拉取订阅页并与本地快照比对。
 * 快照只存视频 id 集合：存整条记录会让比对与迁移都变复杂，而判断「是否新片」
 * 只需要 id。
 */
class SubscriptionUpdateChecker(
    private val repository: ContentRepository
) {

    /** 拉取订阅页并返回新出现的视频（按页面顺序）。首次运行只记录基线，不报更新。 */
    suspend fun detectUpdates(): List<HanimeVideo> = withContext(Dispatchers.IO) {
        val page = runCatching { repository.getSubscriptionsPage() }.getOrNull()
            ?: return@withContext emptyList()
        val currentIds = page.videos.map { it.id }.toSet()
        if (currentIds.isEmpty()) return@withContext emptyList()

        val known = Preferences.subscriptionSeenVideoIds
        val isFirstRun = known.isEmpty()
        val fresh = if (isFirstRun) {
            emptyList()
        } else {
            page.videos.filter { it.id !in known }
        }
        // 快照取并集而非直接覆盖：站点分页只返回最近若干条，覆盖会丢掉更早的记录，
        // 下一轮又把它们当成新片重复通知。
        val merged = (known + currentIds).toList().takeLast(MAX_TRACKED_IDS).toSet()
        Preferences.setSubscriptionSeenVideoIds(merged)
        fresh
    }

    private companion object {
        /** 快照上限：订阅视频通常只有几十条，留 2000 足够覆盖长期使用又不无限增长。 */
        const val MAX_TRACKED_IDS = 2000
    }
}
