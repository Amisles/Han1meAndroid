package app.amisles.hanime.domain.preferences

/**
 * 一次性界面提示的「是否已展示」标记。
 *
 * 与设置项分开：它们只增不减、没有界面入口，也不参与备份语义，混进设置接口会把两个
 * 生命周期完全不同的东西绑在一起。
 */
interface UiHintStore {

    val onboardingDone: Boolean
    fun markOnboardingDone()

    val playerGestureIntroShown: Boolean
    fun markPlayerGestureIntroShown()

    val notificationPromptShown: Boolean
    fun markNotificationPromptShown()

    val downloadQuickHintDismissed: Boolean
    fun dismissDownloadQuickHint()

    val homeLongPressHintDismissed: Boolean
    fun dismissHomeLongPressHint()

    fun weeklyReportDismissedWeek(): String
    fun setWeeklyReportDismissedWeek(weekKey: String)
}
