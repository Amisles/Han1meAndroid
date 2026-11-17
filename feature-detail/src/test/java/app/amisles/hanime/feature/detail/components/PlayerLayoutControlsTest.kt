package app.amisles.hanime.feature.detail.components

import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerTapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLayoutControlsTest {

    @Test
    fun `toggling a control flips only that flag`() {
        val toggled = PlayerLayout.DEFAULT.withToggled(PlayerControlId.REWIND)
        assertFalse(toggled.isShown(PlayerControlId.REWIND))
        assertTrue(toggled.isShown(PlayerControlId.PLAY))
        assertTrue(toggled.isShown(PlayerControlId.PROGRESS))
    }

    @Test
    fun `every editable control maps to its own field`() {
        var layout = PlayerLayout.DEFAULT
        PlayerControlId.entries.forEach { layout = layout.withToggled(it) }
        PlayerControlId.entries.forEach { assertFalse(it.name, layout.isShown(it)) }
        PlayerControlId.entries.forEach { layout = layout.withToggled(it) }
        assertEquals(PlayerLayout.DEFAULT, layout)
    }

    @Test
    fun `tap zone cycles through every action`() {
        var layout = PlayerLayout.DEFAULT
        val seen = mutableSetOf(layout.tapActionOf(PlayerTapZoneSlot.LEFT))
        repeat(PlayerTapAction.entries.size - 1) {
            layout = layout.withNextTapAction(PlayerTapZoneSlot.LEFT)
            seen.add(layout.tapActionOf(PlayerTapZoneSlot.LEFT))
        }
        assertEquals(PlayerTapAction.entries.toSet(), seen)
        assertEquals(
            PlayerLayout.DEFAULT.tapLeft,
            layout.withNextTapAction(PlayerTapZoneSlot.LEFT).tapLeft
        )
    }

    @Test
    fun `cycling one zone leaves the others untouched`() {
        val layout = PlayerLayout.DEFAULT.withNextTapAction(PlayerTapZoneSlot.CENTER)
        assertEquals(PlayerLayout.DEFAULT.tapLeft, layout.tapLeft)
        assertEquals(PlayerLayout.DEFAULT.tapRight, layout.tapRight)
        assertNotEquals(PlayerLayout.DEFAULT.tapCenter, layout.tapCenter)
    }
}
