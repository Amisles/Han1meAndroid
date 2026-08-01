package app.amisles.hanime.data.download

import android.content.Context
import app.amisles.hanime.data.local.database.FavoriteDatabase

object DownloadManagerHolder {
    @Volatile
    private var instance: DownloadManager? = null

    fun getInstance(context: Context): DownloadManager {
        return instance ?: synchronized(this) {
            instance ?: run {
                val dao = FavoriteDatabase.getInstance(context).downloadDao()
                DownloadManager(context.applicationContext, dao).also { instance = it }
            }
        }
    }
}