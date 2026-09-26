package com.pulse.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.runtime.Immutable
import com.pulse.music.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class EqBand(val index: Int, val centerHz: Int, val level: Int)

@Immutable
data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val preset: Int = -1,
    val bassSupported: Boolean = false,
    val bass: Int = 0,
)

/** Wraps the platform Equalizer and BassBoost effects bound to the player's audio session. */
class EqualizerManager(private val prefs: AppPrefs) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _state = MutableStateFlow(EqState(enabled = prefs.eqEnabled))
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        val enabled = prefs.eqEnabled
        equalizer = runCatching {
            Equalizer(0, audioSessionId).apply {
                val preset = prefs.eqPreset
                val saved = prefs.eqBands
                if (preset in 0 until numberOfPresets) {
                    usePreset(preset.toShort())
                } else if (saved != null && saved.size == numberOfBands.toInt()) {
                    saved.forEachIndexed { i, level -> setBandLevel(i.toShort(), level.toShort()) }
                }
                setEnabled(enabled)
            }
        }.getOrNull()
        bassBoost = runCatching {
            BassBoost(0, audioSessionId).apply {
                if (strengthSupported) setStrength(prefs.bassStrength.toShort())
                setEnabled(enabled)
            }
        }.getOrNull()
        publish()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        _state.update { it.copy(available = false) }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.eqEnabled = enabled
        runCatching { equalizer?.setEnabled(enabled) }
        runCatching { bassBoost?.setEnabled(enabled) }
        _state.update { it.copy(enabled = enabled) }
    }

    fun setBandLevel(index: Int, level: Int) {
        val eq = equalizer ?: return
        runCatching { eq.setBandLevel(index.toShort(), level.toShort()) }
        _state.update { s ->
            s.copy(preset = -1, bands = s.bands.map { if (it.index == index) it.copy(level = level) else it })
        }
        prefs.eqPreset = -1
        prefs.eqBands = _state.value.bands.map { it.level }
    }

    fun usePreset(preset: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(preset.toShort()) }
        prefs.eqPreset = preset
        publish()
        prefs.eqBands = _state.value.bands.map { it.level }
    }

    fun setBass(strength: Int) {
        runCatching { bassBoost?.setStrength(strength.toShort()) }
        prefs.bassStrength = strength
        _state.update { it.copy(bass = strength) }
    }

    fun reset() {
        val eq = equalizer ?: return
        _state.value.bands.forEach { runCatching { eq.setBandLevel(it.index.toShort(), 0) } }
        prefs.eqPreset = -1
        prefs.eqBands = null
        setBass(0)
        publish()
    }

    private fun publish() {
        val eq = equalizer
        if (eq == null) {
            _state.value = EqState(available = false, enabled = prefs.eqEnabled)
            return
        }
        _state.value = runCatching {
            val range = eq.bandLevelRange
            EqState(
                available = true,
                enabled = prefs.eqEnabled,
                bands = (0 until eq.numberOfBands).map { i ->
                    EqBand(i, eq.getCenterFreq(i.toShort()) / 1000, eq.getBandLevel(i.toShort()).toInt())
                },
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                preset = prefs.eqPreset,
                bassSupported = bassBoost?.strengthSupported == true,
                bass = prefs.bassStrength,
            )
        }.getOrElse { EqState(available = false, enabled = prefs.eqEnabled) }
    }
}
