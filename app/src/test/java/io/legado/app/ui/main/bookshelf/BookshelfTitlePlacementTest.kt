package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.bookshelf.compose.BookshelfTitleSettings
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BookshelfTitlePlacementTest {
    @Test fun onlyTheActiveBookTitleConsumesTheSelection() {
        val shown = BookshelfTitleSettings(0, "@font:example", 0x66123456)
        assertSame(shown, shown.belowCover(isBook = true))
        assertNull(shown.belowCover(isBook = false)) // Folder caption.
        assertNull(shown.onCover(eligible = true)) // Default-cover lettering is independent.
        val overlaid = shown.copy(mode = 2)
        assertNull(overlaid.belowCover(isBook = true))
        assertSame(overlaid, overlaid.onCover(eligible = true))
        assertNull(overlaid.onCover(eligible = false))
        val hidden = shown.copy(mode = 1)
        assertNull(hidden.belowCover(isBook = true))
        assertNull(hidden.onCover(eligible = true))
    }
}
