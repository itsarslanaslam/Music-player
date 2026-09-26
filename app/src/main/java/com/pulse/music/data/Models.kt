package com.pulse.music.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

fun songUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Long,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
    val path: String,
) {
    val uri: Uri = songUri(id)
    val artUri: Uri = albumArtUri(albumId)
    val folder: String = path.substringBeforeLast('/', "")
}

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val artUri: Uri = albumArtUri(id)
}

@Immutable
data class Artist(
    val id: Long,
    val name: String,
    val songs: List<Song>,
    val albums: List<Album>,
) {
    val artUri: Uri? = albums.firstOrNull()?.artUri ?: songs.firstOrNull()?.artUri
}

@Immutable
data class Folder(val path: String, val name: String, val songs: List<Song>)

@Immutable
data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val loaded: Boolean = false,
) {
    val songsById: Map<Long, Song> = songs.associateBy { it.id }
    val albumsById: Map<Long, Album> = albums.associateBy { it.id }
    val artistsById: Map<Long, Artist> = artists.associateBy { it.id }
    val foldersByPath: Map<String, Folder> = folders.associateBy { it.path }
}

@Immutable
data class SearchResults(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

@Immutable
data class QueueItem(val index: Int, val song: Song)

enum class SongSort(val label: String, val comparator: Comparator<Song>) {
    TITLE("Title", compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title }),
    ARTIST("Artist", compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
    ALBUM("Album", compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.track }),
    DATE_ADDED("Recently added", compareByDescending<Song> { it.dateAdded }),
    DURATION("Longest first", compareByDescending<Song> { it.duration }),
}

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .build()
        )
        .build()
