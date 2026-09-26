package com.pulse.music

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pulse.music.ui.AppRoot
import com.pulse.music.util.hasAudioPermission

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        container.player.connect()
        if (hasAudioPermission(this)) container.repository.start()
        setContent { AppRoot() }
    }

    override fun onStop() {
        super.onStop()
        container.player.persist()
    }
}
