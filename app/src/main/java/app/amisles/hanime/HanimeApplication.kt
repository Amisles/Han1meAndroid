package app.amisles.hanime

import android.app.Application
import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.feature.detail.ExoPlayerFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HanimeApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        AppLogger.init(this)
        // 在 IO 线程预热 ExoPlayer 磁盘缓存，避免首进详情页在主线程同步做磁盘 I/O（P2-7）
        ExoPlayerFactory.prewarmCache(this)
        AppLogger.d("APP", "HanimeApplication onCreate")
    }
}
