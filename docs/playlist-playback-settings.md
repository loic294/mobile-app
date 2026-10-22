# Podcast and audiobook playlists (Android test build)

Requires a Music Assistant server with `music/playlists/playback_settings` and
`music/playlists/set_playback_settings`. An older server's missing endpoint is
shown as an error, never treated as default settings.

For the official **Music Assistant DEV SERVER** app configured with both fork
branches, see the
[server test deployment guide](https://github.com/loic294/server/blob/playlist-playback-test/docs/test-deployment.md).
Connect this client to `http://HOME_ASSISTANT_IP:8195` (on this network,
`http://192.168.68.76:8195`), not the normal server at port 8095.

Open a library playlist's three-dot menu → **Podcast/Audiobook settings**.

- **Resume when playing playlist** starts at the first item in the selected
  ordering and resumes episodes/books at their saved listening position.
- **Remove finished items** requires resume, an editable playlist, and a
  non-dynamic playlist. The server removes items only at natural playback end,
  not when skipping or stopping.
- **Show remaining time** adds e.g. `42:15 left` to episode/book list rows.
  Unstarted items show the full duration; finished or overshot items show
  `00:00 left`. Tracks are unchanged. Current-user progress events refresh rows,
  including rewinds to zero.

Settings default to false on the server for existing playlists. Opening, loading,
and closing the dialog never save. Save is disabled while reading or saving;
errors remain visible with Retry, and a successful save shows confirmation.
Personal playlists require their owner or a library manager with library-write
permission; collaborators cannot manage these settings. Dynamic playlists cannot
opt in. The server is the final authority.

Playlist sorting offers Original, Name and Duration with ascending/descending
direction. Playing an episode/book from a row, its playback menu, or **Play
playlist from here** keeps the playlist origin and selected ordering. Playing
outside a playlist stays standalone. Filtering is only a visual search; playback
continues through the playlist in the selected order.

## Install the test APK

The fork's **Playlist test (Android)** workflow runs on pushes to
`playlist-playback-test` and by **Run workflow**. It uses JDK 21, runs Kotlin
tests, Android unit tests, Android lint and detekt, and builds a debug-signed APK.
No Play Store or release-signing secrets are used.

1. Open the successful workflow run's **Artifacts**.
2. Download `music-assistant-playlist-test-apk` and extract its ZIP.
3. Open `androidApp-debug.apk` on Android and allow installation from that
   browser/file manager, or use:

   ```sh
   adb install -r androidApp-debug.apk
   ```

The app is named **Music Assistant Playlist Test**, application ID
`io.music_assistant.client.playlisttest`, and installs beside the normal app.
It has separate settings/login storage. Debug signing keys differ between local
machines and fresh CI runners: if Android reports a signature mismatch for an
update, uninstall only the test app and install again (this clears its local
settings). Never uninstall the production app to install this test build.

Local APK output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
Build with an Android SDK and JDK 21:

```sh
./gradlew :composeApp:testAndroidHostTest --tests '*PlaylistPlaybackSettingsTest'
./gradlew detektAll :androidApp:lintDebug :androidApp:assembleDebug
```

On a compatible server, verify loading/cancelling causes no writes, all settings
persist on reopen, collaborator saves are blocked, and dynamic/non-editable
playlists cannot enable removal. Check episode/book rows in ascending and
descending order, mid-item resume, natural-end removal versus skip/stop, progress
updates/rewinds, and unchanged music-track behavior. Device playback validation
requires a connected server and player; offline tests mock the RPC contract.
