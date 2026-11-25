package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.help.AppFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only font library: selecting a bookshelf font must not change the system face or font folder. */
@Composable
internal fun BookshelfTitleFontLibrary(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val fonts by produceState<List<AppFont.FontItem>?>(null, context) {
        value = withContext(Dispatchers.IO) { AppFont.list(context) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("字体库") },
        text = {
            val entries = fonts
            when {
                entries == null -> Text("正在读取字体库…")
                entries.isEmpty() -> Text("字体库为空。可返回书名设置，选择「导入 TTF/OTF」。")
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(entries, key = { it.ref }) { font ->
                        Text(
                            text = if (font.ref == current) "✓ ${font.displayName}" else font.displayName,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(font.ref) }.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(android.R.string.cancel)) } }
    )
}
