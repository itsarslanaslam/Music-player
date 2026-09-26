package com.pulse.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulse.music.data.Song
import com.pulse.music.ui.components.MiniPlayer
import com.pulse.music.ui.screens.AddToPlaylistDialog
import com.pulse.music.ui.screens.AlbumScreen
import com.pulse.music.ui.screens.ArtistScreen
import com.pulse.music.ui.screens.EqualizerScreen
import com.pulse.music.ui.screens.FavoritesScreen
import com.pulse.music.ui.screens.FolderScreen
import com.pulse.music.ui.screens.LibraryScreen
import com.pulse.music.ui.screens.NowPlayingScreen
import com.pulse.music.ui.screens.PermissionScreen
import com.pulse.music.ui.screens.PlaylistScreen
import com.pulse.music.ui.screens.SearchScreen
import com.pulse.music.ui.screens.SleepTimerSheet
import com.pulse.music.ui.screens.SongOptionsSheet
import com.pulse.music.ui.theme.PulseTheme
import com.pulse.music.ui.theme.rememberArtworkAccent
import com.pulse.music.util.hasAudioPermission

@Composable
fun AppRoot(vm: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val accent by rememberArtworkAccent(song?.artUri)
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }

    // Catches the case where the user grants access from system settings and comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!granted && hasAudioPermission(context)) {
            granted = true
            vm.onPermissionGranted()
        }
    }

    PulseTheme(accent) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!granted) {
                PermissionScreen(onGranted = {
                    granted = true
                    vm.onPermissionGranted()
                })
            } else {
                MainContent(vm, song)
            }
        }
    }
}

@Composable
private fun MainContent(vm: MusicViewModel, song: Song?) {
    val nav = rememberNavController()
    val state by vm.playerState.collectAsStateWithLifecycle()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pair<Song, Long?>?>(null) }
    var addToPlaylistFor by remember { mutableStateOf<Song?>(null) }
    var showSleepTimer by remember { mutableStateOf(false) }

    // Keep the last song around so the mini player can animate out gracefully.
    val lastSong = remember { LastSong() }
    if (song != null) lastSong.value = song

    val actions = remember(nav) {
        AppActions(
            navigate = { route ->
                playerOpen = false
                nav.navigate(route) { launchSingleTop = true }
            },
            back = { nav.popBackStack() },
            openSongMenu = { s, playlistId -> menuFor = s to playlistId },
            openSleepTimer = { showSleepTimer = true },
            openPlayer = { playerOpen = true },
        )
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBarBottom + if (song != null) 88.dp else 16.dp

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalBottomPadding provides bottomPadding,
        LocalNowPlaying provides NowPlayingInfo(song?.id, state.isPlaying),
    ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 14 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 14 } },
            ) {
                composable(Routes.LIBRARY) { LibraryScreen(vm) }
                composable(Routes.SEARCH) { SearchScreen(vm) }
                composable(Routes.EQUALIZER) { EqualizerScreen(vm) }
                composable(Routes.FAVORITES) { FavoritesScreen(vm) }
                composable(
                    "album/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry -> AlbumScreen(entry.arguments?.getLong("id") ?: 0L, vm) }
                composable(
                    "artist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry -> ArtistScreen(entry.arguments?.getLong("id") ?: 0L, vm) }
                composable(
                    "playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry -> PlaylistScreen(entry.arguments?.getLong("id") ?: 0L, vm) }
                composable("folder/{path}") { entry ->
                    // Navigation already decodes the path that Routes.folder() encoded.
                    FolderScreen(entry.arguments?.getString("path").orEmpty(), vm)
                }
            }

            AnimatedVisibility(
                visible = song != null && !playerOpen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                enter = slideInVertically(tween(300)) { it } + fadeIn(),
                exit = slideOutVertically(tween(250)) { it } + fadeOut(),
            ) {
                lastSong.value?.let { MiniPlayer(it, vm, onOpen = { playerOpen = true }) }
            }

            AnimatedVisibility(
                visible = playerOpen && song != null,
                enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250)),
            ) {
                NowPlayingScreen(vm, onCollapse = { playerOpen = false })
            }

            BackHandler(enabled = playerOpen) { playerOpen = false }
        }

        menuFor?.let { (s, playlistId) ->
            SongOptionsSheet(
                song = s,
                playlistId = playlistId,
                vm = vm,
                onDismiss = { menuFor = null },
                onAddToPlaylist = { addToPlaylistFor = it },
            )
        }
        addToPlaylistFor?.let { s ->
            AddToPlaylistDialog(song = s, vm = vm, onDismiss = { addToPlaylistFor = null })
        }
        if (showSleepTimer) {
            SleepTimerSheet(vm, onDismiss = { showSleepTimer = false })
        }
    }
}

/** Plain holder, not state: it only needs to survive while the mini player animates out. */
private class LastSong {
    var value: Song? = null
}
