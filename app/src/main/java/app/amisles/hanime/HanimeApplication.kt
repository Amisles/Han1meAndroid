package app.amisles.hanime

import android.app.Application
import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.preferences.Preferences
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
        AppLogger.d("APP", "HanimeApplication onCreate")
    }
}
