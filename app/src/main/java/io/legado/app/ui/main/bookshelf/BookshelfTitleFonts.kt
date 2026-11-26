package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import io.legado.app.help.AppFont
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** Local imports are private to the bookshelf, never replacements for reader/library fonts. */
internal object BookshelfTitleFonts {
    private const val DIRECTORY = "bookshelf-cover-fonts"
    private const val MAX_BYTES = 64L * 1024 * 1024

    fun label(ref: String): String = when {
        ref.isEmpty() -> "默认字体"
        ref.startsWith("@font:") -> ref.removePrefix("@font:")
        else -> File(ref).name.substringAfter('_')
    }

    // Called on IO, only after the user confirms the bookshelf dialog. Publish only a
    // complete, valid file. An unreadable URI or failed copy leaves the old ref intact.
    fun importDocument(context: Context, uri: Uri): File {
        val name = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
        require(name.endsWith(".ttf", true) || name.endsWith(".otf", true)) { "请选择 TTF 或 OTF 字体文件" }
        val dir = File(context.filesDir, DIRECTORY)
        check(dir.isDirectory || dir.mkdirs()) { "无法创建书架字体目录" }
        val temp = File.createTempFile("import-", ".tmp", dir)
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("无法打开字体")
            stream.use { input ->
                temp.outputStream().buffered().use { out ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buf)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "字体超过 64 MiB" }
                        out.write(buf, 0, count)
                    }
                }
            }
            val signature = DataInputStream(temp.inputStream()).use { it.readInt() }
            require(signature == 0x00010000 || signature == 0x4F54544F || signature == 0x74727565) { "不是有效的 TTF/OTF 字体" }
            require(loadFile(temp) != null) { "无法读取字体" }
            // Provider-supplied names are labels, not filesystem paths.
            val safeName = name.substringAfterLast('/').map { if (it.isLetterOrDigit() || it in "._- ") it else '_' }.joinToString("").takeLast(60)
            val file = File(dir, "${UUID.randomUUID()}_$safeName")
            check(temp.renameTo(file)) { "无法保存字体" }
            return file
        } finally {
            temp.delete()
        }
    }

    fun removeOldImport(context: Context, ref: String) {
        val file = File(ref)
        if (file.parentFile == File(context.filesDir, DIRECTORY)) file.delete()
    }

    private fun loadFile(file: File): Typeface? = if (!file.isFile) null else runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Typeface.Builder(file).build()
        else Typeface.createFromFile(file)
    }.getOrNull()

    // Books, folders and labels can have different faces on the same screen.
    private val importedFaces = android.util.LruCache<String, Typeface>(12)

    @Synchronized
    fun cachedImport(ref: String): Typeface? = importedFaces.get(ref)

    @Synchronized
    fun resolve(context: Context, ref: String): Typeface? {
        if (ref.startsWith("@font:")) return runCatching { AppFont.tryTypeface(ref) }.getOrNull()
        val file = File(ref)
        if (file.parentFile != File(context.filesDir, DIRECTORY) || !file.isFile) return null
        return importedFaces.get(ref) ?: loadFile(file)?.also { importedFaces.put(ref, it) }
    }
}
