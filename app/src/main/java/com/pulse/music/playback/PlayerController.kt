package com.pulse.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pulse.music.data.AppPrefs
import com.pulse.music.data.MusicRepository
import com.pulse.music.data.SavedQueue
import com.pulse.music.data.Song
import com.pulse.music.data.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

@Immutable
data class QueueEntry(val index: Int, val mediaId: String)

@Immutable
data class PlayerState(
    val connected: Boolean = false,
    val currentMediaId: String? = null,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val duration: Long = 0L,
    val queue: List<QueueEntry> = emptyList(),
    /** Epoch millis when the sleep timer fires, [SLEEP_END_OF_TRACK], or null when off. */
    val sleepTimerEnd: Long? = null,
)

const val SLEEP_END_OF_TRACK = -1L

/**
 * UI-facing wrapper around a MediaController connected to [PlaybackService].
 *
 * Shuffle is handled here rather than by ExoPlayer's shuffle order: the queue is
 * physically reordered so the "Up next" list always shows the real play order,
 * and turning shuffle off restores the original order without interrupting playback.
 */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: MusicRepository,
    private val prefs: AppPrefs,
) {
    private var controller: MediaController? = null
    private var connecting = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var originalOrder: List<MediaItem>? = null
    private var sleepJob: Job? = null
    private var saveJob: Job? = null
    private var pauseAtEndOfTrack = false
    private var restoreAttempted = false

    val position: Long get() = controller?.currentPosition ?: 0L

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync(c, rebuildQueue = true)
            _state.update { it.copy(connected = true) }
            scope.launch { restoreQueue() }
        }, ContextCompat.getMainExecutor(context))
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (pauseAtEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                pauseAtEndOfTrack = false
                controller?.pause()
                _state.update { it.copy(sleepTimerEnd = null) }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            sync(player, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) scheduleSave()
        }
    }

    private fun sync(p: Player, rebuildQueue: Boolean) {
        _state.update { s ->
            s.copy(
                currentMediaId = p.currentMediaItem?.mediaId,
                currentIndex = p.currentMediaItemIndex,
                isPlaying = p.playWhenReady &&
                    (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING),
                repeatMode = p.repeatMode,
                duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
                queue = if (rebuildQueue) List(p.mediaItemCount) { QueueEntry(it, p.getMediaItemAt(it).mediaId) } else s.queue,
            )
        }
    }

    // region Playback actions

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val start = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }
        if (_state.value.shuffle) {
            originalOrder = items
            val rest = items.filterIndexed { i, _ -> i != start }.shuffled()
            c.setMediaItems(listOf(items[start]) + rest, 0, 0L)
        } else {
            originalOrder = null
            c.setMediaItems(items, start, 0L)
        }
        c.prepare()
        c.play()
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        _state.update { it.copy(shuffle = true) }
        playSongs(songs, Random.nextInt(songs.size))
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(c.currentMediaItemIndex)
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }

    fun previous() { controller?.seekToPrevious() }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val enable = !_state.value.shuffle
        _state.update { it.copy(shuffle = enable) }
        val count = c.mediaItemCount
        val current = c.currentMediaItemIndex
        if (count <= 1) {
            originalOrder = null
            scheduleSave()
            return
        }
        if (enable) {
            val all = List(count) { c.getMediaItemAt(it) }
            originalOrder = all
            val rest = all.filterIndexed { i, _ -> i != current }.shuffled()
            // Remove everything around the current song so playback never stops.
            c.removeMediaItems(current + 1, count)
            c.removeMediaItems(0, current)
            c.addMediaItems(rest)
        } else {
            val original = originalOrder
            originalOrder = null
            val currentId = c.currentMediaItem?.mediaId
            val pos = original?.indexOfFirst { it.mediaId == currentId } ?: -1
            if (original != null && pos >= 0) {
                c.removeMediaItems(current + 1, count)
                c.removeMediaItems(0, current)
                c.addMediaItems(0, original.subList(0, pos))
                c.addMediaItems(original.subList(pos + 1, original.size))
            }
        }
        scheduleSave()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playSongs(listOf(song))
            return
        }
        val item = song.toMediaItem()
        c.addMediaItem(c.currentMediaItemIndex + 1, item)
        originalOrder?.let { list ->
            val at = list.indexOfFirst { it.mediaId == c.currentMediaItem?.mediaId }
            originalOrder = list.toMutableList().apply { add(if (at >= 0) at + 1 else size, item) }
        }
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) {
            playSongs(songs)
            return
        }
        val items = songs.map { it.toMediaItem() }
        c.addMediaItems(items)
        originalOrder = originalOrder?.plus(items)
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        val id = c.getMediaItemAt(index).mediaId
        c.removeMediaItem(index)
        originalOrder = originalOrder?.toMutableList()?.apply {
            val at = indexOfFirst { it.mediaId == id }
            if (at >= 0) removeAt(at)
        }
    }

    fun removeSongFromQueue(songId: Long) {
        val c = controller ?: return
        val id = songId.toString()
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == id) c.removeMediaItem(i)
        }
        originalOrder = originalOrder?.filterNot { it.mediaId == id }
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from == to || from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
    }

    // endregion

    // region Sleep timer

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val ms = minutes * 60_000L
        _state.update { it.copy(sleepTimerEnd = System.currentTimeMillis() + ms) }
        sleepJob = scope.launch {
            delay(ms)
            fadeOutAndPause()
            _state.update { it.copy(sleepTimerEnd = null) }
        }
    }

    fun sleepAtEndOfTrack() {
        cancelSleepTimer()
        pauseAtEndOfTrack = true
        _state.update { it.copy(sleepTimerEnd = SLEEP_END_OF_TRACK) }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        pauseAtEndOfTrack = false
        _state.update { it.copy(sleepTimerEnd = null) }
    }

    private suspend fun fadeOutAndPause() {
        val c = controller ?: return
        val start = c.volume
        try {
            repeat(20) { step ->
                c.volume = start * (1f - (step + 1) / 20f)
                delay(150)
            }
            c.pause()
        } finally {
            c.volume = start
        }
    }

    // endregion

    // region Persistence

    /** Saves the queue and position right away (called when the app goes to the background). */
    fun persist() {
        saveJob?.cancel()
        saveNow()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            saveNow()
        }
    }

    private fun saveNow() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }.filter { it >= 0 }
        prefs.saveQueue(
            SavedQueue(
                ids = ids,
                currentId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L,
                position = c.currentPosition,
                shuffle = _state.value.shuffle,
                repeatMode = c.repeatMode,
            )
        )
    }

    private suspend fun restoreQueue() {
        if (restoreAttempted) return
        restoreAttempted = true
        val saved = prefs.loadQueue() ?: return
        if ((controller?.mediaItemCount ?: 1) > 0) return
        val library = withTimeoutOrNull(60_000) {
            repository.library.first { it.loaded && it.songs.isNotEmpty() }
        } ?: return
        val c = controller ?: return
        if (c.mediaItemCount > 0) return

        val songs = saved.ids.mapNotNull { library.songsById[it] }
        if (songs.isEmpty()) return
        val found = songs.indexOfFirst { it.id == saved.currentId }
        val index = if (found >= 0) found else 0
        _state.update { it.copy(shuffle = saved.shuffle) }
        c.repeatMode = saved.repeatMode
        c.setMediaItems(songs.map { it.toMediaItem() }, index, if (found >= 0) saved.position else 0L)
        c.prepare()
    }

    // endregion
}
