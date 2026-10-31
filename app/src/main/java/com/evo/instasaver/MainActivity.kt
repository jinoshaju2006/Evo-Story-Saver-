package com.evo.instasaver

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF090912)
private val Panel = Color(0xFF191827)
private val Violet = Color(0xFF9B6BFF)

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
    val context = LocalContext.current
    var link by remember { mutableStateOf(shared) }
    var message by remember { mutableStateOf("Paste a direct media file URL to begin.") }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF21133B), Night)))
            .padding(22.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(28.dp))
        Text("EVO", color = Violet, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Text("INSTA SAVER", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Save media you have permission to keep.", color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(34.dp))
        Text("MEDIA LINK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
            BasicTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                decorationBox = { inner ->
                    Box {
                        if (link.isEmpty()) Text("Paste a direct image/video URL…", color = Color.Gray)
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { queueDownload(context, link) { message = it } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                shape = RoundedCornerShape(14.dp)
            ) { Text("DOWNLOAD", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color(0xFFD6CDEA), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("HOW IT WORKS", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "1. Paste a direct media-file URL.\n2. Tap Download.\n3. Find the file in Downloads.",
            color = Color.LightGray, lineHeight = 22.sp, fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Instagram post, Reel and Story page links require a supported authorised media resolver. This starter app does not bypass private-account access or Instagram restrictions.",
            color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

private fun queueDownload(context: Context, raw: String, update: (String) -> Unit) {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull()
    if (uri == null || uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) {
        update("Enter a valid http/https media URL.")
        return
    }
    if (uri.host?.contains("instagram.com", ignoreCase = true) == true) {
        update("Instagram page URLs need an authorised media resolver; direct page downloading is not supported.")
        return
    }
    val fileName = uri.lastPathSegment?.takeIf { it.contains('.') }
        ?: "evo_media_${System.currentTimeMillis()}.bin"
    runCatching {
        val request = DownloadManager.Request(uri)
            .setTitle("EVO Insta Saver")
            .setDescription("Downloading media")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }.onSuccess {
        update("Download queued. Check your Downloads folder.")
    }.onFailure {
        update("Download failed: ${it.localizedMessage ?: "unknown error"}")
    }
}
