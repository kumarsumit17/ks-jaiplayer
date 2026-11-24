package com.ks.jaiplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.browse_allow
import com.ks.jaiplayer.resources.browse_empty
import com.ks.jaiplayer.resources.browse_files_title
import com.ks.jaiplayer.resources.browse_folders_title
import com.ks.jaiplayer.resources.browse_no_access
import com.ks.jaiplayer.resources.common_back
import com.ks.jaiplayer.resources.common_items
import com.ks.jaiplayer.resources.common_loading
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A folder of the device's media library: a series, a season, a USB drive's folder. */
data class MediaFolder(val id: String, val name: String, val location: String, val count: Int)

/**
 * The device's video and audio, read without a system file picker. Google TV often has none,
 * so the TV app browses this instead (Android: MediaStore, which also indexes USB drives).
 */
interface MediaLibrary {
    /** Asks for permission to read media if needed; [onResult] says whether reading is allowed. */
    fun requestAccess(onResult: (Boolean) -> Unit)

    /** Folders that hold video or audio, by name. */
    suspend fun folders(): List<MediaFolder>

    /** The folder's video and audio files in natural name order (episode 2 before episode 10). */
    suspend fun files(folder: MediaFolder): List<MediaRef>
}

/**
 * What the browser is choosing: one file to play, or a series (a folder), played from the chosen
 * episode to the end of the folder.
 */
enum class BrowseMode { File, Folder }

/** Full-screen, remote-friendly browser of [library]: folders, then a folder's files. */
@Composable
fun MediaBrowser(
    library: MediaLibrary,
    mode: BrowseMode,
    onFile: (MediaRef) -> Unit,
    onFolder: (List<MediaRef>) -> Unit,
    onClose: () -> Unit,
) {
    var access by remember { mutableStateOf<Boolean?>(null) }
    var folders by remember { mutableStateOf<List<MediaFolder>?>(null) }
    var open by remember { mutableStateOf<MediaFolder?>(null) }
    var files by remember { mutableStateOf<List<MediaRef>?>(null) }
    var asked by remember { mutableStateOf(0) }

    LaunchedEffect(asked) { library.requestAccess { access = it } }
    LaunchedEffect(access) { if (access == true) folders = library.folders() }
    LaunchedEffect(open) { files = null; open?.let { files = library.files(it) } }

    val back = { if (open != null) open = null else onClose() }
    val first = remember { FocusRequester() }

    Surface(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyUp && (it.key == Key.Back || it.key == Key.Escape)) { back(); true }
            else it.key == Key.Back || it.key == Key.Escape
        },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back, modifier = Modifier.tvFocusRing()) {
                    Icon(JaiIcons.ArrowBack, stringResource(Res.string.common_back))
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    open?.name ?: stringResource(if (mode == BrowseMode.File) Res.string.browse_files_title else Res.string.browse_folders_title),
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(20.dp))
            when {
                access == false -> Message(stringResource(Res.string.browse_no_access)) {
                    Button(onClick = { asked++ }, modifier = Modifier.tvFocusRing(RoundedCornerShape(50)).focusRequester(first)) {
                        Text(stringResource(Res.string.browse_allow))
                    }
                }
                access == null || folders == null || (open != null && files == null) -> Message(stringResource(Res.string.common_loading))
                open == null && folders!!.isEmpty() -> Message(stringResource(Res.string.browse_empty))
                open == null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(folders!!, key = { it.id }) { folder ->
                        BrowseRow(JaiIcons.Folder, folder.name, listOf(pluralStringResource(Res.plurals.common_items, folder.count, folder.count), folder.location).filter { it.isNotEmpty() }.joinToString(" · "),
                            if (folder == folders!!.first()) Modifier.focusRequester(first) else Modifier) {
                            open = folder
                        }
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(files!!, key = { it.uri }) { media ->
                        BrowseRow(JaiIcons.Film, media.title, null, if (media == files!!.first()) Modifier.focusRequester(first) else Modifier) {
                            if (mode == BrowseMode.File) onFile(media) else onFolder(files!!.dropWhile { it != media })
                        }
                    }
                }
            }
        }
    }
    // The remote needs something focused: the first row (or the Allow button) once it is there.
    LaunchedEffect(access, folders, files, open) { runCatching { first.requestFocus() } }
}

@Composable
private fun Message(text: String, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 560.dp))
            action()
        }
    }
}

@Composable
private fun BrowseRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().tvFocusRing(RoundedCornerShape(10.dp)),
    ) {
        Row(Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (detail != null) {
                    Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
