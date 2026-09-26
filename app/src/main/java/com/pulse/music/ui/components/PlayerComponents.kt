package com.pulse.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.music.data.Song
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.theme.PulseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Polls the playback position only while something is playing and only while the
 * caller is on screen. Read the value in a draw lambda to avoid recomposition.
 */
@Composable
fun rememberPlaybackPosition(vm: MusicViewModel, isPlaying: Boolean, key: Any?): MutableLongState {
    val position = remember { mutableLongStateOf(vm.position()) }
    LaunchedEffect(isPlaying, key) {
        position.longValue = vm.position()
        while (isPlaying) {
            delay(250)
            position.longValue = vm.position()
        }
    }
    return position
}

/** Drag sideways to skip. The content follows the finger and springs back. */
@Composable
fun Modifier.swipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    threshold: Dp = 90.dp,
    tilt: Boolean = false,
): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val limit = with(LocalDensity.current) { threshold.toPx() }
    return this
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val v = offset.value
                    if (v <= -limit) next() else if (v >= limit) previous()
                    scope.launch {
                        offset.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, amount ->
                change.consume()
                scope.launch { offset.snapTo(offset.value + amount * 0.8f) }
            }
        }
        .graphicsLayer {
            translationX = offset.value
            if (tilt) rotationZ = offset.value / 50f
            alpha = 1f - (abs(offset.value) / (limit * 5f)).coerceIn(0f, 0.4f)
        }
}

/** Thin seek bar that thickens while you drag it. Tap anywhere to jump. */
@Composable
fun SeekBar(
    progress: () -> Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    var active by remember { mutableStateOf(false) }
    val thumbRadius by animateDpAsState(if (active) 9.dp else 6.dp, label = "thumb")
    val trackHeight by animateDpAsState(if (active) 6.dp else 4.dp, label = "track")
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                val inset = 10.dp.toPx()
                detectTapGestures { o ->
                    end(((o.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                val inset = 10.dp.toPx()
                var last = 0f
                fun fraction(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { o -> active = true; last = fraction(o.x); scrub(last) },
                    onDragEnd = { active = false; end(last) },
                    onDragCancel = { active = false; end(last) },
                ) { change, _ ->
                    change.consume()
                    last = fraction(change.position.x)
                    scrub(last)
                }
            }
    ) {
        val inset = 10.dp.toPx()
        val width = size.width - 2 * inset
        val y = size.height / 2
        val p = progress().coerceIn(0f, 1f)
        val stroke = trackHeight.toPx()
        drawLine(track, Offset(inset, y), Offset(inset + width, y), stroke, StrokeCap.Round)
        drawLine(accent, Offset(inset, y), Offset(inset + width * p, y), stroke, StrokeCap.Round)
        drawCircle(accent, thumbRadius.toPx(), Offset(inset + width * p, y))
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val shape = RoundedCornerShape(30)
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(20.dp, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(accent)
            .clickable(interactionSource = interaction, indication = ripple(color = onAccent), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "playPause",
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = onAccent,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

/** Floating bar above the content. Tap to open, swipe sideways to skip. */
@Composable
fun MiniPlayer(song: Song, vm: MusicViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.playerState.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val position = rememberPlaybackPosition(vm, state.isPlaying, song.id)
    val duration = state.duration.takeIf { it > 0 } ?: song.duration
    val container = accent.copy(alpha = 0.16f).compositeOver(PulseColors.SurfaceHigh)
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .height(66.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(container)
            .clickable(onClick = onOpen)
            .swipeToSkip(onNext = vm::next, onPrevious = vm::previous)
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.artUri, Modifier.size(48.dp), RoundedCornerShape(13.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = vm::next) {
                Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(28.dp))
            }
        }
        Canvas(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(2.dp)
        ) {
            val fraction = if (duration > 0) (position.longValue.toFloat() / duration).coerceIn(0f, 1f) else 0f
            drawRect(accent.copy(alpha = 0.18f))
            drawRect(accent, size = Size(size.width * fraction, size.height))
        }
    }
}
