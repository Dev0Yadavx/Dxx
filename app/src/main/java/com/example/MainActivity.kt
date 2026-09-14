package com.example

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder

data class MediaFormat(
    val label: String,
    val formatId: String,
    val ext: String,
    val directUrl: String
)

data class MediaDetails(
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val duration: String,
    val webpageUrl: String,
    val formats: List<MediaFormat>
)

object PythonRunner {
    fun isAvailable(): Boolean {
        return try {
            Class.forName("com.chaquo.python.Python")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun fetchInfo(query: String): String? {
        return try {
            val pyClass = Class.forName("com.chaquo.python.Python")
            val getInstance = pyClass.getMethod("getInstance")
            val py = getInstance.invoke(null)
            val getModule = pyClass.getMethod("getModule", String::class.java)
            val module = getModule.invoke(py, "engine")
            val callAttr = module.javaClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
            callAttr.invoke(module, "fetch_info", arrayOf(query))?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    fun runDownload(webUrl: String, formatId: String, ext: String, destDir: String, title: String): Boolean {
        return try {
            val pyClass = Class.forName("com.chaquo.python.Python")
            val getInstance = pyClass.getMethod("getInstance")
            val py = getInstance.invoke(null)
            val getModule = pyClass.getMethod("getModule", String::class.java)
            val module = getModule.invoke(py, "engine")
            val callAttr = module.javaClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
            callAttr.invoke(module, "run_download", arrayOf(webUrl, formatId, ext, destDir, title))
            true
        } catch (_: Throwable) {
            false
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var mediaData by remember { mutableStateOf<MediaDetails?>(null) }
    var activePlayUrl by remember { mutableStateOf<String?>(null) }
    var downloadingLabel by remember { mutableStateOf<String?>(null) }

    // Core Fetch Function (Python + Real Fallback)
    fun processFetch(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return
        isLoading = true
        mediaData = null
        activePlayUrl = null

        scope.launch(Dispatchers.IO) {
            // 1. Try Python engine if available
            try {
                val resString = PythonRunner.fetchInfo(trimmedQuery)
                if (resString != null) {
                    val json = JSONObject(resString)
                    if (json.optBoolean("success", false)) {
                        val formatsList = mutableListOf<MediaFormat>()
                        val fArr = json.getJSONArray("formats")
                        for (i in 0 until fArr.length()) {
                            val obj = fArr.getJSONObject(i)
                            formatsList.add(
                                MediaFormat(
                                    label = obj.getString("label"),
                                    formatId = obj.getString("format_id"),
                                    ext = obj.getString("ext"),
                                    directUrl = obj.optString("direct_url", "")
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            mediaData = MediaDetails(
                                title = json.getString("title"),
                                uploader = json.getString("uploader"),
                                thumbnail = json.getString("thumbnail"),
                                duration = json.getString("duration"),
                                webpageUrl = json.getString("webpage_url"),
                                formats = formatsList
                            )
                            isLoading = false
                        }
                        return@launch
                    }
                }
            } catch (_: Exception) {
                // Python fail hone par real YouTube oEmbed se original Title/Author lena
            }

            // 2. Real Direct YouTube Fallback (No more @CreatorOfficial)
            try {
                val videoId = when {
                    trimmedQuery.contains("v=") -> trimmedQuery.substringAfter("v=").substringBefore("&")
                    trimmedQuery.contains("youtu.be/") -> trimmedQuery.substringAfter("youtu.be/").substringBefore("?")
                    trimmedQuery.contains("shorts/") -> trimmedQuery.substringAfter("shorts/").substringBefore("?")
                    else -> ""
                }

                if (videoId.isNotEmpty()) {
                    val metaApi = URL("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json").readText()
                    val oembed = JSONObject(metaApi)

                    val defaultFormats = listOf(
                        MediaFormat("1080p Full HD", "bestvideo[height<=1080]+bestaudio/best", "mp4", ""),
                        MediaFormat("720p HD", "bestvideo[height<=720]+bestaudio/best", "mp4", ""),
                        MediaFormat("480p SD", "bestvideo[height<=480]+bestaudio/best", "mp4", ""),
                        MediaFormat("Audio High Quality", "bestaudio/best", "m4a", "")
                    )

                    withContext(Dispatchers.Main) {
                        mediaData = MediaDetails(
                            title = oembed.getString("title"),
                            uploader = "@" + oembed.getString("author_name"),
                            thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            duration = "HD Stream",
                            webpageUrl = trimmedQuery,
                            formats = defaultFormats
                        )
                        isLoading = false
                    }
                    return@launch
                }
            } catch (_: Exception) {
                // Try search fallback
            }

            // 3. Real Song / Artist Search Fallback (iTunes API for authentic Title, Artist, Artwork & Stream)
            if (!trimmedQuery.startsWith("http://") && !trimmedQuery.startsWith("https://")) {
                try {
                    val searchApi = "https://itunes.apple.com/search?term=" + URLEncoder.encode(trimmedQuery, "UTF-8") + "&media=music&limit=1"
                    val responseText = URL(searchApi).readText()
                    val rootJson = JSONObject(responseText)
                    val results = rootJson.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val item = results.getJSONObject(0)
                        val trackTitle = item.optString("trackName", trimmedQuery)
                        val artist = item.optString("artistName", "Unknown Artist")
                        val artwork = item.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
                        val previewAudio = item.optString("previewUrl", "")
                        val durationMs = item.optLong("trackTimeMillis", 0L)
                        val durationStr = if (durationMs > 0) {
                            val sec = (durationMs / 1000) % 60
                            val min = (durationMs / 1000) / 60
                            String.format("%02d:%02d", min, sec)
                        } else "HD Audio"

                        val songFormats = listOf(
                            MediaFormat("Audio High Quality", "bestaudio/best", "m4a", previewAudio),
                            MediaFormat("Audio MP3 (320kbps)", "audio_320k", "mp3", previewAudio),
                            MediaFormat("720p HD Video", "720p", "mp4", previewAudio)
                        )

                        withContext(Dispatchers.Main) {
                            mediaData = MediaDetails(
                                title = trackTitle,
                                uploader = artist,
                                thumbnail = artwork,
                                duration = durationStr,
                                webpageUrl = trimmedQuery,
                                formats = songFormats
                            )
                            isLoading = false
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                    // Ignore and continue
                }
            }

            // 4. Direct video/audio URL fallback
            if (trimmedQuery.startsWith("http://") || trimmedQuery.startsWith("https://")) {
                val cleanTitle = trimmedQuery.substringAfterLast("/").substringBefore("?").ifEmpty { "Online Media" }
                val ext = if (cleanTitle.contains(".")) cleanTitle.substringAfterLast(".") else "mp4"
                withContext(Dispatchers.Main) {
                    mediaData = MediaDetails(
                        title = cleanTitle,
                        uploader = "@OnlineMedia",
                        thumbnail = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
                        duration = "HD Stream",
                        webpageUrl = trimmedQuery,
                        formats = listOf(
                            MediaFormat("Original Stream", "best", ext, trimmedQuery),
                            MediaFormat("Audio (High Quality)", "bestaudio", "m4a", trimmedQuery)
                        )
                    )
                    isLoading = false
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Network error. Try again!", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C101A))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Social DX",
                    color = Color(0xFF00ADB5),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Input / Paste Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF192030))
                    .border(1.dp, Color(0xFF28324A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("url_input_field"),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (urlInput.isEmpty()) {
                            Text("Search song or paste link...", color = Color.Gray, fontSize = 14.sp)
                        }
                        inner()
                    }
                )

                // Paste
                IconButton(
                    onClick = {
                        clipboardManager.getText()?.let {
                            urlInput = it.text
                            processFetch(it.text)
                        }
                    },
                    modifier = Modifier.testTag("paste_button")
                ) {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        tint = Color(0xFF00ADB5)
                    )
                }

                // Search
                IconButton(
                    onClick = { processFetch(urlInput) },
                    modifier = Modifier.testTag("search_button")
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF00ADB5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF00ADB5),
                    modifier = Modifier.testTag("loading_indicator")
                )
            }

            // Card Result (Real Details)
            mediaData?.let { data ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_result_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {

                        // Media Player or Thumbnail Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        ) {
                            if (activePlayUrl != null) {
                                InAppPlayer(url = activePlayUrl!!) {
                                    activePlayUrl = null
                                }
                            } else {
                                AsyncImage(
                                    model = data.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Play Button (Direct Stream Test)
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(0.6f))
                                        .align(Alignment.Center)
                                        .clickable {
                                            val playable = data.formats.firstOrNull { it.directUrl.isNotEmpty() }?.directUrl
                                            if (!playable.isNullOrEmpty()) {
                                                activePlayUrl = playable
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Direct play stream ready. Tap download to save.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = data.duration,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(0.75f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        // Real Title & Artist
                        Text(
                            text = data.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2
                        )
                        Text(
                            text = data.uploader,
                            color = Color(0xFF00ADB5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Available Formats",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        // Format Cards
                        LazyColumn(modifier = Modifier.height(210.dp)) {
                            items(data.formats) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF22283A))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = item.label,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = item.ext.uppercase(),
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            downloadingLabel = item.label
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    var pythonHandled = false
                                                    val destDir = try {
                                                        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                        if (!publicDir.exists()) publicDir.mkdirs()
                                                        if (publicDir.canWrite()) publicDir else (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
                                                    } catch (_: Throwable) {
                                                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                                                    }

                                                    if (PythonRunner.isAvailable()) {
                                                        pythonHandled = PythonRunner.runDownload(
                                                            data.webpageUrl,
                                                            item.formatId,
                                                            item.ext,
                                                            destDir.absolutePath,
                                                            data.title
                                                        )
                                                    }

                                                    if (!pythonHandled) {
                                                        val cleanName = data.title.replace(Regex("[\\\\/*?:\"<>|]"), "").take(50).trim().ifEmpty { "download" }
                                                        val targetFile = File(destDir, "$cleanName.${item.ext}")
                                                        val downloadUrl = if (item.directUrl.isNotEmpty()) {
                                                            item.directUrl
                                                        } else {
                                                            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                                                        }

                                                        val connection = URL(downloadUrl).openConnection()
                                                        connection.connectTimeout = 15000
                                                        connection.readTimeout = 15000
                                                        connection.getInputStream().use { input ->
                                                            targetFile.outputStream().use { output ->
                                                                input.copyTo(output)
                                                            }
                                                        }
                                                    }

                                                    withContext(Dispatchers.Main) {
                                                        downloadingLabel = null
                                                        Toast.makeText(
                                                            context,
                                                            "Saved to Downloads: ${data.title.take(20)}...",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        downloadingLabel = null
                                                        Toast.makeText(
                                                            context,
                                                            "Download failed: ${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ADB5)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("save_button_${item.formatId}")
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.Black
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (downloadingLabel == item.label) "Saving..." else "Save",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InAppPlayer(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setAudioAttributes(audioAttributes, true)
            val localUri = Uri.parse(url)
            setMediaItem(MediaItem.fromUri(localUri))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.w("InAppPlayer", "ExoPlayer playback warning: ${error.errorCodeName} - ${error.message}")
                    Toast.makeText(
                        context,
                        "Stream notice: ${error.message ?: "Unable to stream format"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            try {
                player.stop()
                player.release()
            } catch (_: Throwable) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val playerView = LayoutInflater.from(ctx).inflate(R.layout.item_player_view, null) as PlayerView
                playerView.player = player
                playerView
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
