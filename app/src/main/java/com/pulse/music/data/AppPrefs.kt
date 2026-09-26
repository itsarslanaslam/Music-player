package com.pulse.music.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SavedQueue(
    val ids: List<Long>,
    val currentId: Long,
    val position: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("pulse_prefs", Context.MODE_PRIVATE)

    private val _songSort = MutableStateFlow(
        SongSort.entries.getOrElse(sp.getInt(KEY_SORT, 0)) { SongSort.TITLE }
    )
    val songSort: StateFlow<SongSort> = _songSort.asStateFlow()

    fun setSongSort(sort: SongSort) {
        _songSort.value = sort
        sp.edit().putInt(KEY_SORT, sort.ordinal).apply()
    }

    fun saveQueue(queue: SavedQueue) {
        sp.edit()
            .putString(KEY_QUEUE, queue.ids.joinToString(","))
            .putLong(KEY_CURRENT, queue.currentId)
            .putLong(KEY_POSITION, queue.position)
            .putBoolean(KEY_SHUFFLE, queue.shuffle)
            .putInt(KEY_REPEAT, queue.repeatMode)
            .apply()
    }

    fun loadQueue(): SavedQueue? {
        val raw = sp.getString(KEY_QUEUE, null) ?: return null
        val ids = raw.split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return SavedQueue(
            ids = ids,
            currentId = sp.getLong(KEY_CURRENT, -1),
            position = sp.getLong(KEY_POSITION, 0),
            shuffle = sp.getBoolean(KEY_SHUFFLE, false),
            repeatMode = sp.getInt(KEY_REPEAT, 0),
        )
    }

    var eqEnabled: Boolean
        get() = sp.getBoolean(KEY_EQ_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_EQ_ENABLED, value).apply()

    var eqPreset: Int
        get() = sp.getInt(KEY_EQ_PRESET, -1)
        set(value) = sp.edit().putInt(KEY_EQ_PRESET, value).apply()

    var eqBands: List<Int>?
        get() = sp.getString(KEY_EQ_BANDS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        set(value) = sp.edit().putString(KEY_EQ_BANDS, value?.joinToString(",")).apply()

    var bassStrength: Int
        get() = sp.getInt(KEY_BASS, 0)
        set(value) = sp.edit().putInt(KEY_BASS, value).apply()

    private companion object {
        const val KEY_SORT = "song_sort"
        const val KEY_QUEUE = "queue_ids"
        const val KEY_CURRENT = "queue_current"
        const val KEY_POSITION = "queue_position"
        const val KEY_SHUFFLE = "queue_shuffle"
        const val KEY_REPEAT = "queue_repeat"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_EQ_BANDS = "eq_bands"
        const val KEY_BASS = "eq_bass"
    }
}
