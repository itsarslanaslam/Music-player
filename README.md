# Pulse

A dark, minimal music player for Android, written in Kotlin with Jetpack Compose and Media3.
The accent color is pulled from the album art of whatever is playing, so the whole app
shifts color with your music.

## Run it

1. Open the `Pulse` folder in Android Studio (Ladybug 2024.2 or newer, JDK 17).
2. Let Gradle sync. The wrapper downloads Gradle 8.10.2 and all dependencies.
3. Pick a device or emulator on Android 8.0 or newer and press Run.
4. Tap "Allow access" so Pulse can read your music.

Release APK: `./gradlew assembleRelease` (output in `app/build/outputs/apk/release`).
The release build is minified with R8 and signed with the debug key so you can install
it right away. Switch to your own keystore in `app/build.gradle.kts` before publishing.

## Features

**Library**
- Songs, Albums, Artists, Playlists and Folders tabs with swipeable pages
- Sort songs by title, artist, album, recently added or length
- Fast scroller for big libraries
- Search across songs, albums and artists
- Library refreshes by itself when files are added or removed
- Skips ringtones and voice notes (tracks under 20 seconds)

**Playback**
- Background playback with notification, lock screen and Bluetooth controls (Media3)
- Gapless playback, audio focus handling, pauses when headphones are unplugged
- Shuffle that keeps a real, visible play order and restores the original order when turned off
- Repeat all and repeat one
- Queue: play next, add to queue, jump to any song, remove songs
- Remembers your queue and position after the app is closed
- Sleep timer (5 to 90 minutes, or at the end of the current song) with a soft fade out
- Equalizer with device presets, per band sliders and bass boost

**Your collection**
- Liked songs
- Playlists: create, rename, delete, add and remove songs
- Share any song

**Interface**
- Accent color extracted from album art with Palette, tuned for a dark background
- Now Playing: blurred cover background (Android 12+), swipe the cover to skip,
  drag down to close, cover shrinks while paused
- Floating mini player: tap to open, swipe sideways to skip
- Animated playing indicator on the current song everywhere it appears

## How it's built

```
app/src/main/java/com/pulse/music
  PulseApp.kt              App container (repository, database, player, equalizer)
  MainActivity.kt          Edge to edge, connects the player, hosts Compose
  data/
    Models.kt              Song, Album, Artist, Folder, Library, sorting
    MusicRepository.kt     MediaStore scan and auto refresh
    Database.kt            Room: playlists and liked songs
    AppPrefs.kt            Sort order, saved queue, equalizer settings
  playback/
    PlaybackService.kt     MediaSessionService with ExoPlayer
    PlayerController.kt    MediaController wrapper exposing StateFlow to the UI
    EqualizerManager.kt    Equalizer and BassBoost bound to the audio session
  ui/
    AppRoot.kt             Theme, permission gate, navigation, mini player, overlays
    MusicViewModel.kt      Single activity-scoped view model
    theme/Theme.kt         Colors, type, album-art accent extraction
    components/            Artwork, song rows, seek bar, mini player, fast scroller
    screens/               Library, Now Playing, details, search, equalizer, sheets
```

Performance notes:
- Library grouping, sorting and search run on background dispatchers.
- Lists use stable keys and content types.
- The playback position is polled only while music plays and is read in the draw
  phase, so the progress bars update without recomposing the screen.
- The album-art color changes once per song, not every frame.

## Note

This project was written without access to the Android SDK, so it has not been compiled
yet. If the first sync or build reports an error, it will most likely be a small API
mismatch that is quick to fix.
