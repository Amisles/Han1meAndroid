package app.amisles.hanime.core.common.util

/**
 * 音量键翻页的全局挂载点：阅读类页面进入时挂回调，离开时摘除。
 * 宿主 Activity 在 onKeyDown 里查询并按返回值决定是否消费按键。
 */
object VolumeKeyPager {
    @Volatile
    var handler: ((turnForward: Boolean) -> Boolean)? = null
}
