package com.pulse.music.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.Song
import com.pulse.music.playback.SLEEP_END_OF_TRACK
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.Artwork
import com.pulse.music.ui.components.IconTile
import com.pulse.music.ui.components.ShipWheelFilled
import com.pulse.music.ui.components.ShipWheelOutline
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.formatDuration
import com.pulse.music.util.songsLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SongOptionsSheet(
    song: Song,
    playlistId: Long?,
    vm: MusicViewModel,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val liked = song.id in favorites

    fun close(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = PulseColors.Surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artUri, Modifier.size(56.dp), RoundedCornerShape(12.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${song.artist}, ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PulseColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = PulseColors.Line)
        Spacer(Modifier.height(6.dp))

        SheetOption(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { vm.playNext(song); close() }
        SheetOption(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { vm.addToQueue(listOf(song)); close() }
        SheetOption(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { close { onAddToPlaylist(song) } }
        SheetOption(
            if (liked) ShipWheelFilled else ShipWheelOutline,
            if (liked) "Remove from Liked songs" else "Add to Liked songs",
            tint = if (liked) MaterialTheme.colorScheme.primary else PulseColors.OnSurface,
        ) { vm.toggleFavorite(song.id); close() }
        SheetOption(Icons.Rounded.Album, "Go to album") { close { actions.navigate(Routes.album(song.albumId)) } }
        SheetOption(Icons.Rounded.Person, "Go to artist") { close { actions.navigate(Routes.artist(song.artistId)) } }
        if (playlistId != null) {
            SheetOption(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") {
                vm.removeFromPlaylist(playlistId, song.id)
                close()
            }
        }
        SheetOption(Icons.Rounded.Share, "Share") {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, song.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            close { context.startActivity(Intent.createChooser(send, "Share ${song.title}")) }
        }
        SheetOption(Icons.Rounded.VisibilityOff, "Remove from library") {
            vm.removeFromLibrary(song)
            close()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetOption(
    icon: ImageVector,
    label: String,
    tint: Color = PulseColors.OnSurface,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AddToPlaylistDialog(song: Song, vm: MusicViewModel, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = onDismiss,
            onConfirm = { name ->
                vm.createPlaylist(name, listOf(song))
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PulseColors.SurfaceHigh,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    PlaylistChoice(
                        title = "New playlist",
                        subtitle = null,
                        onClick = { creating = true },
                    ) { IconTile(Icons.Rounded.Add, Modifier.size(44.dp), RoundedCornerShape(10.dp)) }
                }
                items(playlists, key = { it.id }) { p ->
                    PlaylistChoice(
                        title = p.name,
                        subtitle = p.songCount.songsLabel(),
                        onClick = {
                            vm.addToPlaylist(p.id, p.name, listOf(song))
                            onDismiss()
                        },
                    ) {
                        Artwork(
                            p.firstSongId?.let { library.songsById[it]?.artUri },
                            Modifier.size(44.dp),
                            RoundedCornerShape(10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlaylistChoice(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PulseColors.Muted)
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PulseColors.SurfaceHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SleepTimerSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    val end = state.sleepTimerEnd
    val now by produceState(System.currentTimeMillis(), end) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PulseColors.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    end == null -> "Music keeps playing until you stop it."
                    end == SLEEP_END_OF_TRACK -> "Pauses when this song ends."
                    else -> "Pauses in ${(end - now).coerceAtLeast(0).formatDuration()}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (end != null) MaterialTheme.colorScheme.primary else PulseColors.Muted,
            )
            Spacer(Modifier.height(12.dp))
        }
        val options = listOf(5, 15, 30, 45, 60, 90)
        options.forEach { minutes ->
            TimerOption("$minutes minutes") { vm.setSleepTimer(minutes); onDismiss() }
        }
        TimerOption("When this song ends") { vm.sleepAtEndOfTrack(); onDismiss() }
        if (end != null) {
            TimerOption("Turn off timer", color = MaterialTheme.colorScheme.primary) { vm.cancelSleepTimer(); onDismiss() }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimerOption(label: String, color: Color = PulseColors.OnSurface, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}
