package com.pulse.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.music.ui.components.IconTile
import com.pulse.music.ui.theme.PulseColors
import com.pulse.music.util.audioPermission
import com.pulse.music.util.permissionsToRequest

@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var denials by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[audioPermission] == true) onGranted() else denials++
    }
    val accent = MaterialTheme.colorScheme.primary
    val blocked = denials >= 2

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(
            Icons.Rounded.LibraryMusic,
            Modifier.size(112.dp),
            CircleShape,
            iconSize = 52.dp,
            brush = Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), PulseColors.Surface)),
        )
        Spacer(Modifier.height(28.dp))
        Text("Your music, front and center", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            if (blocked) "Access is turned off. Open settings, tap Permissions, and allow Music and audio."
            else "Pulse plays the songs saved on this phone. Allow access to your audio files to build your library.",
            style = MaterialTheme.typography.bodyLarge,
            color = PulseColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (blocked) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                } else {
                    launcher.launch(permissionsToRequest())
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(if (blocked) "Open settings" else "Allow access")
        }
    }
}
