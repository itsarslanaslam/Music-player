package com.pulse.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.LocalBottomPadding
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.AlbumCard
import com.pulse.music.ui.components.Artwork
import com.pulse.music.ui.components.CollectionRow
import com.pulse.music.ui.components.EmptyState
import com.pulse.music.ui.components.SectionTitle
import com.pulse.music.ui.components.SongRow
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.songsLabel
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: MusicViewModel) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            TextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Songs, albums, artists") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PulseColors.Muted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PulseColors.SurfaceHigh,
                    unfocusedContainerColor = PulseColors.SurfaceHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomPadding.current),
        ) {
            if (query.isBlank()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Search your library",
                        message = "Find any song by its title, artist or album.",
                    )
                }
            } else if (results.isEmpty && results.query == query.trim()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No matches for \"${query.trim()}\"",
                        message = "Check the spelling or try fewer words.",
                    )
                }
            }
            if (results.artists.isNotEmpty()) {
                item(key = "artistsTitle") { SectionTitle("Artists") }
                items(results.artists, key = { "artist${it.id}" }) { artist ->
                    CollectionRow(
                        title = artist.name,
                        subtitle = artist.songs.size.songsLabel(),
                        onClick = { actions.navigate(Routes.artist(artist.id)) },
                    ) { Artwork(artist.artUri, Modifier.size(50.dp), CircleShape) }
                }
            }
            if (results.albums.isNotEmpty()) {
                item(key = "albumsTitle") { SectionTitle("Albums") }
                item(key = "albums") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                        items(results.albums, key = { it.id }) { album ->
                            AlbumCard(
                                title = album.title,
                                subtitle = album.artist,
                                artUri = album.artUri,
                                onClick = { actions.navigate(Routes.album(album.id)) },
                                modifier = Modifier.width(144.dp),
                            )
                        }
                    }
                }
            }
            if (results.songs.isNotEmpty()) {
                item(key = "songsTitle") { SectionTitle("Songs") }
                itemsIndexed(results.songs, key = { _, s -> "song${s.id}" }) { index, song ->
                    SongRow(song, onClick = {
                        keyboard?.hide()
                        vm.play(results.songs, index)
                    })
                }
            }
        }
    }
}
