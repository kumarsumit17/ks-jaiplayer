package com.ks.jaiplayer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.focusGroup

// Building blocks of the control bar, shared by the core and the plugins, so every control
// looks and behaves the same (focus on TV, no focus on desktop).

/** Tells the player screen a menu is open, so the controls stay shown meanwhile. */
val LocalMenuOpen = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val m = LocalMetrics.current
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(m.button + 8.dp).tvFocusRing()) {
        Icon(icon, label, Modifier.size(m.icon), tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
    }
}

/** A control that opens a menu. [anchor] draws the button; [items] gets a function that closes the menu. */
@Composable
fun ControlMenu(
    label: String,
    anchor: @Composable (open: () -> Unit) -> Unit,
    items: @Composable (close: () -> Unit) -> Unit,
) {
    val onMenu = LocalMenuOpen.current
    var open by remember { mutableStateOf(false) }
    fun set(value: Boolean) { open = value; onMenu(value) }
    Box {
        anchor { set(true) }
        if (LocalFormFactor.current == FormFactor.Tv) {
            // TV: a panel along the right edge, readable from the sofa, that keeps the remote's
            // focus inside it until Back closes it.
            if (open) SidePanel(label, onClose = { set(false) }) { items { set(false) } }
        } else {
            DropdownMenu(expanded = open, onDismissRequest = { set(false) }) {
                Text(
                    label, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                items { set(false) }
            }
        }
    }
}

@Composable
private fun SidePanel(label: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    Popup(
        alignment = Alignment.CenterEnd,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            Modifier.fillMaxHeight().width(420.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)
                    .focusRequester(focus).focusGroup(),
            ) {
                Text(
                    label, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                content()
            }
        }
    }
    // Into the panel's first choice, so the remote starts there.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun IconControlMenu(icon: ImageVector, label: String, items: @Composable (close: () -> Unit) -> Unit) =
    ControlMenu(label, anchor = { open -> ControlButton(icon, label, open) }, items = items)

@Composable
fun TextControlMenu(text: String, label: String, items: @Composable (close: () -> Unit) -> Unit) =
    ControlMenu(label, anchor = { open ->
        TextButton(onClick = open, modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp))) {
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }, items = items)

/** One choice in a control menu, ticked when [selected]. */
@Composable
fun MenuChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = { Text(if (selected) "✓" else "", modifier = Modifier.width(16.dp)) },
        onClick = onClick,
    )
}

/**
 * On TV, a clear ring around the focused control (the suite's TV convention: D-pad first, focus
 * always visible). No effect elsewhere.
 */
@Composable
fun Modifier.tvFocusRing(shape: Shape = CircleShape): Modifier {
    // TV always; desktop too, where Tab moves through the player's buttons. Not on the phone.
    if (LocalFormFactor.current == FormFactor.Phone) return this
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
}

/** On desktop, the sliders never take keyboard focus: the arrow keys always seek and change the volume. */
@Composable
fun Modifier.desktopNoFocus(): Modifier =
    if (LocalFormFactor.current == FormFactor.Desktop) focusProperties { canFocus = false } else this
