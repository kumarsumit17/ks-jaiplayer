package com.ks.jaiplayer.plugins.recent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.plugin.JaiPlugin
import com.ks.jaiplayer.plugin.MenuEntry
import com.ks.jaiplayer.plugin.MenuGroup
import com.ks.jaiplayer.plugin.PluginContext
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.common_remove
import com.ks.jaiplayer.resources.home_clear_recent
import com.ks.jaiplayer.resources.home_recent
import com.ks.jaiplayer.resources.home_recent_empty
import com.ks.jaiplayer.resources.home_recent_missing
import com.ks.jaiplayer.resources.menu_open_recent
import com.ks.jaiplayer.ui.JaiIcons
import com.ks.jaiplayer.ui.LocalMetrics
import com.ks.jaiplayer.ui.PlatformHost
import com.ks.jaiplayer.ui.tvFocusRing
import org.jetbrains.compose.resources.stringResource

/** Built-in plugin: the last 20 files played, on the home screen and in File → Open recent. */
class RecentFilesPlugin : JaiPlugin {
    override val id = "recent-files"

    private var recent by mutableStateOf(emptyList<MediaRef>())

    override fun install(context: PluginContext) {
        val store = RecentFiles(context.settings)
        recent = store.load()
        context.onMediaOpened { recent = store.add(it) }
        context.addHomeSection {
            RecentSection(
                recent, context.host,
                open = context.open,
                remove = { recent = store.remove(it.uri) },
                clear = { recent = store.clear() },
            )
        }
        context.addMenuEntry(
            MenuGroup.File,
            MenuEntry(
                label = { stringResource(Res.string.menu_open_recent) },
                enabled = { recent.isNotEmpty() },
                children = {
                    recent.map { media -> MenuEntry(label = { media.title }, onClick = { context.open(media) }) } +
                        MenuEntry(label = { stringResource(Res.string.home_clear_recent) }, onClick = { recent = store.clear() })
                },
            ),
        )
    }
}

@Composable
private fun RecentSection(
    recent: List<MediaRef>,
    host: PlatformHost,
    open: (MediaRef) -> Unit,
    remove: (MediaRef) -> Unit,
    clear: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.home_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (recent.isNotEmpty()) TextButton(onClick = clear) { Text(stringResource(Res.string.home_clear_recent)) }
        }
        Spacer(Modifier.height(8.dp))
        if (recent.isEmpty()) {
            Text(
                stringResource(Res.string.home_recent_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recent, key = { it.uri }) { media -> RecentRow(media, host, open, remove) }
            }
        }
    }
}

@Composable
private fun RecentRow(media: MediaRef, host: PlatformHost, open: (MediaRef) -> Unit, remove: (MediaRef) -> Unit) {
    val m = LocalMetrics.current
    val exists = remember(media) { host.exists(media) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().tvFocusRing(RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.clickable(enabled = exists) { open(media) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(JaiIcons.Film, null, Modifier.size(m.icon).alpha(if (exists) 1f else 0.4f))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f).alpha(if (exists) 1f else 0.5f)) {
                Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    if (exists) host.location(media) else stringResource(Res.string.home_recent_missing),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { remove(media) }, modifier = Modifier.tvFocusRing()) {
                Icon(JaiIcons.Close, stringResource(Res.string.common_remove), Modifier.size(m.icon * 0.8f))
            }
        }
    }
}
