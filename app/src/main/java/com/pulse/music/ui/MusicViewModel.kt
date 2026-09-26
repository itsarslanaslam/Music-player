package com.pulse.music.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.music.PulseApp
import com.pulse.music.data.FavoriteEntity
import com.pulse.music.data.Library
import com.pulse.music.data.PlaylistEntity
import com.pulse.music.data.PlaylistSongEntity
import com.pulse.music.data.PlaylistSummary
import com.pulse.music.data.QueueItem
import com.pulse.music.data.SearchResults
import com.pulse.music.data.Song
import com.pulse.music.data.SongSort
import com.pulse.music.data.ThemeMode
import com.pulse.music.playback.EqState
import com.pulse.music.playback.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PulseApp).container
    private val repository = container.repository
    private val player = container.player
    private val equalizer = container.equalizer
    private val prefs = container.prefs
    private val dao = container.database.playlistDao()

    val library: StateFlow<Library> = repository.library
    val scanning: StateFlow<Boolean> = repository.scanning
    val playerState: StateFlow<PlayerState> = player.state
    val eqState: StateFlow<EqState> = equalizer.state
    val songSort: StateFlow<SongSort> = prefs.songSort
    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
    val hiddenSongIds: StateFlow<Set<Long>> = prefs.hiddenSongIds

    val sortedSongs: StateFlow<List<Song>> = combine(library, songSort) { lib, sort ->
        if (sort == SongSort.TITLE) lib.songs else lib.songs.sortedWith(sort.comparator)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSong: StateFlow<Song?> = combine(
        player.state.map { it.currentMediaId }.distinctUntilChanged(),
        library,
    ) { id, lib -> id?.toLongOrNull()?.let { lib.songsById[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val queue: StateFlow<List<QueueItem>> = combine(
        player.state.map { it.queue }.distinctUntilChanged { a, b -> a === b },
        library,
    ) { entries, lib ->
        entries.mapNotNull { e -> e.mediaId.toLongOrNull()?.let { lib.songsById[it] }?.let { QueueItem(e.index, it) } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Counts and covers only consider songs still in the library (not removed, not deleted from the device).
    val playlists: StateFlow<List<PlaylistSummary>> = combine(
        dao.observePlaylists(),
        dao.observeAllPlaylistSongs(),
        library,
    ) { summaries, refs, lib ->
        if (!lib.loaded) return@combine summaries
        val present = refs.filter { it.songId in lib.songsById }.groupBy { it.playlistId }
        summaries.map { p ->
            val songs = present[p.id].orEmpty()
            p.copy(songCount = songs.size, firstSongId = songs.firstOrNull()?.songId)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<Long>> =
        dao.observeFavoriteIds().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteSongs: StateFlow<List<Song>> = combine(dao.observeFavoriteIds(), library) { ids, lib ->
        ids.mapNotNull { lib.songsById[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<SearchResults> = combine(_query.debounce(120), library) { q, lib -> search(q, lib) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

    fun playlistSongs(id: Long): Flow<List<Song>> = combine(dao.observeSongIds(id), library) { ids, lib ->
        ids.mapNotNull { lib.songsById[it] }
    }

    // Library
    fun onPermissionGranted() = repository.start()
    fun rescan() = repository.rescan()
    fun setSongSort(sort: SongSort) = prefs.setSongSort(sort)
    fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)

    fun removeFromLibrary(song: Song) {
        player.removeSongFromQueue(song.id)
        repository.hideSong(song.id)
        toast("Removed from library")
    }

    fun restoreRemovedSongs() {
        val count = hiddenSongIds.value.size
        repository.restoreHiddenSongs()
        toast(if (count == 1) "Restored 1 song" else "Restored $count songs")
    }
    fun setQuery(q: String) { _query.value = q }

    // Playback
    fun position(): Long = player.position
    fun play(songs: List<Song>, index: Int) = player.playSongs(songs, index)
    fun shuffle(songs: List<Song>) = player.shuffleAll(songs)
    fun togglePlay() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun skipTo(index: Int) = player.skipTo(index)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = player.moveInQueue(from, to)

    fun playNext(song: Song) {
        player.playNext(song)
        toast("Playing next")
    }

    fun addToQueue(songs: List<Song>) {
        player.addToQueue(songs)
        toast("Added to queue")
    }

    // Sleep timer
    fun setSleepTimer(minutes: Int) = player.setSleepTimer(minutes)
    fun sleepAtEndOfTrack() = player.sleepAtEndOfTrack()
    fun cancelSleepTimer() = player.cancelSleepTimer()

    // Equalizer
    fun setEqEnabled(enabled: Boolean) = equalizer.setEnabled(enabled)
    fun setBandLevel(index: Int, level: Int) = equalizer.setBandLevel(index, level)
    fun usePreset(preset: Int) = equalizer.usePreset(preset)
    fun setBass(strength: Int) = equalizer.setBass(strength)
    fun resetEq() = equalizer.reset()

    // Liked songs and playlists
    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            if (songId in favoriteIds.value) dao.removeFavorite(songId)
            else dao.addFavorite(FavoriteEntity(songId, System.currentTimeMillis()))
        }
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()) {
        viewModelScope.launch {
            val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
            if (songs.isNotEmpty()) {
                insertSongs(id, songs)
                toast("Added to $name")
            }
        }
    }

    fun addToPlaylist(playlistId: Long, playlistName: String, songs: List<Song>) {
        viewModelScope.launch {
            insertSongs(playlistId, songs)
            toast("Added to $playlistName")
        }
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { dao.removeSong(playlistId, songId) }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { dao.renamePlaylist(id, name.trim()) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { dao.deletePlaylist(id) }
    }

    private suspend fun insertSongs(playlistId: Long, songs: List<Song>) {
        val start = dao.maxPosition(playlistId) + 1
        dao.insertSongs(songs.mapIndexed { i, s -> PlaylistSongEntity(playlistId, s.id, start + i) })
    }

    private fun search(raw: String, lib: Library): SearchResults {
        val q = raw.trim()
        if (q.isEmpty()) return SearchResults()
        val songs = lib.songs.asSequence()
            .filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
            .sortedBy { if (it.title.startsWith(q, true)) 0 else if (it.title.contains(q, true)) 1 else 2 }
            .take(80).toList()
        val albums = lib.albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.take(20)
        val artists = lib.artists.filter { it.name.contains(q, true) }
            .sortedBy { if (it.name.startsWith(q, true)) 0 else 1 }.take(20)
        return SearchResults(q, songs, albums, artists)
    }

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}
