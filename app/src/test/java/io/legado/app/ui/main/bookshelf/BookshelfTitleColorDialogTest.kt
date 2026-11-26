package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BookshelfTitleColorDialogTest {
    @Test fun pickerAndLocalResetStayInDraftUntilLayoutApply() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val owner = Robolectric.buildActivity(FragmentActivity::class.java)
        owner.get().setTheme(R.style.AppTheme_Light)
        owner.setup()
        val manager = owner.get().supportFragmentManager
        var saved = BookshelfConfigValues(0, false, false, false, false, true, 0, 0, 0, 0, 2, 12)
        var commits = 0
        fun open() = BookshelfConfigDialog.create(saved) { saved = it; commits++ }.also {
            it.showNow(manager, "layout")
        }
        fun picker(host: BookshelfConfigDialog): AlertDialog {
            host.pickTitleColor()
            host.childFragmentManager.executePendingTransactions()
            val child = host.childFragmentManager.fragments.single() as ColorPickerDialog
            assertSame(host, child.parentFragment)
            return (child.requireDialog() as AlertDialog).also { assertTrue(it.isShowing) }
        }
        val main = shadowOf(Looper.getMainLooper())
        try {
            val first = open()
            val dialog = picker(first)
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick() // Presets -> custom.
            val hex = checkNotNull(dialog.findViewById<EditText>(com.jaredrummler.android.colorpicker.R.id.cpv_hex))
            assertTrue(hex.requestFocus())
            hex.setText("66123456")
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            main.idle()
            assertEquals(0, commits)
            assertNull(saved.typography[ShelfTitleSlot.Book].color)
            first.applyDraft()
            main.idle()
            assertEquals(0x66123456, saved.typography[ShelfTitleSlot.Book].color)
            assertEquals(1, commits)

            val second = open()
            picker(second).cancel()
            main.idle()
            second.restoreTitleColor()
            second.dismissNow() // Main layout cancelled after the reset.
            assertEquals(1, commits)
            assertEquals(0x66123456, saved.typography[ShelfTitleSlot.Book].color)
            val third = open()
            third.restoreTitleColor()
            third.applyDraft()
            main.idle()
            assertEquals(2, commits)
            assertNull(saved.typography[ShelfTitleSlot.Book].color)
        } finally {
            owner.pause().stop().destroy()
        }
    }
}
