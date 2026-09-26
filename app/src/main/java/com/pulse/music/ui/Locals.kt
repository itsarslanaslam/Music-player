package com.pulse.music.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.pulse.music.data.Song

@Stable
class AppActions(
    val navigate: (String) -> Unit,
    val back: () -> Unit,
    val openSongMenu: (song: Song, playlistId: Long?) -> Unit,
    val openSleepTimer: () -> Unit,
    val openPlayer: () -> Unit,
)

@Immutable
data class NowPlayingInfo(val songId: Long? = null, val isPlaying: Boolean = false)

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions not provided") }

/** Space to leave at the bottom of scrolling content so the mini player never covers it. */
val LocalBottomPadding = compositionLocalOf { 0.dp }

val LocalNowPlaying = compositionLocalOf { NowPlayingInfo() }

object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val EQUALIZER = "equalizer"
    const val FAVORITES = "favorites"
    fun album(id: Long) = "album/$id"
    fun artist(id: Long) = "artist/$id"
    fun playlist(id: Long) = "playlist/$id"
    fun folder(path: String) = "folder/${Uri.encode(path)}"
}
