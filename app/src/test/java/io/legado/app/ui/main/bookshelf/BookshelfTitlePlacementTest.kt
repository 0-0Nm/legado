@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.BookCover
import io.legado.app.ui.main.bookshelf.compose.*
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookshelfTitlePlacementTest {
    @get:Rule val ui = createEmptyComposeRule()

    // Read the upper half only: horizontal overlay is at the bottom and caption is below it.
    // This is the native Canvas fill, not a test-only property attached to the cover.
    private lateinit var content: ComposeView

    private fun glyphMask(ink: Int): Set<Int> {
        val bounds = ui.onNodeWithTag("tile", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val w = bounds.width.toInt() / 2
        return ui.runOnIdle {
            // Use the attached view's real draw traversal, backed by Robolectric native Skia.
            // Window PixelCopy's hardware frame-commit callback isn't delivered in this host.
            val bitmap = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            content.draw(canvas)
            val pixels = IntArray(w * w)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, w)
            bitmap.recycle()
            pixels.indices.filterTo(mutableSetOf()) { pixels[it] == ink }
        }
    }

    @Test fun actualGridTextAndCoverCanvasAcrossModesAndFollowSwitches() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        val prefs = app.defaultSharedPreferences
        val file = File(app.filesDir, "bookshelf-cover-fonts/test_number.ttf").apply {
            parentFile!!.mkdirs()
            app.assets.open("font/number.ttf").use { input -> outputStream().use { input.copyTo(it) } }
        }
        assertNotNull(BookshelfTitleFonts.resolve(app, file.path))
        val hInk = 0xff14ba59.toInt()
        val vInk = 0xffb112d9.toInt()
        val folderInk = 0xff1535c7.toInt()
        val base = ShelfTypography().with(ShelfTitleSlot.Book, ShelfTitleChoice(file.path, hInk))
            .with(ShelfTitleSlot.Folder, ShelfTitleChoice(color = folderInk))
        val book = BookshelfBookItemUi(BookShelfDisplay("url", "local", "", "888", "", null, null,
            null, null, null, 0, 0, null, 0, 0, 0, null, 0, 0, false, 0, null), false, 0, false, emptyList(), null)
        var item: BookshelfItemUi = book
        val version = mutableIntStateOf(0)
        AppConfig.useDefaultCover = true // no network or image loader in this test
        val owner = Robolectric.buildActivity(ComponentActivity::class.java)
        owner.get().setTheme(R.style.AppTheme_Light)
        owner.setup()
        try {
            content = ComposeView(owner.get())
            owner.get().setContentView(content)
            content.setContent {
                key(version.intValue) { BookshelfGridItem(item, Modifier.width(160.dp).testTag("tile"), onClick = {}, onLongClick = {}) }
            }
            val masks = mutableMapOf<Boolean, Set<Int>>()
            for (mode in 0..2) for (vertical in listOf(false, true)) {
                for (font in listOf(false, true)) for (color in listOf(false, true)) {
                    ui.runOnIdle {
                        prefs.edit().putBoolean(PreferKey.coverShowName, vertical)
                            .putBoolean(PreferKey.coverShowNameN, vertical).apply()
                        AppConfig.showBookname = mode
                        AppConfig.titleFontColor = if (color) "#0880EF" else "#AD6201"
                        AppConfig.titleFontPath = if (font) "" else file.path
                        ShelfTitleStore.write(prefs, base.with(ShelfTitleSlot.BookVertical,
                            ShelfTitleChoice("missing.ttf", vInk, font, color)))
                        BookCover.upDefaultCover()
                        refreshBookshelfCoverTitle()
                        version.intValue++
                    }
                    val caption = ui.onNodeWithText("888", useUnmergedTree = true)
                    if (mode == 1) caption.assertDoesNotExist() else {
                        caption.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            assertTrue(get(layouts))
                            assertEquals(hInk, layouts.single().layoutInput.style.color.toArgb())
                            val input = layouts.single().layoutInput
                            val resolved = input.fontFamilyResolver.resolve(input.style.fontFamily).value as Typeface
                            fun advance(face: Typeface?) = android.graphics.Paint().apply {
                                typeface = face; textSize = 32f
                            }.measureText("888")
                            assertEquals(advance(BookshelfTitleFonts.resolve(app, file.path)), advance(resolved), .01f)
                        }
                    }
                    val ink = if (color) hInk else vInk
                    val other = if (color) vInk else hInk
                    if (vertical) {
                        val observed = glyphMask(ink)
                        assertTrue("No vertical fill: mode=$mode followFont=$font followColor=$color", observed.isNotEmpty())
                        val previous = masks.putIfAbsent(font, observed)
                        if (previous != null) assertEquals(previous, observed)
                        if (masks.size == 2) assertNotEquals(masks[true], masks[false])
                    } else assertTrue(glyphMask(ink).isEmpty())
                    assertTrue(glyphMask(other).isEmpty())
                }
            }
            ui.runOnIdle { item = BookshelfFolderItemUi(BookGroup(groupName = "888")); version.intValue++ }
            assertTrue(glyphMask(folderInk).isNotEmpty())
            ui.onNodeWithText("888", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                val result = mutableListOf<TextLayoutResult>(); assertTrue(get(result))
                assertEquals(folderInk, result.single().layoutInput.style.color.toArgb())
            }
            ui.runOnIdle {
                item = book.copy(display = book.display.copy(coverUrl = "https://example.invalid/real-cover"))
                version.intValue++
            }
            assertTrue(glyphMask(hInk).isEmpty()) // ineligible: horizontal still exists
            ui.onNodeWithText("888", useUnmergedTree = true).assertExists()
            for (theme in listOf("#BE1133", "#18ABCC")) {
                ui.runOnIdle {
                    item = book; AppConfig.titleFontColor = theme; AppConfig.titleFontPath = file.path
                    ShelfTitleStore.write(prefs, ShelfTypography().with(ShelfTitleSlot.Book, ShelfTitleChoice(font = "missing.ttf")))
                    refreshBookshelfCoverTitle(); version.intValue++
                }
                ui.onNodeWithText("888", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                    val values = mutableListOf<TextLayoutResult>(); assertTrue(get(values))
                    val input = values.single().layoutInput
                    assertEquals(shelfDefaultInk(owner.get().backgroundColor), input.style.color.toArgb())
                    assertEquals(Typeface.DEFAULT, input.fontFamilyResolver.resolve(input.style.fontFamily).value)
                }
            }
        } finally { owner.pause().stop().destroy() }
    }
}
