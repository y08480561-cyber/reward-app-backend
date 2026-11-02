package com.example.ui.screens

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*

// WARNING: This URL exposes the admin users table. Ensure appropriate backend authorization is in place.
const val ADMIN_USERS_URL = "https://reward-app-backend-a9ze.onrender.com/admin/users"

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(NavyPrimary)) {
        // Header
        Surface(modifier = Modifier.fillMaxWidth().shadow(6.dp), color = NavySecondary) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                }
                Text("مدیریت کاربران (ادمین)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                hasError = false
                            }
                            override fun onPageFinished(view: WebView?, url: String?) { isLoading = false }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) { hasError = true; isLoading = false }
                            }
                        }
                        loadUrl(ADMIN_USERS_URL)
                        webViewInstance = this
                    }
                },
                update = { webViewInstance = it }
            )

            if (isLoading && !hasError) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = GoldAccent)
            }
            if (hasError) {
                Text("خطا در بارگذاری صفحه", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
