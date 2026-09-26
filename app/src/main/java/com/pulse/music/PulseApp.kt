package com.pulse.music

import android.app.Application
import android.content.Context
import com.pulse.music.data.AppDatabase
import com.pulse.music.data.AppPrefs
import com.pulse.music.data.MusicRepository
import com.pulse.music.playback.EqualizerManager
import com.pulse.music.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PulseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container. Lives as long as the process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = AppPrefs(app)
    val database = AppDatabase.create(app)
    val repository = MusicRepository(app, scope)
    val equalizer = EqualizerManager(prefs)
    val player = PlayerController(app, scope, repository, prefs)
}

val Context.container: AppContainer
    get() = (applicationContext as PulseApp).container
