package com.ks.jaiplayer.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.naturalNameOrder

/** Files through the Storage Access Framework: names, folders (series) and lasting access. */
internal object Documents {
    private val mediaMime = Regex("^(video|audio)/.*")

    /** Keeps read access after a restart, so recent files and series still open. */
    fun keepAccess(context: Context, uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun mediaRef(context: Context, uri: Uri, title: String? = null): MediaRef =
        MediaRef(uri.toString(), title?.takeIf { it.isNotBlank() } ?: displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString())

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Every video and audio file in the chosen folder and its subfolders, in episode order. */
    fun mediaInTree(context: Context, tree: Uri, maxDepth: Int = 4): List<MediaRef> {
        val found = mutableListOf<Pair<String, MediaRef>>()
        fun walk(documentId: String, path: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < maxDepth) walk(id, "$path$name/", depth + 1)
                    } else if (mediaMime.matches(mime)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        found += "$path$name" to MediaRef(uri.toString(), name)
                    }
                }
            }
        }
        runCatching { walk(DocumentsContract.getTreeDocumentId(tree), "", 0) }
        return found.sortedWith(compareBy(naturalNameOrder) { it.first }).map { it.second }
    }

    fun exists(context: Context, media: MediaRef): Boolean = runCatching {
        val uri = Uri.parse(media.uri)
        if (uri.scheme == "file") return java.io.File(uri.path.orEmpty()).isFile
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    /** Where the file is, as far as a content URI tells: the folder part of its document id. */
    fun location(media: MediaRef): String {
        val uri = Uri.parse(media.uri)
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.path?.substringBeforeLast('/').orEmpty()
        return id.substringAfter(':', id).substringBeforeLast('/', "")
    }
}
