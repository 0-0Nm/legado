package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.toTimeAgo

sealed interface BookshelfItemUi {
    val key: String
    val contentType: String
}

data class BookshelfFolderItemUi(
    val group: BookGroup
) : BookshelfItemUi {
    override val key: String = "folder:${group.groupId}"
    override val contentType: String = "folder"
}

data class BookshelfBookItemUi(
    val display: BookShelfDisplay,
    val isUpdating: Boolean,
    val unreadCount: Int,
    val hasNewChapter: Boolean,
    val tags: List<String>,
    val lastUpdateText: String?
) : BookshelfItemUi {
    override val key: String = "book:${display.bookUrl}"
    override val contentType: String = "book"
}

fun buildBookshelfItems(
    groups: List<BookGroup>,
    books: List<BookShelfDisplay>,
    isRootGroup: Boolean,
    groupId: Long,
    isUpdating: (String) -> Boolean
): List<BookshelfItemUi> {
    val configuredTags = AppConfig.bookshelfGroupTags[groupId].orEmpty()
    val hiddenTags = AppConfig.bookshelfHiddenTags[groupId].orEmpty()
    val bookItems = books.map { book ->
        BookshelfBookItemUi(
            display = book,
            isUpdating = !book.isLocal && isUpdating(book.bookUrl),
            unreadCount = book.getUnreadChapterNum(),
            hasNewChapter = book.lastCheckCount > 0,
            tags = book.displayUserTags(configuredTags, hiddenTags)
                .take(4),
            lastUpdateText = if (AppConfig.showLastUpdateTime && !book.isLocal) {
                book.latestChapterTime.toTimeAgo()
            } else {
                null
            }
        )
    }
    if (!isRootGroup) {
        return bookItems
    }
    return groups.map(::BookshelfFolderItemUi) + bookItems
}

private fun BookShelfDisplay.displayUserTags(
    configuredTags: List<String>,
    hiddenTags: Set<String>
): List<String> {
    val userTags = BookTagHelper.parse(customTag)
    val visibleConfiguredTags = configuredTags
        .filterNot { configured -> hiddenTags.any { it.equals(configured, ignoreCase = true) } }
    val candidateTags = visibleConfiguredTags.ifEmpty { userTags }
    return candidateTags.filter { candidate ->
        userTags.any { it.equals(candidate, ignoreCase = true) }
    }.distinctBy { it.lowercase() }
}

fun updateBookshelfItemUpdating(
    items: List<BookshelfItemUi>,
    bookUrl: String,
    isUpdating: (String) -> Boolean
): List<BookshelfItemUi> {
    var changed = false
    val updatedItems = items.map { item ->
        if (item is BookshelfBookItemUi && item.display.bookUrl == bookUrl) {
            val nextUpdating = !item.display.isLocal && isUpdating(bookUrl)
            if (nextUpdating != item.isUpdating) {
                changed = true
                item.copy(isUpdating = nextUpdating)
            } else {
                item
            }
        } else {
            item
        }
    }
    return if (changed) updatedItems else items
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfGridItem(
    item: BookshelfItemUi,
    modifier: Modifier = Modifier,
    fragment: Fragment? = null,
    lifecycle: Lifecycle? = null,
    onClick: (BookshelfItemUi) -> Unit,
    onLongClick: (BookshelfItemUi) -> Unit
) {
    val settings = bookshelfTitleSettings
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.TopEnd
        ) {
            BookshelfCover(
                item = item,
                modifier = Modifier.fillMaxWidth(),
                fragment = fragment,
                lifecycle = lifecycle
            )
            if (item is BookshelfBookItemUi) {
                BookshelfStatusBadge(item)
            }
            if (settings.mode == 2) {
                ShelfCaption(item, Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp), overlay = true)
            }
        }
        if (settings.mode == 0) {
            ShelfCaption(item, Modifier.fillMaxWidth().padding(top = 6.dp), centered = true)
        }
    }
}

/** Both horizontal placements use this exact text consumer. A contrast shadow keeps the
 * overlay legible without changing its ink or replacing a custom color with white. */
@Composable
internal fun ShelfCaption(item: BookshelfItemUi, modifier: Modifier = Modifier,
    centered: Boolean = false, overlay: Boolean = false, list: Boolean = false) {
    val paint = rememberBookshelfTitlePaint(item.titleSlot(false))
    Text(text = item.displayName, modifier = modifier,
        color = Color(paint.color), fontFamily = remember(paint.typeface) { FontFamily(paint.typeface) },
        fontWeight = FontWeight.Normal, fontSize = if (list) 16.sp else 12.sp,
        style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = if (overlay)
            androidx.compose.ui.graphics.Shadow(Color(io.legado.app.ui.main.bookshelf.shelfDefaultInk(paint.color)),
                blurRadius = 3f) else null),
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        maxLines = if (list) 1 else 2, minLines = if (centered) 2 else 1,
        overflow = TextOverflow.Ellipsis)
}

@Composable
private fun BookshelfCover(
    item: BookshelfItemUi,
    modifier: Modifier,
    fragment: Fragment?,
    lifecycle: Lifecycle?
) {
    BookshelfComposeCover(
        item = item,
        modifier = modifier,
        fragment = fragment,
        lifecycle = lifecycle
    )
}

@Composable
private fun BookshelfStatusBadge(item: BookshelfBookItemUi) {
    if (item.isUpdating) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(5.dp)
                .size(20.dp),
            strokeWidth = 2.dp,
            color = Color(LocalContext.current.accentColor)
        )
        return
    }
    if (!AppConfig.showUnread || item.unreadCount <= 0) return
    val badgeColor = if (item.hasNewChapter) {
        Color(LocalContext.current.accentColor)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }
    Text(
        text = item.unreadCount.coerceAtMost(99999).toString(),
        modifier = Modifier
            .padding(5.dp)
            .clip(CircleShape)
            .background(badgeColor)
            .widthIn(min = 20.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

private val BookshelfItemUi.displayName: String
    get() = when (this) {
        is BookshelfBookItemUi -> display.name
        is BookshelfFolderItemUi -> group.groupName
    }
