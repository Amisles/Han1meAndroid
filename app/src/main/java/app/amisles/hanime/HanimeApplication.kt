package app.amisles.hanime

import android.app.Application
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.feature.detail.ExoPlayerFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HanimeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        AppLogger.init(this)
        ExoPlayerFactory.prewarmCache(this)
        AppLogger.d("APP", "HanimeApplication onCreate")
    }
}
