package com.ks.jaiplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.common_close
import com.ks.jaiplayer.resources.common_language
import com.ks.jaiplayer.resources.common_language_english
import com.ks.jaiplayer.resources.common_language_hindi
import com.ks.jaiplayer.resources.common_language_system
import com.ks.jaiplayer.resources.common_settings
import com.ks.jaiplayer.settings.LanguageChoice
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ks.jaiplayer.resources.settings_this_device
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsDialog(app: JaiPlayerState) {
    AlertDialog(
        onDismissRequest = { app.showSettings = false },
        title = { Text(stringResource(Res.string.common_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(Res.string.common_language),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LanguageChoice.entries.forEach { choice ->
                    val label = when (choice) {
                        LanguageChoice.System -> Res.string.common_language_system
                        LanguageChoice.English -> Res.string.common_language_english
                        LanguageChoice.Hindi -> Res.string.common_language_hindi
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(app.languageChoice == choice, role = Role.RadioButton) { app.chooseLanguage(choice) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = app.languageChoice == choice, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 12.dp))
                    }
                }
                app.host.deviceSettings.takeIf { it.isNotEmpty() }?.let { settings ->
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(
                        stringResource(Res.string.settings_this_device),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    settings.forEach { DeviceSettingRow(it) }
                }
                app.updates?.let {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    UpdateSection(it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.showSettings = false }) { Text(stringResource(Res.string.common_close)) }
        },
    )
}

@Composable
private fun DeviceSettingRow(setting: DeviceSetting) {
    var on by remember(setting) { mutableStateOf(setting.isOn()) }
    Row(
        Modifier.fillMaxWidth()
            .toggleable(on, role = Role.Switch) { on = it; setting.set(it) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(setting.label))
            Text(
                stringResource(setting.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

