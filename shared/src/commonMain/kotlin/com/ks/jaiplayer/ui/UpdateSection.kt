package com.ks.jaiplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ks.jaiplayer.resources.Res
import com.ks.jaiplayer.resources.app_name
import com.ks.jaiplayer.resources.common_app_update_allow_install
import com.ks.jaiplayer.resources.common_app_update_allow_install_button
import com.ks.jaiplayer.resources.common_app_update_auto
import com.ks.jaiplayer.resources.common_app_update_auto_hint
import com.ks.jaiplayer.resources.common_app_update_available
import com.ks.jaiplayer.resources.common_app_update_check_button
import com.ks.jaiplayer.resources.common_app_update_check_failed
import com.ks.jaiplayer.resources.common_app_update_checking
import com.ks.jaiplayer.resources.common_app_update_current
import com.ks.jaiplayer.resources.common_app_update_damaged
import com.ks.jaiplayer.resources.common_app_update_dev
import com.ks.jaiplayer.resources.common_app_update_download_failed
import com.ks.jaiplayer.resources.common_app_update_downloading
import com.ks.jaiplayer.resources.common_app_update_install_button
import com.ks.jaiplayer.resources.common_app_update_install_failed
import com.ks.jaiplayer.resources.common_app_update_no_installer
import com.ks.jaiplayer.resources.common_app_update_open_failed
import com.ks.jaiplayer.resources.common_app_update_opened_android
import com.ks.jaiplayer.resources.common_app_update_opened_mac
import com.ks.jaiplayer.resources.common_app_update_opened_windows
import com.ks.jaiplayer.resources.common_app_update_section
import com.ks.jaiplayer.resources.common_app_update_unreadable
import com.ks.jaiplayer.resources.common_app_update_up_to_date
import com.ks.jaiplayer.update.OpenedOn
import com.ks.jaiplayer.update.UpdateFailure
import com.ks.jaiplayer.update.UpdateService
import com.ks.jaiplayer.update.UpdateStatus
import com.ks.jaiplayer.update.formatSize
import org.jetbrains.compose.resources.stringResource

/** The suite's "App updates" section (ks-utility `specs/app-update.md`), with the shared texts. */
@Composable
fun UpdateSection(updates: UpdateService) {
    val status by updates.status.collectAsState()
    val auto by updates.autoCheck.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.common_app_update_section), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            if (updates.isDevBuild) stringResource(Res.string.common_app_update_dev)
            else stringResource(Res.string.common_app_update_current, updates.currentVersion),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.common_app_update_auto), Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = updates::setAutoCheck)
        }
        Text(
            stringResource(Res.string.common_app_update_auto_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = updates::checkNow, enabled = status !is UpdateStatus.Checking && status !is UpdateStatus.Downloading) {
                Text(stringResource(Res.string.common_app_update_check_button))
            }
            Spacer(Modifier.width(12.dp))
            statusText(status)?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
        }
        when (val s = status) {
            is UpdateStatus.Available -> {
                Spacer(Modifier.height(8.dp))
                Button(onClick = updates::install) { Text(stringResource(Res.string.common_app_update_install_button, formatSize(s.sizeBytes))) }
            }
            UpdateStatus.NeedsPermission -> {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.common_app_update_allow_install), style = MaterialTheme.typography.bodySmall)
                Row {
                    OutlinedButton(onClick = updates::openInstallPermissionSettings, modifier = Modifier.padding(top = 6.dp)) {
                        Text(stringResource(Res.string.common_app_update_allow_install_button))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = updates::install, modifier = Modifier.padding(top = 6.dp)) {
                        Text(stringResource(Res.string.common_app_update_check_button))
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun statusText(status: UpdateStatus): String? = when (status) {
    UpdateStatus.Idle -> null
    UpdateStatus.Checking -> stringResource(Res.string.common_app_update_checking)
    is UpdateStatus.UpToDate -> stringResource(Res.string.common_app_update_up_to_date, status.version)
    is UpdateStatus.Available -> stringResource(Res.string.common_app_update_available, status.version)
    is UpdateStatus.NoInstaller -> stringResource(Res.string.common_app_update_no_installer, status.version)
    is UpdateStatus.Downloading -> stringResource(Res.string.common_app_update_downloading, status.version)
    is UpdateStatus.Opened -> when (status.on) {
        OpenedOn.Mac -> stringResource(Res.string.common_app_update_opened_mac, stringResource(Res.string.app_name))
        OpenedOn.Windows -> stringResource(Res.string.common_app_update_opened_windows)
        OpenedOn.Android -> stringResource(Res.string.common_app_update_opened_android)
    }
    UpdateStatus.NeedsPermission -> null
    is UpdateStatus.Failed -> when (status.failure) {
        UpdateFailure.Check -> stringResource(Res.string.common_app_update_check_failed, status.detail)
        UpdateFailure.Unreadable -> stringResource(Res.string.common_app_update_unreadable)
        UpdateFailure.Download -> stringResource(Res.string.common_app_update_download_failed, status.detail)
        UpdateFailure.Damaged -> stringResource(Res.string.common_app_update_damaged)
        UpdateFailure.Install -> stringResource(Res.string.common_app_update_install_failed, status.detail)
        UpdateFailure.Open -> stringResource(Res.string.common_app_update_open_failed, status.detail)
    }
}
