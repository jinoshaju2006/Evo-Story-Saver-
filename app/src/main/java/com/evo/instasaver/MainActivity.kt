package com.evo.instasaver

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private val Night = Color(0xFF07070D)
private val Panel = Color(0x24FFFFFF)
private val Violet = Color(0xFFFF2848)
private val GlassBorder = Color(0x55FFFFFF)
private val worker = Executors.newSingleThreadExecutor()
private val mainHandler = Handler(Looper.getMainLooper())

data class MediaItem(val type: String, val url: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = if (intent?.action == Intent.ACTION_SEND)
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { SaverHome(shared) }
    }
}

@Composable
private fun SaverHome(shared: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("evo_saver", Context.MODE_PRIVATE) }
    var link by remember { mutableStateOf(shared) }
    var apiKey by remember { mutableStateOf(prefs.getString("gis_key", "") ?: "") }
    var message by remember { mutableStateOf("Enter your API key once, then paste a public Instagram link.") }
    var loading by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listOf<MediaItem>()) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF291023), Color(0xFF11101D), Night)))
            .padding(22.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).background(Color(0x26FFFFFF), RoundedCornerShape(16.dp))
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) { Text("EVO", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("EVO", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text("INSTA SAVER", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("FROSTED • FAST • PRIVATE KEY STORAGE", color = Color(0xFFBDB7C8), fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(22.dp))
        Text("GETINSAVER API KEY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = apiKey,
            onValueChange = { apiKey = it; prefs.edit().putString("gis_key", it.trim()).apply() },
            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                .padding(15.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            visualTransformation = PasswordVisualTransformation(),
            decorationBox = { inner -> Box { if (apiKey.isEmpty()) Text("gis_live_…", color = Color.Gray); inner() } }
        )
        Spacer(Modifier.height(16.dp))
        Text("INSTAGRAM LINK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = link,
            onValueChange = { link = it; items = emptyList() },
            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp))
                .padding(15.dp).heightIn(min = 74.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            decorationBox = { inner -> Box { if (link.isEmpty()) Text("Paste a public post, Reel or Story URL…", color = Color.Gray); inner() } }
        )
        Spacer(Modifier.height(14.dp))
        Button(
            enabled = !loading,
            onClick = {
                val cleanLink = link.trim()
                val cleanKey = apiKey.trim()
                if (!cleanKey.startsWith("gis_live_")) {
                    message = "Enter your valid GetInSaver API key."
                } else if (!cleanLink.startsWith("https://www.instagram.com/") &&
                    !cleanLink.startsWith("https://instagram.com/") &&
                    !cleanLink.startsWith("http://www.instagram.com/") &&
                    !cleanLink.startsWith("http://instagram.com/")) {
                    message = "Paste a valid Instagram link."
                } else {
                    loading = true
                    items = emptyList()
                    message = "Resolving media… this can take up to 2 minutes."
                    worker.execute {
                        try {
                            val service = when {
                                cleanLink.contains("/stories/highlights/") -> "instagram-highlight"
                                cleanLink.contains("/stories/") -> "instagram-story"
                                else -> "instagram"
                            }
                            val conn = (URL("https://developers.getinsaver.com/v1/resolve").openConnection() as HttpURLConnection)
                            conn.requestMethod = "POST"
                            conn.connectTimeout = 20000
                            conn.readTimeout = 135000
                            conn.doOutput = true
                            conn.setRequestProperty("Authorization", "Bearer $cleanKey")
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.outputStream.use {
                                it.write(JSONObject().put("service", service).put("url", cleanLink).put("max_results", 10).toString().toByteArray())
                            }
                            val code = conn.responseCode
                            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                                ?.bufferedReader()?.use { it.readText() }.orEmpty()
                            conn.disconnect()
                            val json = JSONObject(body)
                            if (code !in 200..299) {
                                throw Exception(json.optString("message", json.optString("error", "API error ($code)")))
                            }
                            val arr = json.optJSONArray("items")
                            val found = mutableListOf<MediaItem>()
                            if (arr != null) for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val mediaUrl = obj.optString("url")
                                val type = obj.optString("type")
                                if (mediaUrl.startsWith("https://") && type in listOf("video", "image", "audio")) {
                                    found.add(MediaItem(type, mediaUrl))
                                }
                            }
                            mainHandler.post {
                                loading = false
                                items = found
                                message = if (found.isEmpty()) "No downloadable media found. The post may be private or unsupported."
                                    else "Found ${found.size} media file(s). Choose what to save."
                            }
                        } catch (e: Exception) {
                            mainHandler.post {
                                loading = false
                                message = "Could not resolve: ${e.message ?: "Please try again."}"
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E42)),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (loading) "PLEASE WAIT…" else "FIND MEDIA", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(14.dp))
        Text(message, color = Color(0xFFD6CDEA), fontSize = 13.sp)
        items.forEachIndexed { index, item ->
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { downloadMedia(context, item, index) { message = it } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x553A3448))
            ) { Text("DOWNLOAD ${item.type.uppercase()} ${index + 1}", color = Color.White) }
        }
        Spacer(Modifier.weight(1f))
        Text("HOW TO USE", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "1. Add your GetInSaver API key.\n2. Paste a public Instagram post or Reel link.\n3. Tap Find Media, then Download.",
            color = Color.LightGray, lineHeight = 21.sp, fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Your API key is stored privately on this device and is not included in the app source. Private/login-only content cannot be accessed.",
            color = Color.Gray, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}

private fun downloadMedia(context: Context, item: MediaItem, index: Int, update: (String) -> Unit) {
    runCatching {
        val uri = Uri.parse(item.url)
        val ext = when (item.type) { "video" -> ".mp4"; "image" -> ".jpg"; "audio" -> ".mp3"; else -> ".bin" }
        val pathName = uri.lastPathSegment?.substringBefore("?")?.takeIf { it.contains(".") }
        val fileName = pathName ?: "evo_instagram_${System.currentTimeMillis()}_${index + 1}$ext"
        val request = DownloadManager.Request(uri)
            .setTitle("EVO Insta Saver")
            .setDescription("Saving Instagram media")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }.onSuccess { update("Download started. Check your Downloads folder.") }
        .onFailure { update("Download failed: ${it.localizedMessage ?: "unknown error"}") }
}
