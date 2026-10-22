package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlaylistAccess
import io.music_assistant.client.data.model.client.PlaylistPlaybackSettings
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.canManagePlaybackSettings
import io.music_assistant.client.data.model.client.playlistPlayContext
import io.music_assistant.client.data.model.client.remainingSeconds
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.data.play.PlayRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPlaybackSettingsTest {
    private val playlist = AppMediaItemFixtures.playlist().copy(
        itemId = "42",
        provider = "library",
        uri = "library://playlist/42",
        isEditable = true,
    )
    private val episode = AppMediaItemFixtures.episodes(listOf("Episode")).single().copy(
        duration = 120.0,
        resumePositionMs = 30000,
        uri = "podcast://podcast_episode/episode",
    )
    private val settings = PlaylistPlaybackSettings(true, true, true)

    @Test
    fun `requests use library id string and all three independent settings`() {
        assertEquals("42", Request.Playlist.playbackSettings("42").args?.get("item_id")?.jsonPrimitive?.content)
        val request = Request.Playlist.setPlaybackSettings("42", settings)
        assertEquals("music/playlists/set_playback_settings", request.command)
        assertEquals(JsonPrimitive("42"), request.args?.get("item_id"))
        listOf("spoken_word", "remove_finished", "show_remaining_time").forEach {
            assertEquals(JsonPrimitive(true), request.args?.get(it))
        }
    }

    @Test
    fun `remaining duration clamps progress and only applies to spoken word`() {
        assertEquals(90.0, episode.remainingSeconds())
        assertEquals(0.0, episode.copy(resumePositionMs = 999999).remainingSeconds())
        assertEquals(120.0, episode.copy(resumePositionMs = -1000).remainingSeconds())
        assertEquals(120.0, episode.copy(resumePositionMs = null).remainingSeconds())
        assertEquals(0.0, episode.copy(fullyPlayed = true).remainingSeconds())
        assertNull(episode.copy(duration = null).remainingSeconds())
        assertNull(AppMediaItemFixtures.track().remainingSeconds())
        assertEquals(
            30.0,
            AppMediaItemFixtures.audiobook().copy(duration = 60.0, resumePositionMs = 30000).remainingSeconds(),
        )
    }

    @Test
    fun `permissions do not confuse collaborators with owners`() {
        val personal = playlist.copy(access = PlaylistAccess(owner = "owner", collaborative = true))
        assertTrue(personal.canManagePlaybackSettings("owner", canWrite = true, managesLibrary = false))
        assertFalse(personal.canManagePlaybackSettings("collaborator", canWrite = true, managesLibrary = false))
        assertTrue(personal.canManagePlaybackSettings("manager", canWrite = true, managesLibrary = true))
        assertFalse(personal.canManagePlaybackSettings("owner", canWrite = false, managesLibrary = false))
        assertFalse(personal.copy(isDynamic = true).canManagePlaybackSettings("owner", true, true))
        assertFalse(personal.copy(provider = "other").canManagePlaybackSettings("owner", true, true))
    }

    @Test
    fun `removal requires spoken word editable non dynamic playlist`() {
        assertEquals(settings, settings.eligibleFor(playlist))
        assertFalse(settings.copy(spokenWord = false).eligibleFor(playlist).removeFinished)
        assertFalse(settings.eligibleFor(playlist.copy(isEditable = false)).removeFinished)
        val dynamic = settings.eligibleFor(playlist.copy(isDynamic = true))
        assertFalse(dynamic.spokenWord)
        assertFalse(dynamic.removeFinished)
        assertTrue(dynamic.showRemainingTime)
    }

    @Test
    fun `row and play from here preserve playlist identity and displayed direction`() = runTest {
        val sort = SortOption(SortField.ORIGINAL, descending = true)
        val row = playlistPlayContext(episode, playlist, false, sort)
        assertEquals(row, playlistPlayContext(episode, playlist, true, sort))
        assertEquals(playlist, row.media)
        assertEquals(episode.mediaUri, row.startItem)
        assertEquals(sort, row.sort)
        val book = AppMediaItemFixtures.audiobook().copy(uri = "library://audiobook/book")
        assertEquals(playlist, playlistPlayContext(book, playlist, false, sort).media)
        val standalone = playlistPlayContext(episode, null, false, sort)
        assertEquals(episode, standalone.media)
        assertNull(standalone.sort)
        val startPlaylist = playlistPlayContext(playlist, playlist, false, sort)
        assertNull(startPlaylist.startItem)
        val track = AppMediaItemFixtures.track()
        assertEquals(track, playlistPlayContext(track, playlist, false, sort).media)

        val client = Client()
        PlayDispatcher(client, backgroundScope).play(
            PlayRequest(
                "queue",
                listOf(row.media.mediaUri!!),
                QueueOption.REPLACE,
                startItem = row.startItem,
                sort = row.sort,
            ),
        )
        runCurrent()
        val args = client.requests.single().args
        assertEquals(JsonPrimitive(episode.mediaUri), args?.get("start_item"))
        assertEquals(JsonPrimitive("original"), args?.get("sort_field"))
        assertEquals(JsonPrimitive("desc"), args?.get("sort_direction"))
    }

    @Test
    fun `opening cancelling and loading never save defaults`() = runTest {
        val client = Client()
        val gate = CompletableDeferred<Result<Answer>>()
        client.gate = gate
        val controller = PlaylistPlaybackSettingsController(client, backgroundScope)
        controller.load(playlist)
        runCurrent()
        assertTrue(controller.state.value.loading)
        assertFalse(controller.state.value.canSave)
        controller.save(settings, true)
        controller.close()
        gate.complete(Result.success(answer()))
        runCurrent()
        assertEquals(1, client.requests.size)
        assertEquals(settings, controller.state.value.value)
        assertFalse(controller.state.value.visible)
        controller.open()
        runCurrent()
        controller.close()
        assertTrue(client.requests.all { it.command == "music/playlists/playback_settings" })
    }

    @Test
    fun `read failures and malformed responses stay visible and block saving`() = runTest {
        val client = Client()
        val controller = PlaylistPlaybackSettingsController(client, backgroundScope)
        client.result = Result.failure(IllegalStateException("Endpoint unavailable"))
        controller.load(playlist)
        runCurrent()
        assertEquals("Endpoint unavailable", controller.state.value.error)
        assertTrue(controller.state.value.visible)
        assertNull(controller.state.value.value)
        controller.save(settings, true)
        assertEquals(1, client.requests.size)
        client.result = Result.success(Answer(buildJsonObject { put("result", JsonObject(emptyMap())) }))
        controller.open()
        runCurrent()
        assertTrue(controller.state.value.error != null)
        assertFalse(controller.state.value.canSave)
    }

    @Test
    fun `save denial stays visible successful save uses normalized server response`() = runTest {
        val client = Client()
        val controller = PlaylistPlaybackSettingsController(client, backgroundScope)
        controller.load(playlist)
        runCurrent()
        controller.save(settings, false)
        assertEquals(1, client.requests.size)
        client.result = Result.failure(IllegalStateException("Forbidden"))
        controller.save(settings, true)
        runCurrent()
        assertEquals("Forbidden", controller.state.value.error)
        assertFalse(controller.state.value.saved)
        client.result = Result.success(answer())
        controller.open()
        runCurrent()
        controller.save(settings.copy(spokenWord = false), true)
        runCurrent()
        assertEquals(JsonPrimitive(false), client.requests.last().args?.get("remove_finished"))
        assertTrue(controller.state.value.saved)
        assertEquals(settings, controller.state.value.value)
    }

    private class Client : StubServiceClient() {
        val requests = mutableListOf<Request>()
        var result: Result<Answer> = Result.success(answer())
        var gate: CompletableDeferred<Result<Answer>>? = null
        override suspend fun sendRequest(request: Request): Result<Answer> {
            requests += request
            return gate?.await() ?: result
        }
    }

    companion object {
        private fun answer() = Answer(
            buildJsonObject {
                put(
                    "result",
                    buildJsonObject {
                        put("spoken_word", JsonPrimitive(true))
                        put("remove_finished", JsonPrimitive(true))
                        put("show_remaining_time", JsonPrimitive(true))
                    },
                )
            },
        )
    }
}
