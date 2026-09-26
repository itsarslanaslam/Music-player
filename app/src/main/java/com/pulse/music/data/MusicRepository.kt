package com.pulse.music.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads the device's music from MediaStore and keeps it up to date.
 * Grouping into albums, artists and folders happens once per scan, off the main thread.
 */
class MusicRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var started = false
    private var scanJob: Job? = null

    fun start() {
        if (started) return
        started = true
        rescan()
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    rescan(debounceMs = 1500)
                }
            },
        )
    }

    fun rescan(debounceMs: Long = 0) {
        scanJob?.cancel()
        scanJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _scanning.value = true
            try {
                _library.value = withContext(Dispatchers.IO) { buildLibrary(querySongs()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Missing permission or a provider hiccup. Keep whatever we had.
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun querySongs(): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())
        val songs = ArrayList<Song>(512)

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                val displayName = c.getString(nameCol) ?: "Unknown"
                songs += Song(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol).clean() ?: displayName.substringBeforeLast('.'),
                    artist = c.getString(artistCol).clean() ?: "Unknown artist",
                    album = c.getString(albumCol).clean() ?: "Unknown album",
                    albumId = c.getLong(albumIdCol),
                    artistId = c.getLong(artistIdCol),
                    duration = c.getLong(durationCol),
                    track = c.getInt(trackCol),
                    year = c.getInt(yearCol),
                    dateAdded = c.getLong(addedCol),
                    path = c.getString(dataCol) ?: "",
                )
            }
        }
        return songs
    }

    private fun buildLibrary(raw: List<Song>): Library {
        val ci = String.CASE_INSENSITIVE_ORDER
        val songs = raw.sortedWith(compareBy<Song, String>(ci) { it.title })

        val albums = raw.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedWith(compareBy<Song> { it.track }.thenBy(ci) { it.title })
            Album(
                id = id,
                title = sorted.first().album,
                artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "Unknown artist",
                year = list.maxOf { it.year },
                songs = sorted,
            )
        }.sortedWith(compareBy<Album, String>(ci) { it.title })
        val albumsById = albums.associateBy { it.id }

        val artists = raw.groupBy { it.artistId }.map { (id, list) ->
            val artistAlbums = list.map { it.albumId }.distinct()
                .mapNotNull { albumsById[it] }
                .sortedByDescending { it.year }
            Artist(id, list.first().artist, list.sortedWith(compareBy<Song, String>(ci) { it.title }), artistAlbums)
        }.sortedWith(compareBy<Artist, String>(ci) { it.name })

        val folders = raw.filter { it.folder.isNotEmpty() }.groupBy { it.folder }.map { (path, list) ->
            Folder(path, path.substringAfterLast('/'), list.sortedWith(compareBy<Song, String>(ci) { it.title }))
        }.sortedWith(compareBy<Folder, String>(ci) { it.name })

        return Library(songs, albums, artists, folders, loaded = true)
    }

    private fun String?.clean(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }

    companion object {
        /** Skip ringtones, notification sounds and voice notes. */
        const val MIN_DURATION_MS = 20_000L
    }
}
