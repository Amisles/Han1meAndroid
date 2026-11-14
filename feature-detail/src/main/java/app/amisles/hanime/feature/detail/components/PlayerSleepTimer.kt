package app.amisles.hanime.feature.detail.components

/** 会话级睡眠定时：到点暂停（[endAtMillis] > 0）或播完当前集暂停（[afterEpisode]），均不落盘。 */
internal data class PlayerSleepTimer(
    val endAtMillis: Long = 0L,
    val afterEpisode: Boolean = false,
    val minutes: Int = 0
) {
    val active: Boolean get() = endAtMillis > 0L || afterEpisode
}
