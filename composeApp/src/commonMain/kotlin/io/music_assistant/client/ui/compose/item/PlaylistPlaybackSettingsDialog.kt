package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlaylistPlaybackSettings
import io.music_assistant.client.data.model.client.items.Playlist

internal val LocalPlaylistPlaybackSettings = staticCompositionLocalOf<PlaylistPlaybackSettings?> { null }
internal val LocalOpenPlaylistPlaybackSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun PlaylistPlaybackSettingsDialog(
    state: PlaylistPlaybackSettingsState,
    playlist: Playlist,
    canManage: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSave: (PlaylistPlaybackSettings) -> Unit,
) {
    var draft by remember(state.value) { mutableStateOf(state.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Podcast/Audiobook settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.loading || state.saving) CircularProgressIndicator()
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, enabled = !state.saving && !state.loading) { Text("Retry") }
                }
                if (playlist.isDynamic) {
                    Text("Dynamic playlists cannot enable playlist resume or remove finished items.")
                } else if (!canManage) {
                    Text("Only the playlist owner or a library manager can change these settings.")
                }
                draft?.let { value ->
                    val editable = canManage && state.canSave && !state.saved
                    SettingToggle(
                        "Resume when playing playlist",
                        "Start at the first item in the selected sort order and resume each episode or audiobook " +
                            "from its saved listening progress.",
                        value.spokenWord,
                        editable,
                    ) { draft = value.copy(spokenWord = it, removeFinished = value.removeFinished && it) }
                    SettingToggle(
                        "Remove finished items",
                        "Remove items from the source playlist only when playback finishes naturally, never on skip or stop.",
                        value.removeFinished,
                        editable && value.spokenWord && playlist.isEditable && !playlist.isDynamic,
                    ) { draft = value.copy(removeFinished = it) }
                    SettingToggle(
                        "Show remaining time",
                        "Show listening time left for episodes and audiobooks. Music tracks keep their normal duration.",
                        value.showRemainingTime,
                        editable,
                    ) { draft = value.copy(showRemainingTime = it) }
                }
                if (state.saved) Text("Playback settings saved.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { draft?.let(onSave) },
                enabled = canManage && state.canSave && !state.saved,
            ) { Text(if (state.saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.saving) { Text("Close") } },
    )
}

@Composable
private fun SettingToggle(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
