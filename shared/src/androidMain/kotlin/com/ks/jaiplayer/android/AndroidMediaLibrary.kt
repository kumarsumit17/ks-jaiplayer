package com.ks.jaiplayer.android

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.naturalNameOrder
import com.ks.jaiplayer.ui.MediaFolder
import com.ks.jaiplayer.ui.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The TV's video and audio from MediaStore, which indexes internal storage and USB drives, so
 * the Google TV app needs no system file picker. [ask] requests the permissions it is given
 * and reports whether reading is allowed.
 */
class AndroidMediaLibrary(
    private val context: Context,
    private val ask: (Array<String>, (Boolean) -> Unit) -> Unit,
) : MediaLibrary {
    private class Entry(val media: MediaRef, val folder: MediaFolder)

    private var scanned: List<Entry>? = null

    private val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun granted() = permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun requestAccess(onResult: (Boolean) -> Unit) {
        if (granted()) onResult(true) else ask(permissions) { onResult(it || granted()) }
    }

    override suspend fun folders(): List<MediaFolder> =
        scan().map { it.folder }.distinctBy { it.id }.sortedWith(compareBy(naturalNameOrder) { it.name })

    override suspend fun files(folder: MediaFolder): List<MediaRef> =
        scan().filter { it.folder.id == folder.id }.map { it.media }.sortedWith(compareBy(naturalNameOrder) { it.title })

    private suspend fun scan(): List<Entry> = scanned ?: withContext(Dispatchers.IO) {
        val volumes = if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context).toList() else listOf("external")
        val all = volumes.flatMap { volume ->
            val video = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val audio = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(volume) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            query(video, volume) + query(audio, volume)
        }
        // Counts per folder, now that every file is known.
        val counts = all.groupingBy { it.folder.id }.eachCount()
        all.map { Entry(it.media, it.folder.copy(count = counts.getValue(it.folder.id))) }.also { scanned = it }
    }

    @Suppress("DEPRECATION") // DATA is the only folder path before Android 10 (API 29).
    private fun query(collection: Uri, volume: String): List<Entry> {
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, pathColumn)
        val out = mutableListOf<Entry>()
        runCatching {
            context.contentResolver.query(collection, columns, null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val path = c.getColumnIndexOrThrow(pathColumn)
                while (c.moveToNext()) {
                    val title = c.getString(name) ?: continue
                    val raw = c.getString(path).orEmpty()
                    // RELATIVE_PATH is "Movies/Show/Season 1/"; DATA is the whole file path.
                    val dir = (if (Build.VERSION.SDK_INT >= 29) raw else raw.substringBeforeLast('/', "")).trimEnd('/')
                    val folderName = dir.substringAfterLast('/').ifEmpty { volume }
                    val parent = dir.substringBeforeLast('/', "")
                    val where = if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY || volume == "external") parent else listOf(volume, parent).filter { it.isNotEmpty() }.joinToString("/")
                    val uri = ContentUris.withAppendedId(collection, c.getLong(id))
                    out += Entry(MediaRef(uri.toString(), title), MediaFolder("$volume:$dir", folderName, where, 0))
                }
            }
        }
        return out
    }
}
