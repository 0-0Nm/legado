package io.legado.app.ui.main.bookshelf

import android.content.SharedPreferences
import androidx.core.content.edit
import io.legado.app.constant.PreferKey

/** Each destination owns its raw choices. Following never writes through to either choice. */
enum class ShelfTitleSlot(val label: String, val key: String) {
    Book("书籍横排", "book"), BookVertical("书籍竖排", "bookVertical"),
    Folder("文件夹横排", "folder"), FolderVertical("文件夹竖排", "folderVertical"),
    GroupTag("分组标签", "groupTag");

    val horizontal: ShelfTitleSlot? get() = when (this) {
        BookVertical -> Book
        FolderVertical -> Folder
        else -> null
    }
}

data class ShelfTitleChoice(
    val font: String = "", val color: Int? = null,
    val followFont: Boolean = true, val followColor: Boolean = true
)

data class ShelfTypography(private val choices: Map<ShelfTitleSlot, ShelfTitleChoice> = emptyMap()) {
    operator fun get(slot: ShelfTitleSlot) = choices[slot] ?: ShelfTitleChoice()
    fun with(slot: ShelfTitleSlot, choice: ShelfTitleChoice) =
        ShelfTypography(if (choice == ShelfTitleChoice()) choices - slot else choices + (slot to choice))

    fun effective(slot: ShelfTitleSlot): ShelfTitleChoice {
        val own = this[slot]
        val horizontal = this[slot.horizontal ?: return own]
        return own.copy(font = if (own.followFont) horizontal.font else own.font,
            color = if (own.followColor) horizontal.color else own.color)
    }

    internal fun fontRefs() = choices.values.map { it.font }.filter { it.isNotEmpty() }.toSet()
}

internal object ShelfTitleStore {
    private fun ShelfTitleSlot.key(field: String) = when {
        this == ShelfTitleSlot.Book && field == "font" -> PreferKey.bookshelfCoverTitleFont
        this == ShelfTitleSlot.Book && field == "color" -> PreferKey.bookshelfCoverTitleColor
        else -> "bookshelf.title.$key.$field"
    }

    fun read(prefs: SharedPreferences): ShelfTypography {
        val all = prefs.all
        return ShelfTitleSlot.entries.fold(ShelfTypography()) { result, slot ->
            result.with(slot, ShelfTitleChoice(all[slot.key("font")] as? String ?: "",
                all[slot.key("color")]?.toString()?.toIntOrNull(),
                all[slot.key("followFont")] as? Boolean ?: true,
                all[slot.key("followColor")] as? Boolean ?: true))
        }
    }

    fun write(prefs: SharedPreferences, typography: ShelfTypography) = prefs.edit {
        ShelfTitleSlot.entries.forEach { slot ->
            val choice = typography[slot]
            putString(slot.key("font"), choice.font.takeIf { it.isNotEmpty() })
            putString(slot.key("color"), choice.color?.toString())
            if (slot.horizontal != null) {
                putBoolean(slot.key("followFont"), choice.followFont)
                putBoolean(slot.key("followColor"), choice.followColor)
            }
        }
    }
}

// Neutral, high-contrast ink selected from the actual page background, never the theme title.
internal fun shelfDefaultInk(background: Int): Int =
    if (androidx.core.graphics.ColorUtils.calculateLuminance(background) > 0.179) android.graphics.Color.BLACK
    else android.graphics.Color.WHITE
