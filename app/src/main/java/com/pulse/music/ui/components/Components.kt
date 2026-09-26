package com.pulse.music.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pulse.music.data.Song
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.LocalBottomPadding
import com.pulse.music.ui.LocalNowPlaying
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.formatDuration
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Album art with a quiet placeholder. The note icon shows through when a file has no art. */
@Composable
fun Artwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    iconSize: Dp = 22.dp,
) {
    val context = LocalContext.current
    val placeholder = Brush.linearGradient(listOf(PulseColors.SurfaceHighest, PulseColors.Surface))
    Box(modifier.clip(shape).background(placeholder), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = PulseColors.Muted.copy(alpha = 0.45f),
            modifier = Modifier.size(iconSize),
        )
        if (uri != null) {
            val request = remember(uri) { ImageRequest.Builder(context).data(uri).crossfade(180).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    iconSize: Dp = 24.dp,
    brush: Brush = PlaceholderBrush,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier.clip(shape).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Three little bars that bounce while a song plays. Animation values are read in the draw phase only. */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val a = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(340), RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(16.dp)) {
        val w = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, v ->
            val h = size.height * (if (playing) v.value else 0.3f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2 * w, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playlistId: Long? = null,
    highlight: Boolean? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = highlight ?: (nowPlaying.songId == song.id)
    val accent = MaterialTheme.colorScheme.primary
    val actions = LocalActions.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else {
            Box(Modifier.size(50.dp)) {
                Artwork(song.artUri, Modifier.matchParentSize(), RoundedCornerShape(10.dp))
                if (isCurrent) {
                    Box(
                        Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { PlayingBars(nowPlaying.isPlaying, accent) }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(
                song.duration.formatDuration(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = { actions.openSongMenu(song, playlistId) }) {
                Icon(Icons.Rounded.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Track number that turns into the playing indicator for the current song. */
@Composable
fun TrackNumber(song: Song) {
    val nowPlaying = LocalNowPlaying.current
    Box(Modifier.size(width = 32.dp, height = 44.dp), contentAlignment = Alignment.Center) {
        if (nowPlaying.songId == song.id) {
            PlayingBars(nowPlaying.isPlaying, MaterialTheme.colorScheme.primary)
        } else {
            val n = song.track % 1000
            Text(
                if (n > 0) n.toString() else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    artUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Artwork(artUri, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp), iconSize = 40.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(icon, Modifier.size(84.dp), CircleShape, iconSize = 36.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Big art, title, and Play / Shuffle buttons at the top of album, artist and playlist screens. */
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    meta: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    art: @Composable (Modifier) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        art(Modifier.size(216.dp))
        Spacer(Modifier.height(22.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = if (onSubtitleClick != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = if (onSubtitleClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSubtitleClick).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            FilledTonalButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PulseColors.SurfaceHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Rounded.Shuffle, null, tint = accent)
                Spacer(Modifier.width(6.dp))
                Text("Shuffle")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Large artwork with a soft glow in the current accent color. */
@Composable
fun HeaderArtwork(uri: Uri?, modifier: Modifier, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(22.dp)
    val accent = MaterialTheme.colorScheme.primary
    Artwork(
        uri,
        modifier.shadow(28.dp, shape, ambientColor = accent, spotColor = accent),
        shape,
        iconSize = 64.dp,
    )
}

/** Detail screen frame: back button, a title that fades in once the header scrolls away, and a list. */
@Composable
fun DetailScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val appActions = LocalActions.current
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent)))
        )
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    AnimatedVisibility(visible = showTitle, enter = fadeIn(), exit = fadeOut()) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = appActions.back) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomPadding.current),
                content = content,
            )
        }
    }
}

/**
 * Draggable scrollbar for long lists. It appears while scrolling and only
 * intercepts touches while visible, so it never blocks the row buttons.
 */
@Composable
fun BoxScope.FastScroller(state: LazyListState, itemCount: Int) {
    if (itemCount < 50) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val active = dragging || state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = if (active) 120 else 500, delayMillis = if (active) 0 else 900),
        label = "fastScroller",
    )
    val accent = MaterialTheme.colorScheme.primary
    val line = PulseColors.Line
    val bottom = LocalBottomPadding.current
    val touchable = alpha > 0.05f

    Canvas(
        Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(28.dp)
            .padding(top = 8.dp, bottom = bottom, end = 4.dp)
            .then(
                if (touchable) Modifier.pointerInput(itemCount) {
                    fun jump(y: Float) {
                        val fraction = (y / size.height).coerceIn(0f, 1f)
                        scope.launch { state.scrollToItem((fraction * (itemCount - 1)).roundToInt()) }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; jump(it.y) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        jump(change.position.y)
                    }
                } else Modifier
            )
    ) {
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxFirst = (itemCount - visible).coerceAtLeast(1)
        val progress = (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
        val w = 4.dp.toPx()
        val thumb = 48.dp.toPx()
        val x = size.width - w
        drawRoundRect(
            color = line.copy(alpha = 0.7f * alpha),
            topLeft = Offset(x, 0f),
            size = Size(w, size.height),
            cornerRadius = CornerRadius(w / 2),
        )
        drawRoundRect(
            color = accent.copy(alpha = alpha),
            topLeft = Offset(x - 2.dp.toPx(), progress * (size.height - thumb)),
            size = Size(w + 4.dp.toPx(), thumb),
            cornerRadius = CornerRadius(w),
        )
    }
}
