package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.PlaylistPlaybackSettings
import io.music_assistant.client.data.model.client.items.Playlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistPlaybackSettingsState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val value: PlaylistPlaybackSettings? = null,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = value != null && !loading && !saving && error == null
}

class PlaylistPlaybackSettingsController(
    private val client: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaylistPlaybackSettingsState())
    val state = _state.asStateFlow()
    private var playlist: Playlist? = null
    private var loadJob: Job? = null

    fun load(item: Playlist) {
        loadJob?.cancel()
        playlist = item
        _state.update { it.copy(loading = true, value = null, error = null, saved = false) }
        loadJob = scope.launch {
            try {
                require(item.provider == "library") { "Add this playlist to the library before changing playback settings." }
                val answer = client.sendRequest(Request.Playlist.playbackSettings(item.itemId)).getOrThrow()
                val value = requireNotNull(answer.resultAs<PlaylistPlaybackSettings>()) {
                    "Invalid playlist playback settings response."
                }
                _state.update { it.copy(loading = false, value = value) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Unable to load settings.", visible = true) }
            }
        }
    }

    fun open() {
        _state.update { it.copy(visible = true, saved = false) }
        playlist?.let(::load)
    }

    fun close() {
        if (!_state.value.saving) _state.update { it.copy(visible = false) }
    }

    fun save(value: PlaylistPlaybackSettings, canManage: Boolean) {
        val item = playlist ?: return
        if (!canManage || !_state.value.canSave) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        scope.launch {
            try {
                val answer = client.sendRequest(
                    Request.Playlist.setPlaybackSettings(item.itemId, value.eligibleFor(item)),
                ).getOrThrow()
                val saved = requireNotNull(answer.resultAs<PlaylistPlaybackSettings>()) {
                    "Invalid playlist playback settings response."
                }
                _state.update { it.copy(saving = false, value = saved, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Unable to save settings.") }
            }
        }
    }
}
