package app.amisles.hanime.feature.detail.components

import android.content.pm.ActivityInfo
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(90 - 25))
        assertEquals(DeviceTilt.TOP_TO_RIGHT, classifyDeviceTilt(90 + 25))
    }

    @Test
    fun largeTiltFallsIntoBufferBeforePortrait() {
        assertNull(classifyDeviceTilt(90 - 40))
        assertNull(classifyDeviceTilt(90 + 40))
    }

    @Test
    fun landscapeSidesAreNotSwapped() {
        assertEquals(TOP_TO_RIGHT_ORIENTATION, classifyDeviceTilt(90)?.landscapeOrientation)
        assertEquals(TOP_TO_LEFT_ORIENTATION, classifyDeviceTilt(270)?.landscapeOrientation)
    }

    @Test
    fun pinnedOrientationPrefersLandscapeTilt() {
        assertEquals(TOP_TO_LEFT_ORIENTATION, pin(DeviceTilt.TOP_TO_LEFT, Surface.ROTATION_0))
        assertEquals(TOP_TO_RIGHT_ORIENTATION, pin(DeviceTilt.TOP_TO_RIGHT, Surface.ROTATION_0))
    }

    @Test
    fun pinnedOrientationFallsBackToDisplayRotation() {
        assertEquals(TOP_TO_LEFT_ORIENTATION, pin(null, Surface.ROTATION_90))
        assertEquals(TOP_TO_RIGHT_ORIENTATION, pin(null, Surface.ROTATION_270))
        assertEquals(TOP_TO_LEFT_ORIENTATION, pin(DeviceTilt.PORTRAIT, Surface.ROTATION_90))
        assertEquals(TOP_TO_RIGHT_ORIENTATION, pin(DeviceTilt.PORTRAIT, Surface.ROTATION_270))
    }

    @Test
    fun pinnedOrientationHandlesNaturalLandscapeScreen() {
        assertEquals(TOP_TO_LEFT_ORIENTATION, pin(null, Surface.ROTATION_0, naturalLandscape = true))
        assertEquals(TOP_TO_RIGHT_ORIENTATION, pin(null, Surface.ROTATION_180, naturalLandscape = true))
    }

    @Test
    fun pinnedOrientationResolvesToAPinnedLandscapeConstant() {
        val allowed = setOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        )
        val rotations = listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)
        val tilts = listOf(null, DeviceTilt.PORTRAIT, DeviceTilt.TOP_TO_LEFT, DeviceTilt.TOP_TO_RIGHT)
        rotations.forEach { rotation ->
            tilts.forEach { tilt ->
                listOf(true, false).forEach { natural ->
                    val pinned = pinnedLandscapeOrientation(tilt, rotation, natural)
                    assertTrue("rotation=$rotation tilt=$tilt natural=$natural -> $pinned", pinned in allowed)
                }
            }
        }
    }

    private fun pin(tilt: DeviceTilt?, rotation: Int, naturalLandscape: Boolean = false): Int =
        pinnedLandscapeOrientation(tilt, rotation, naturalLandscape)

    private companion object {
        val TOP_TO_RIGHT_ORIENTATION = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        val TOP_TO_LEFT_ORIENTATION = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
