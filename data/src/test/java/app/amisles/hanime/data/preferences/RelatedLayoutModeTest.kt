package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedLayoutModeTest {

    @Test
    fun `portrait cards always use grid regardless of the choice`() {
        assertTrue(RelatedLayoutMode.usesGrid(RelatedLayoutMode.LIST, hasPortraitCards = true))
        assertTrue(RelatedLayoutMode.usesGrid(null, hasPortraitCards = true))
        assertTrue(RelatedLayoutMode.usesGrid(RelatedLayoutMode.GRID, hasPortraitCards = true))
    }

    @Test
    fun `landscape cards follow the choice and fall back to list`() {
        assertFalse(RelatedLayoutMode.usesGrid(null, hasPortraitCards = false))
        assertFalse(RelatedLayoutMode.usesGrid(RelatedLayoutMode.LIST, hasPortraitCards = false))
        assertTrue(RelatedLayoutMode.usesGrid(RelatedLayoutMode.GRID, hasPortraitCards = false))
    }
}
