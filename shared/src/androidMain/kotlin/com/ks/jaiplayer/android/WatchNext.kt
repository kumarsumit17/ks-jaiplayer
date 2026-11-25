package com.ks.jaiplayer.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.ks.jaiplayer.plugins.resume.ResumeEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Google TV only: keeps the home screen's Continue watching row (the Watch Next channel) in step
 * with the files left unfinished ([ResumeEntry]). Each program opens the file in the app through
 * its VIEW intent, with the position, the length and a frame from where it was left.
 *
 * A program the viewer removed from the row is not put back until the file is watched again.
 * Work runs off the main thread; a newer list replaces one still being published.
 */
class WatchNext(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun publish(entries: List<ResumeEntry>) {
        job?.cancel()
        job = scope.launch {
            runCatching { sync(entries.take(MAX_PROGRAMS)) }
                .onSuccess { android.util.Log.i(TAG, "Continue watching: ${entries.size.coerceAtMost(MAX_PROGRAMS)} files") }
                .onFailure { android.util.Log.w(TAG, "Continue watching could not be updated", it) }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun sync(entries: List<ResumeEntry>) {
        val helper = PreviewChannelHelper(context)
        val existing = existingPrograms()
        val wanted = entries.map { it.uri }.toSet()
        for ((uri, program) in existing) {
            if (uri !in wanted) context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(program.id), null, null)
        }
        for (entry in entries) {
            val old = existing[entry.uri]
            // Removed by the viewer: only a newer viewing brings it back.
            if (old != null && !old.isBrowsable && entry.updatedAtMs <= old.lastEngagementTimeUtcMillis) continue
            val program = program(entry, old)
            if (old == null) helper.publishWatchNextProgram(program) else helper.updateWatchNextProgram(program, old.id)
        }
        Thumbnails.keepOnly(context, wanted)
    }

    @SuppressLint("RestrictedApi")
    private fun existingPrograms(): Map<String, WatchNextProgram> {
        val out = mutableMapOf<String, WatchNextProgram>()
        context.contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                p.internalProviderId?.let { out[it] = p }
            }
        }
        return out
    }

    @SuppressLint("RestrictedApi")
    private fun program(e: ResumeEntry, old: WatchNextProgram?): WatchNextProgram {
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(e.uri))
            .setClassName(context, MainActivity::class.java.name)
            .putExtra(Intent.EXTRA_TITLE, e.title)
        val builder = if (old != null) WatchNextProgram.Builder(old) else WatchNextProgram.Builder()
        builder.setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(e.title)
            .setLastEngagementTimeUtcMillis(e.updatedAtMs)
            .setLastPlaybackPositionMillis(e.positionMs.toInt())
            .setDurationMillis(e.durationMs.toInt())
            .setInternalProviderId(e.uri)
            .setIntent(open)
            .setBrowsable(true)
        Thumbnails.frame(context, e)?.let { builder.setPosterArtUri(it).setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9) }
        return builder.build()
    }

    private companion object {
        const val MAX_PROGRAMS = 10
        const val TAG = "JaiPlayer"
    }
}

/** Frames taken where each file was left, stored in the app's cache and served by [ThumbnailProvider]. */
internal object Thumbnails {
    fun dir(context: Context) = File(context.cacheDir, "watch-next").apply { mkdirs() }

    fun nameOf(uri: String) = "f" + uri.hashCode().toUInt().toString(16) + ".jpg"

    /** A content URI the TV home screen can read, or null if no frame could be taken. */
    fun frame(context: Context, e: ResumeEntry): Uri? {
        val file = File(dir(context), nameOf(e.uri))
        if (!file.isFile || file.length() == 0L) {
            val bitmap = runCatching {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(context, Uri.parse(e.uri))
                    r.getScaledFrameAtTime(e.positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
                }
            }.getOrNull() ?: return null
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        return Uri.Builder().scheme("content").authority("${context.packageName}.thumbnails").appendPath(file.name).build()
    }

    fun keepOnly(context: Context, uris: Set<String>) {
        val keep = uris.map(::nameOf).toSet()
        dir(context).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }
}

/** Serves the Continue watching thumbnails, read only, and nothing else. */
class ThumbnailProvider : android.content.ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("read only")
        val name = uri.lastPathSegment?.takeIf { NAME.matches(it) } ?: throw java.io.FileNotFoundException()
        val file = File(Thumbnails.dir(context!!), name).takeIf { it.isFile } ?: throw java.io.FileNotFoundException()
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun insert(uri: Uri, values: android.content.ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        val NAME = Regex("f[0-9a-f]{1,8}\\.jpg")
    }
}
