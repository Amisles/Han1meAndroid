package app.amisles.hanime.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.amisles.hanime.R as AppR
import app.amisles.hanime.core.ui.R as CoreR
import app.amisles.hanime.data.preferences.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** 快捷设置磁贴：一键切换隐私模式（遮罩封面；息屏时暂停并遮住播放器）。 */
class PrivacyTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observeJob?.cancel()
        observeJob = Preferences.privacyModeFlow
            .onEach { enabled -> renderTile(enabled) }
            .launchIn(scope)
    }

    override fun onStopListening() {
        observeJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val next = !Preferences.privacyMode
        renderTile(next)
        ioScope.launch { Preferences.setPrivacyMode(next) }
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    private fun renderTile(enabled: Boolean) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            icon = Icon.createWithResource(this@PrivacyTileService, AppR.drawable.ic_privacy_mode)
            label = getString(CoreR.string.privacy_tile_label)
            updateTile()
        }
    }
}
