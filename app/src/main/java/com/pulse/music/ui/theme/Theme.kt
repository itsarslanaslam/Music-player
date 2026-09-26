package com.pulse.music.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PulseColors {
    val Background = Color(0xFF0D0F14)
    val Surface = Color(0xFF151821)
    val SurfaceHigh = Color(0xFF1D212C)
    val SurfaceHighest = Color(0xFF272B37)
    val OnSurface = Color(0xFFF1F2F6)
    val Muted = Color(0xFF8B90A0)
    val Line = Color(0xFF2A2E3A)
    val DefaultAccent = Color(0xFF8B7CFF)
}

private val base = Typography()

val PulseTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(letterSpacing = 0.1.sp),
    bodyMedium = base.bodyMedium.copy(letterSpacing = 0.1.sp),
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

@Composable
fun PulseTheme(accent: Color, content: @Composable () -> Unit) {
    val onAccent = if (accent.luminance() > 0.4f) PulseColors.Background else Color.White
    val tint = accent.copy(alpha = 0.18f).compositeOver(PulseColors.Surface)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = tint,
        onPrimaryContainer = PulseColors.OnSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = tint,
        onSecondaryContainer = PulseColors.OnSurface,
        background = PulseColors.Background,
        onBackground = PulseColors.OnSurface,
        surface = PulseColors.Background,
        onSurface = PulseColors.OnSurface,
        surfaceVariant = PulseColors.SurfaceHigh,
        onSurfaceVariant = PulseColors.Muted,
        surfaceContainerLowest = PulseColors.Background,
        surfaceContainerLow = PulseColors.Surface,
        surfaceContainer = PulseColors.Surface,
        surfaceContainerHigh = PulseColors.SurfaceHigh,
        surfaceContainerHighest = PulseColors.SurfaceHighest,
        outline = PulseColors.Line,
        outlineVariant = PulseColors.Line,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = PulseTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

private val accentCache = LruCache<Uri, Int>(128)

/**
 * The accent color pulled from the current song's album art. The value only changes
 * once per song so the theme doesn't recompose every frame; screens animate it locally.
 */
@Composable
fun rememberArtworkAccent(uri: Uri?): State<Color> {
    val context = LocalContext.current
    val accent = remember { mutableStateOf(PulseColors.DefaultAccent) }
    LaunchedEffect(uri) {
        accent.value = uri?.let { extractAccent(context, it) } ?: PulseColors.DefaultAccent
    }
    return accent
}

private suspend fun extractAccent(context: Context, uri: Uri): Color? {
    accentCache.get(uri)?.let { return Color(it) }
    val request = ImageRequest.Builder(context).data(uri).size(96).allowHardware(false).build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
    val argb = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
            ?: palette.mutedSwatch ?: return@withContext null
        tuneForDarkUi(swatch.rgb)
    } ?: return null
    accentCache.put(uri, argb)
    return Color(argb)
}

/** Keeps the hue but makes sure the color reads well on a near-black background. */
private fun tuneForDarkUi(rgb: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    if (hsl[1] >= 0.12f) hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.74f)
    return ColorUtils.HSLToColor(hsl)
}
