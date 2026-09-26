package com.pulse.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Song
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.AlbumCard
import com.pulse.music.ui.components.CollectionHeader
import com.pulse.music.ui.components.DetailScaffold
import com.pulse.music.ui.components.EmptyState
import com.pulse.music.ui.components.HeaderArtwork
import com.pulse.music.ui.components.IconTile
import com.pulse.music.ui.components.SectionTitle
import com.pulse.music.ui.components.SongRow
import com.pulse.music.ui.components.TrackNumber
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.songsLabel
import com.pulse.music.util.totalDurationLabel

private fun List<Song>.meta(): String = "${size.songsLabel()}, ${totalDurationLabel()}"

@Composable
private fun NotFound() {
    DetailScaffold(title = "") {
        item {
            EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "Not on this device anymore",
                message = "These songs were moved or deleted. Rescan the library to refresh it.",
            )
        }
    }
}

@Composable
fun AlbumScreen(albumId: Long, vm: MusicViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val album = library.albumsById[albumId] ?: return NotFound()
    val artistId = album.songs.firstOrNull()?.artistId

    DetailScaffold(title = album.title) {
        item(key = "header") {
            CollectionHeader(
                title = album.title,
                subtitle = album.artist,
                meta = listOfNotNull(album.year.takeIf { it > 0 }?.toString(), album.songs.meta()).joinToString(", "),
                onPlay = { vm.play(album.songs, 0) },
                onShuffle = { vm.shuffle(album.songs) },
                onSubtitleClick = artistId?.let { id -> { actions.navigate(Routes.artist(id)) } },
            ) { m -> HeaderArtwork(album.artUri, m) }
        }
        itemsIndexed(album.songs, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { vm.play(album.songs, index) }, leading = { TrackNumber(song) })
        }
    }
}

@Composable
fun ArtistScreen(artistId: Long, vm: MusicViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val artist = library.artistsById[artistId] ?: return NotFound()

    DetailScaffold(title = artist.name) {
        item(key = "header") {
            CollectionHeader(
                title = artist.name,
                subtitle = "",
                meta = artist.songs.meta(),
                onPlay = { vm.play(artist.songs, 0) },
                onShuffle = { vm.shuffle(artist.songs) },
            ) { m -> HeaderArtwork(artist.artUri, m, circle = true) }
        }
        if (artist.albums.isNotEmpty()) {
            item(key = "albumsTitle") { SectionTitle("Albums") }
            item(key = "albums") {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    items(artist.albums, key = { it.id }) { album ->
                        AlbumCard(
                            title = album.title,
                            subtitle = if (album.year > 0) album.year.toString() else album.songs.size.songsLabel(),
                            artUri = album.artUri,
                            onClick = { actions.navigate(Routes.album(album.id)) },
                            modifier = Modifier.width(150.dp),
                        )
                    }
                }
            }
        }
        item(key = "songsTitle") { SectionTitle("Songs") }
        itemsIndexed(artist.songs, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { vm.play(artist.songs, index) })
        }
    }
}

@Composable
fun FolderScreen(path: String, vm: MusicViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val folder = library.foldersByPath[path] ?: return NotFound()
    DetailScaffold(title = folder.name) {
        item(key = "header") {
            CollectionHeader(
                title = folder.name,
                subtitle = folder.path,
                meta = folder.songs.meta(),
                onPlay = { vm.play(folder.songs, 0) },
                onShuffle = { vm.shuffle(folder.songs) },
            ) { m -> IconTile(Icons.Rounded.Folder, m, RoundedCornerShape(22.dp), iconSize = 72.dp) }
        }
        itemsIndexed(folder.songs, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { vm.play(folder.songs, index) })
        }
    }
}

@Composable
fun FavoritesScreen(vm: MusicViewModel) {
    val songs by vm.favoriteSongs.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    DetailScaffold(title = "Liked songs") {
        item(key = "header") {
            CollectionHeader(
                title = "Liked songs",
                subtitle = "",
                meta = songs.meta(),
                onPlay = { vm.play(songs, 0) },
                onShuffle = { vm.shuffle(songs) },
            ) { m ->
                IconTile(
                    Icons.Rounded.Favorite,
                    m.shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = accent, spotColor = accent),
                    RoundedCornerShape(22.dp),
                    iconSize = 80.dp,
                    brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.3f))),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Tap the heart on any song to save it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PulseColors.Muted,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { vm.play(songs, index) })
        }
    }
}

@Composable
fun PlaylistScreen(playlistId: Long, vm: MusicViewModel) {
    val playlist by remember(playlistId) { vm.playlist(playlistId) }.collectAsStateWithLifecycle(null)
    val songs by remember(playlistId) { vm.playlistSongs(playlistId) }.collectAsStateWithLifecycle(emptyList())
    val actions = LocalActions.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val name = playlist?.name.orEmpty()

    DetailScaffold(
        title = name,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Playlist options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = PulseColors.SurfaceHigh) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        },
    ) {
        item(key = "header") {
            CollectionHeader(
                title = name,
                subtitle = "Playlist",
                meta = songs.meta(),
                onPlay = { vm.play(songs, 0) },
                onShuffle = { vm.shuffle(songs) },
            ) { m -> HeaderArtwork(songs.firstOrNull()?.artUri, m) }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                Text(
                    "This playlist is empty. Open the menu on any song and choose Add to playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PulseColors.Muted,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            SongRow(song, onClick = { vm.play(songs, index) }, playlistId = playlistId)
        }
    }

    if (renaming) {
        NameDialog(
            title = "Rename playlist",
            initial = name,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
            onConfirm = { vm.renamePlaylist(playlistId, it); renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = PulseColors.SurfaceHigh,
            title = { Text("Delete $name?") },
            text = { Text("The playlist is removed. Your songs stay on the device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deletePlaylist(playlistId)
                    actions.back()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
