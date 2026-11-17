package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerTapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放器布局的编解码测试。
 *
 * 这份配置会被「数据备份」跨版本搬运，缺键、旧版本、越界值都必须落回默认而不是崩掉或错位。
 */
class PlayerLayoutCodecTest {

    @Test
    fun `round trip keeps every field`() {
        val layout = PlayerLayout(
            showNavButtons = false,
            showRewindButton = false,
            showPlayButton = true,
            showForwardButton = false,
            showVolumeButton = false,
            showSpeedButton = true,
            showQualityButton = false,
            showLockButton = false,
            showOrientationButton = false,
            showFullscreenButton = false,
            showCenterPlayButton = false,
            showTimeLabels = false,
            showProgressBar = false,
            showMiniProgress = false,
            showSeekPreview = false,
            showSleepTimer = false,
            showPipEntry = false,
            tapLeft = PlayerTapAction.FORWARD,
            tapCenter = PlayerTapAction.NONE,
            tapRight = PlayerTapAction.PLAY_PAUSE,
            sideZonePercent = 20
        )
        assertEquals(layout, PlayerLayoutCodec.decode(PlayerLayoutCodec.encode(layout)))
    }

    @Test
    fun `blank input falls back to default`() {
        assertEquals(PlayerLayout.DEFAULT, PlayerLayoutCodec.decode(null))
        assertEquals(PlayerLayout.DEFAULT, PlayerLayoutCodec.decode(""))
    }

    @Test
    fun `unknown version falls back to default`() {
        assertTrue(PlayerLayoutCodec.decode("v=9;showRewindButton=0").showRewindButton)
    }

    @Test
    fun `missing keys keep their defaults`() {
        val decoded = PlayerLayoutCodec.decode("v=1;showRewindButton=0")
        assertFalse(decoded.showRewindButton)
        assertTrue(decoded.showPlayButton)
        assertEquals(PlayerLayout.DEFAULT.tapCenter, decoded.tapCenter)
    }

    @Test
    fun `out of range side zone is clamped`() {
        val decoded = PlayerLayoutCodec.decode("v=1;sideZonePercent=90")
        assertEquals(PlayerLayout.MAX_SIDE_ZONE_PERCENT, decoded.sideZonePercent)
    }
}
