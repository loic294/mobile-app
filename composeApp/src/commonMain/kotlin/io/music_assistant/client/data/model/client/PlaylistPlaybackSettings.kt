package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPlaybackSettings(
    @SerialName("spoken_word") val spokenWord: Boolean,
    @SerialName("remove_finished") val removeFinished: Boolean,
    @SerialName("show_remaining_time") val showRemainingTime: Boolean,
) {
    fun eligibleFor(playlist: Playlist) = copy(
        spokenWord = spokenWord && !playlist.isDynamic,
        removeFinished = removeFinished && spokenWord && playlist.isEditable && !playlist.isDynamic,
    )
}

@Serializable
data class PlaylistAccess(
    val owner: String? = null,
    val collaborative: Boolean = false,
)

fun Playlist.canManagePlaybackSettings(userId: String?, canWrite: Boolean, managesLibrary: Boolean): Boolean =
    provider == "library" && !isDynamic && canWrite &&
        (access == null || managesLibrary || (userId != null && access.owner == userId))

/** Progress is milliseconds, while media duration is seconds. Tracks deliberately have no override. */
fun PlayableItem.remainingSeconds(): Double? {
    val (finished, position) = when (this) {
        is PodcastEpisode -> fullyPlayed to resumePositionMs
        is Audiobook -> fullyPlayed to resumePositionMs
        else -> return null
    }
    val total = duration?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: return null
    return if (finished == true) 0.0 else (total - (position ?: 0).coerceAtLeast(0) / 1000.0).coerceIn(0.0, total)
}

data class PlaylistPlayContext(val media: AppMediaItem, val startItem: String?, val sort: SortOption?)

fun playlistPlayContext(
    item: AppMediaItem,
    parent: AppMediaItem?,
    fromHere: Boolean,
    sort: SortOption?,
): PlaylistPlayContext {
    val inPlaylist = parent is Playlist && (item is PodcastEpisode || item is Audiobook)
    val useParent = parent != null && (fromHere || inPlaylist) && item !== parent
    val media = parent?.takeIf { useParent } ?: item
    return PlaylistPlayContext(
        media,
        if (useParent) {
            if (inPlaylist) item.mediaUri else item.itemId
        } else {
            null
        },
        sort.takeIf { media is Playlist },
    )
}
