package com.pulse.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.data.SongSort
import com.pulse.music.data.ThemeMode
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.LocalBottomPadding
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.AlbumCard
import com.pulse.music.ui.components.Artwork
import com.pulse.music.ui.components.CollectionRow
import com.pulse.music.ui.components.EmptyState
import com.pulse.music.ui.components.FastScroller
import com.pulse.music.ui.components.IconTile
import com.pulse.music.ui.components.SongRow
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.songsLabel
import kotlinx.coroutines.launch

private val Tabs = listOf("Songs", "Albums", "Artists", "Playlists", "Folders")

@Composable
private fun ThemeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PulseColors.SurfaceHigh,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun LibraryScreen(vm: MusicViewModel) {
    val pager = rememberPagerState(pageCount = { Tabs.size })
    val scope = rememberCoroutineScope()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    var menuOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }
    val hiddenCount = vm.hiddenSongIds.collectAsStateWithLifecycle().value.size

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.navigate(Routes.SEARCH) }) {
                Icon(Icons.Rounded.Search, "Search")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = PulseColors.SurfaceHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("Rescan library") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = { menuOpen = false; vm.rescan() },
                    )
                    DropdownMenuItem(
                        text = { Text("Equalizer") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = { menuOpen = false; actions.navigate(Routes.EQUALIZER) },
                    )
                    DropdownMenuItem(
                        text = { Text("Sleep timer") },
                        leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                        onClick = { menuOpen = false; actions.openSleepTimer() },
                    )
                    DropdownMenuItem(
                        text = { Text("Theme") },
                        leadingIcon = { Icon(Icons.Rounded.BrightnessMedium, null) },
                        onClick = { menuOpen = false; themeDialogOpen = true },
                    )
                    if (hiddenCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Restore removed songs ($hiddenCount)") },
                            leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                            onClick = { menuOpen = false; vm.restoreRemovedSongs() },
                        )
                    }
                }
            }
        }

        if (themeDialogOpen) {
            val current by vm.themeMode.collectAsStateWithLifecycle()
            ThemeDialog(
                current = current,
                onSelect = { vm.setThemeMode(it); themeDialogOpen = false },
                onDismiss = { themeDialogOpen = false },
            )
        }

        ScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            Tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = PulseColors.Muted,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (scanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SongsTab(vm)
                1 -> AlbumsTab(vm)
                2 -> ArtistsTab(vm)
                3 -> PlaylistsTab(vm)
                else -> FoldersTab(vm)
            }
        }
    }
}

@Composable
private fun LoadingOrEmpty(loaded: Boolean, vm: MusicViewModel) {
    if (loaded) {
        EmptyState(
            icon = Icons.Rounded.LibraryMusic,
            title = "No music found",
            message = "Copy audio files to this device, then rescan. Tracks shorter than 20 seconds are skipped.",
            actionLabel = "Rescan",
            onAction = vm::rescan,
        )
    }
}

@Composable
private fun SongsTab(vm: MusicViewModel) {
    val songs by vm.sortedSongs.collectAsStateWithLifecycle()
    val sort by vm.songSort.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    if (songs.isEmpty()) {
        LoadingOrEmpty(library.loaded, vm)
        return
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomPadding.current),
        ) {
            item(key = "header", contentType = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { vm.shuffle(songs) },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle ${songs.size}")
                    }
                    Spacer(Modifier.weight(1f))
                    SortButton(sort, vm::setSongSort)
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { index, song ->
                SongRow(song, onClick = { vm.play(songs, index) })
            }
        }
        FastScroller(listState, songs.size + 1)
    }
}

@Composable
private fun SortButton(current: SongSort, onSelect: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp), tint = PulseColors.Muted)
            Spacer(Modifier.width(6.dp))
            Text(current.label, color = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PulseColors.SurfaceHigh) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == current) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(vm: MusicViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    if (library.albums.isEmpty()) {
        LoadingOrEmpty(library.loaded, vm)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(152.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = LocalBottomPadding.current),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(library.albums, key = { it.id }) { album ->
            AlbumCard(
                title = album.title,
                subtitle = album.artist,
                artUri = album.artUri,
                onClick = { actions.navigate(Routes.album(album.id)) },
            )
        }
    }
}

@Composable
private fun ArtistsTab(vm: MusicViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    if (library.artists.isEmpty()) {
        LoadingOrEmpty(library.loaded, vm)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = LocalBottomPadding.current),
    ) {
        items(library.artists, key = { it.id }) { artist ->
            CollectionRow(
                title = artist.name,
                subtitle = buildString {
                    append(if (artist.albums.size == 1) "1 album" else "${artist.albums.size} albums")
                    append(", ")
                    append(artist.songs.size.songsLabel())
                },
                onClick = { actions.navigate(Routes.artist(artist.id)) },
            ) {
                Artwork(artist.artUri, Modifier.size(54.dp), CircleShape)
            }
        }
    }
}

@Composable
private fun PlaylistsTab(vm: MusicViewModel) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val accent = MaterialTheme.colorScheme.primary
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = LocalBottomPadding.current),
    ) {
        item(key = "new") {
            CollectionRow("New playlist", "Collect songs for any mood", onClick = { creating = true }) {
                IconTile(
                    Icons.Rounded.Add,
                    Modifier.size(54.dp),
                    brush = Brush.linearGradient(listOf(PulseColors.SurfaceHighest, PulseColors.SurfaceHigh)),
                )
            }
        }
        item(key = "liked") {
            CollectionRow("Liked songs", favorites.size.songsLabel(), onClick = { actions.navigate(Routes.FAVORITES) }) {
                IconTile(
                    Icons.Rounded.Favorite,
                    Modifier.size(54.dp),
                    brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f))),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            CollectionRow(
                title = playlist.name,
                subtitle = playlist.songCount.songsLabel(),
                onClick = { actions.navigate(Routes.playlist(playlist.id)) },
            ) {
                Artwork(playlist.firstSongId?.let { library.songsById[it]?.artUri }, Modifier.size(54.dp))
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                vm.createPlaylist(name)
                creating = false
            },
        )
    }
}

@Composable
private fun FoldersTab(vm: MusicViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    if (library.folders.isEmpty()) {
        LoadingOrEmpty(library.loaded, vm)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = LocalBottomPadding.current),
    ) {
        items(library.folders, key = { it.path }) { folder ->
            CollectionRow(
                title = folder.name,
                subtitle = folder.songs.size.songsLabel(),
                onClick = { actions.navigate(Routes.folder(folder.path)) },
            ) {
                IconTile(Icons.Rounded.Folder, Modifier.size(54.dp))
            }
        }
    }
}
