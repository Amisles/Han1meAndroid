package app.amisles.hanime.feature.detail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 朝向判定的边界与缓冲区。
 *
 * 这两组区间直接决定「横屏播放时轻微竖起会不会退出全屏」，
 * 边界一旦被改窄或改宽都会立刻体现在手感上，故用测试钉住。
 */
class PlayerOrientationTest {

    @Test
    fun landscapeRightCoreRange() {
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(90))
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(60))
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(120))
    }

    @Test
    fun landscapeLeftCoreRange() {
        assertEquals(DeviceTilt.TOP_TO_LEFT, classifyDeviceTilt(270))
        assertEquals(DeviceTilt.TOP_TO_LEFT, classifyDeviceTilt(240))
        assertEquals(DeviceTilt.TOP_TO_LEFT, classifyDeviceTilt(300))
    }

    @Test
    fun portraitCoreRange() {
        assertEquals(DeviceTilt.PORTRAIT, classifyDeviceTilt(0))
        assertEquals(DeviceTilt.PORTRAIT, classifyDeviceTilt(20))
        assertEquals(DeviceTilt.PORTRAIT, classifyDeviceTilt(180))
        assertEquals(DeviceTilt.PORTRAIT, classifyDeviceTilt(340))
    }

    @Test
    fun bufferZoneReturnsNull() {
        assertNull(classifyDeviceTilt(45))
        assertNull(classifyDeviceTilt(135))
        assertNull(classifyDeviceTilt(225))
        assertNull(classifyDeviceTilt(315))
    }

    @Test
    fun slightTiltFromLandscapeKeepsLandscape() {
        // 横屏时把手机竖起 25 度（90° → 65°）：仍在横屏判定区间内，不应退出全屏
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(90 - 25))
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(90 + 25))
    }

    @Test
    fun largeTiltFallsIntoBufferBeforePortrait() {
        // 继续竖到 50°：进入缓冲区，维持原判定（不立即退出全屏），留待时间防抖裁决
        assertNull(classifyDeviceTilt(90 - 40))
        assertNull(classifyDeviceTilt(90 + 40))
    }

    @Test
    fun landscapeSidesAreNotSwapped() {
        assertEquals(TOP_TO_RIGHT_ORIENTATION, classifyDeviceTilt(90)?.landscapeOrientation)
        assertEquals(TOP_TO_LEFT_ORIENTATION, classifyDeviceTilt(270)?.landscapeOrientation)
    }

    private companion object {
        val TOP_TO_RIGHT_ORIENTATION = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        val TOP_TO_LEFT_ORIENTATION = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
