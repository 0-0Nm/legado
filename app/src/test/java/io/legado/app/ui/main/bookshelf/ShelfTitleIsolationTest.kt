package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShelfTitleIsolationTest {
    @Test fun restoredPreferencesKeepLegacyAndDormantValues() {
        val prefs = RuntimeEnvironment.getApplication().defaultSharedPreferences
        prefs.edit().putString(PreferKey.bookshelfCoverTitleFont, "@font:legacy")
            .putString(PreferKey.bookshelfCoverTitleColor, "-7").apply()
        var styles = ShelfTitleStore.read(prefs)
        for ((vertical, horizontal) in listOf(ShelfTitleSlot.BookVertical to ShelfTitleSlot.Book,
            ShelfTitleSlot.FolderVertical to ShelfTitleSlot.Folder)) {
            assertEquals(styles[horizontal], styles.effective(vertical))
            val own = ShelfTitleChoice("@font:own", -88, false, false)
            styles = styles.with(vertical, own)
            for (font in listOf(true, false)) for (color in listOf(true, false)) {
                styles = styles.with(vertical, styles[vertical].copy(followFont = font, followColor = color))
                ShelfTitleStore.write(prefs, styles)
                styles = ShelfTitleStore.read(prefs)
                assertEquals(own.font, styles[vertical].font); assertEquals(own.color, styles[vertical].color)
                assertEquals(if (font) styles[horizontal].font else own.font, styles.effective(vertical).font)
                assertEquals(if (color) styles[horizontal].color else own.color, styles.effective(vertical).color)
            }
        }
        assertEquals("@font:legacy", styles[ShelfTitleSlot.Book].font)
        assertEquals(-7, styles[ShelfTitleSlot.Book].color)
        assertNull(styles[ShelfTitleSlot.GroupTag].color)
    }

    @Test fun actualTagBindDoesNotLeakAcrossBarsOrChangeSelection() {
        val app = RuntimeEnvironment.getApplication(); app.injectAsAppCtx()
        val context = ContextThemeWrapper(app, R.style.AppTheme_Light)
        val groups = RoundedTagBarView(context); val filters = RoundedTagBarView(context)
        fun label(bar: RoundedTagBarView, pos: Int): TextView {
            val rv = bar.getChildAt(0) as RecyclerView
            @Suppress("UNCHECKED_CAST") val adapter = rv.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
            return adapter.createViewHolder(rv, adapter.getItemViewType(pos)).also { adapter.bindViewHolder(it, pos) }.itemView as TextView
        }
        val items = listOf(RoundedTagBarView.Item("A"), RoundedTagBarView.Item("B"))
        groups.submitItems(items, 1); filters.submitItems(items, 0)
        val original = label(filters, 0)
        groups.setLabelAppearance(Typeface.MONOSPACE, 0xff5167df.toInt())
        groups.applyTopBarStyle(force = true)
        for (i in items.indices) {
            val view = label(groups, i)
            assertEquals(Typeface.MONOSPACE, view.typeface); assertEquals(0xff5167df.toInt(), view.currentTextColor)
            assertEquals(i == 1, view.isSelected)
        }
        val filter = label(filters, 0)
        assertEquals(original.typeface, filter.typeface); assertEquals(original.currentTextColor, filter.currentTextColor)
        assertTrue(filter.isSelected)
    }
}
