package com.example.lecteur

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    private var startUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startUri = intent?.data
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { PlayerScreen(startUri) }
            }
        }
    }
}

private fun nameOf(ctx: Context, uri: Uri): String =
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "Média"

private fun toItem(ctx: Context, uri: Uri) = MediaItem.Builder()
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(nameOf(ctx, uri)).build())
    .build()

@Composable
fun PlayerScreen(startUri: Uri?) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var items by remember { mutableStateOf(listOf<MediaItem>()) }
    var current by remember { mutableIntStateOf(-1) }
    var speed by remember { mutableFloatStateOf(1f) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(m: MediaItem?, reason: Int) {
                current = player.currentMediaItemIndex
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    fun load(list: List<MediaItem>) {
        items = list
        player.setMediaItems(list)
        player.prepare()
        player.playWhenReady = true
        current = 0
    }

    LaunchedEffect(startUri) { startUri?.let { load(listOf(toItem(ctx, it))) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            load(uris.map { toItem(ctx, it) })
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 4000 } }
        )
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }) { Text("Ouvrir") }
            OutlinedButton(onClick = {
                val steps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                speed = steps[(steps.indexOf(speed) + 1) % steps.size]
                player.setPlaybackSpeed(speed)
            }) { Text("Vitesse ${speed}x") }
            OutlinedButton(onClick = {
                player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_OFF)
                    Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }) { Text("Boucle") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
            itemsIndexed(items) { i, it ->
                Text(
                    text = it.mediaMetadata.title?.toString() ?: "Média",
                    color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable {
                        player.seekTo(i, 0); player.play(); current = i
                    }.padding(12.dp)
                )
            }
        }
    }
}
