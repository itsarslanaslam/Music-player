package com.pulse.music.ui.screens

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulse.music.data.Song
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.Routes
import com.pulse.music.ui.components.Artwork
import com.pulse.music.ui.components.PlayPauseButton
import com.pulse.music.ui.components.SeekBar
import com.pulse.music.ui.components.SongRow
import com.pulse.music.ui.components.rememberPlaybackPosition
import com.pulse.music.ui.components.swipeToSkip
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.formatDuration
import com.pulse.music.util.songsLabel
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(vm: MusicViewModel, onCollapse: () -> Unit) {
    val songOrNull by vm.currentSong.collectAsStateWithLifecycle()
    val song = songOrNull ?: return
    val state by vm.playerState.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent by animateColorAsState(MaterialTheme.colorScheme.primary, tween(700), label = "accent")
    var showQueue by rememberSaveable { mutableStateOf(false) }

    // Drag the whole screen down to close it.
    val dragY = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 140.dp.toPx() }
    val collapse by rememberUpdatedState(onCollapse)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY.value > dismissPx) collapse()
                        else scope.launch { dragY.animateTo(0f, spring()) }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragY.snapTo((dragY.value + amount).coerceAtLeast(0f)) }
                }
            }
            .graphicsLayer { translationY = dragY.value }
            .background(PulseColors.Background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        // Background: blurred cover on Android 12+, then an accent wash that fades to the base color.
        if (Build.VERSION.SDK_INT >= 31) {
            val bg = remember(song.artUri) { ImageRequest.Builder(context).data(song.artUri).size(256).build() }
            AsyncImage(
                model = bg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.45f },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.50f),
                    0.45f to accent.copy(alpha = 0.14f),
                    1f to PulseColors.Background,
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close player", Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now playing", style = MaterialTheme.typography.labelMedium, color = PulseColors.OnSurface.copy(alpha = 0.7f))
                    Text(
                        song.album,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { actions.navigate(Routes.album(song.albumId)) }
                            .padding(horizontal = 6.dp),
                    )
                }
                IconButton(onClick = { actions.openSongMenu(song, null) }) {
                    Icon(Icons.Rounded.MoreVert, "More options")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Artwork fills whatever height is left and stays square.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val artScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1f else 0.86f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                    label = "artScale",
                )
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .swipeToSkip(onNext = vm::next, onPrevious = vm::previous, tilt = true)
                        .graphicsLayer { scaleX = artScale; scaleY = artScale }
                ) {
                    Crossfade(targetState = song.artUri, animationSpec = tween(350), label = "cover") { uri ->
                        Artwork(
                            uri,
                            Modifier
                                .fillMaxSize()
                                .shadow(32.dp, RoundedCornerShape(26.dp), ambientColor = accent, spotColor = accent),
                            RoundedCornerShape(26.dp),
                            iconSize = 96.dp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PulseColors.OnSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { actions.navigate(Routes.artist(song.artistId)) },
                    )
                }
                FavoriteButton(liked = song.id in favorites, tint = accent) { vm.toggleFavorite(song.id) }
            }

            Spacer(Modifier.height(18.dp))

            val duration = state.duration.takeIf { it > 0 } ?: song.duration
            PlaybackProgress(vm, state.isPlaying, song.id, duration)

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (state.shuffle) accent else PulseColors.Muted,
                    )
                }
                IconButton(onClick = vm::previous, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(40.dp))
                }
                PlayPauseButton(isPlaying = state.isPlaying, onClick = vm::togglePlay, size = 78.dp)
                IconButton(onClick = vm::next, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(40.dp))
                }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) PulseColors.Muted else accent,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.openSleepTimer) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        "Sleep timer",
                        tint = if (state.sleepTimerEnd != null) accent else PulseColors.Muted,
                    )
                }
                IconButton(onClick = { actions.navigate(Routes.EQUALIZER) }) {
                    Icon(Icons.Rounded.Tune, "Equalizer", tint = PulseColors.Muted)
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = PulseColors.Muted)
                }
            }
        }
    }

    if (showQueue) QueueSheet(vm, currentSong = song, onDismiss = { showQueue = false })
}

@Composable
private fun FavoriteButton(liked: Boolean, tint: Color, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    IconButton(onClick = {
        onClick()
        scope.launch {
            scale.animateTo(1.3f, tween(110))
            scale.animateTo(1f, spring(dampingRatio = 0.4f))
        }
    }) {
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (liked) "Remove from Liked songs" else "Add to Liked songs",
            tint = if (liked) tint else PulseColors.OnSurface,
            modifier = Modifier.size(28.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/** Isolated so the 4 Hz position updates only recompose this small piece. */
@Composable
private fun PlaybackProgress(vm: MusicViewModel, isPlaying: Boolean, songId: Long, duration: Long) {
    val position = rememberPlaybackPosition(vm, isPlaying, songId)
    var scrub by remember(songId) { mutableStateOf<Float?>(null) }
    Column {
        SeekBar(
            progress = {
                scrub ?: if (duration > 0) position.longValue.toFloat() / duration else 0f
            },
            onScrub = { scrub = it },
            onScrubEnd = { fraction ->
                val target = (fraction * duration).toLong()
                vm.seekTo(target)
                position.longValue = target
                scrub = null
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = scrub?.let { (it * duration).toLong() } ?: position.longValue
            Text(shown.formatDuration(), style = MaterialTheme.typography.labelMedium, color = PulseColors.Muted)
            Text(duration.formatDuration(), style = MaterialTheme.typography.labelMedium, color = PulseColors.Muted)
        }
    }
}

@Composable
private fun QueueSheet(vm: MusicViewModel, currentSong: Song, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val state by vm.playerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.currentIndex - 1).coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PulseColors.Surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(queue.size.songsLabel(), style = MaterialTheme.typography.bodyMedium, color = PulseColors.Muted)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.8f)) {
            items(queue, key = { "${it.index}:${it.song.id}" }) { item ->
                val isCurrent = item.index == state.currentIndex && item.song.id == currentSong.id
                SongRow(
                    song = item.song,
                    onClick = { vm.skipTo(item.index) },
                    highlight = isCurrent,
                    modifier = Modifier.animateItem(),
                    trailing = {
                        if (!isCurrent) {
                            IconButton(onClick = { vm.removeFromQueue(item.index) }) {
                                Icon(Icons.Rounded.Close, "Remove from queue", tint = PulseColors.Muted)
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
