package com.pulse.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.music.playback.EqBand
import com.pulse.music.ui.LocalActions
import com.pulse.music.ui.LocalBottomPadding
import com.pulse.music.ui.MusicViewModel
import com.pulse.music.ui.components.EmptyState
import com.pulse.music.ui.theme.PulseColors
import java.util.Locale

@Composable
fun EqualizerScreen(vm: MusicViewModel) {
    val eq by vm.eqState.collectAsStateWithLifecycle()
    val actions = LocalActions.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Equalizer") },
            navigationIcon = {
                IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            },
            actions = {
                Switch(
                    checked = eq.enabled,
                    onCheckedChange = vm::setEqEnabled,
                    enabled = eq.available,
                )
                Spacer(Modifier.width(16.dp))
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        if (!eq.available) {
            EmptyState(
                icon = Icons.Rounded.GraphicEq,
                title = "Equalizer not ready",
                message = "Play a song first. If it still doesn't appear, this device doesn't expose audio effects to apps.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomPadding.current),
        ) {
            if (eq.presets.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = eq.preset < 0,
                                onClick = {},
                                enabled = eq.enabled,
                                label = { Text("Custom") },
                            )
                        }
                        itemsIndexed(eq.presets) { index, name ->
                            FilterChip(
                                selected = eq.preset == index,
                                onClick = { vm.usePreset(index) },
                                enabled = eq.enabled,
                                label = { Text(name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            items(eq.bands, key = { it.index }) { band ->
                BandSlider(
                    band = band,
                    min = eq.minLevel,
                    max = eq.maxLevel,
                    enabled = eq.enabled,
                    onChange = { vm.setBandLevel(band.index, it) },
                )
            }

            if (eq.bassSupported) {
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = PulseColors.Line)
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bass boost", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${eq.bass / 10}%", style = MaterialTheme.typography.labelLarge, color = PulseColors.Muted)
                        }
                        Slider(
                            value = eq.bass.toFloat(),
                            onValueChange = { vm.setBass(it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = eq.enabled,
                        )
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = vm::resetEq, enabled = eq.enabled) { Text("Reset to flat") }
                }
            }
        }
    }
}

@Composable
private fun BandSlider(band: EqBand, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatHz(band.centerHz),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = band.level.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatDb(band.level),
            style = MaterialTheme.typography.labelMedium,
            color = PulseColors.Muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

private fun formatHz(hz: Int): String =
    if (hz >= 1000) String.format(Locale.US, "%.1f kHz", hz / 1000f).replace(".0 ", " ") else "$hz Hz"

private fun formatDb(milliBel: Int): String {
    val db = milliBel / 100f
    return String.format(Locale.US, "%+.1f dB", db)
}
