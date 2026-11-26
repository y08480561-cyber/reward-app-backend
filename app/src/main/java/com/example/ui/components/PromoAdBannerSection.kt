package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.model.PromoAdBanner
import com.example.model.PromoBannerUrlValidator
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.LuxuryBorder
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarmWhite
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromoAdBannerSection(
    banners: List<PromoAdBanner>,
    onBannerVisible: (String) -> Unit,
    onBannerClick: (PromoAdBanner) -> Unit,
    modifier: Modifier = Modifier,
    placement: String? = null,
    clockOffsetMs: Long = 0L
) {
    val effectiveNowMs = remember(banners, clockOffsetMs) {
        System.currentTimeMillis() + clockOffsetMs
    }
    val activeBanners = remember(banners, effectiveNowMs, placement) {
        PromoBannerUrlValidator.filterAndSortActive(banners, nowMs = effectiveNowMs, placement = placement)
    }
    if (activeBanners.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("promo_ad_banners_section"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (activeBanners.size == 1) {
            val single = activeBanners.first()
            LaunchedEffect(single.id) {
                onBannerVisible(single.id)
            }
            PromoAdBannerCard(
                banner = single,
                onClick = { onBannerClick(single) }
            )
        } else {
            val pagerState = rememberPagerState(pageCount = { activeBanners.size })
            val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

            LaunchedEffect(pagerState.settledPage, activeBanners) {
                val visibleBanner = activeBanners.getOrNull(pagerState.settledPage)
                if (visibleBanner != null) {
                    onBannerVisible(visibleBanner.id)
                }
            }

            LaunchedEffect(activeBanners.size, isDragged) {
                if (!isDragged && activeBanners.size > 1) {
                    while (true) {
                        delay(5000L)
                        val nextPage = (pagerState.currentPage + 1) % activeBanners.size
                        pagerState.animateScrollToPage(nextPage)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("promo_ad_banners_pager")
            ) { page ->
                val banner = activeBanners[page]
                PromoAdBannerCard(
                    banner = banner,
                    onClick = { onBannerClick(banner) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(activeBanners.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (selected) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) GoldAccent else TextMuted.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PromoAdBannerCard(
    banner: PromoAdBanner,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var retryAttempt by remember(banner.id, banner.imageUrl, banner.updatedAtMs) { mutableIntStateOf(0) }
    var imageAspectRatio by remember(banner.id, banner.imageUrl, banner.updatedAtMs) { mutableFloatStateOf(16f / 9f) }

    val imageRequest = remember(banner.id, banner.imageUrl, banner.updatedAtMs, retryAttempt) {
        ImageRequest.Builder(context)
            .data(banner.imageUrl)
            .memoryCacheKey("${banner.id}_${banner.updatedAtMs}_$retryAttempt")
            .diskCacheKey("${banner.id}_${banner.updatedAtMs}")
            .apply {
                if (retryAttempt > 0) {
                    memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    diskCachePolicy(CachePolicy.WRITE_ONLY)
                }
            }
            .crossfade(true)
            .listener(
                onSuccess = { _, result ->
                    val w = result.drawable.intrinsicWidth
                    val h = result.drawable.intrinsicHeight
                    if (w > 0 && h > 0) {
                        imageAspectRatio = (w.toFloat() / h.toFloat()).coerceIn(1.35f, 2.8f)
                    }
                },
                onError = { _, err ->
                    Log.w(
                        "PromoAdBanner",
                        "Failed loading banner image (${banner.id}) url=${banner.imageUrl}: ${err.throwable.message}"
                    )
                }
            )
            .build()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("promo_ad_banner_card_${banner.id}"),
        shape = RoundedCornerShape(18.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, LuxuryBorder)
    ) {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = banner.title.ifBlank { "پیشنهاد ویژه" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CharcoalSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    LaunchedEffect(banner.id, banner.imageUrl, retryAttempt) {
                        if (retryAttempt < 1) {
                            delay(1500L)
                            retryAttempt++
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CharcoalSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = banner.title.ifBlank { "مشاهده پیشنهاد ویژه" },
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBannerBrowserDialog(
    url: String,
    onOpenExternal: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val safeInitialUrl = remember(url) { PromoBannerUrlValidator.normalizeSafeHttpUrl(url) }
    if (safeInitialUrl == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var isLoading by remember { mutableStateOf(true) }
    var currentTitle by remember { mutableStateOf("مشاهده صفحه") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "بستن مرورگر",
                                tint = WarmWhite
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = WarmWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "مرورگر امن داخلی",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onOpenExternal(safeInitialUrl) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "باز کردن در مرورگر خارجی",
                                tint = GoldLight
                            )
                        }
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = GoldAccent,
                        trackColor = CharcoalSurface
                    )
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val nextUrl = request?.url?.toString()
                                    val safeNext = PromoBannerUrlValidator.normalizeSafeHttpUrl(nextUrl)
                                    return safeNext == null
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    pageUrl: String?,
                                    favicon: Bitmap?
                                ) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    isLoading = false
                                    val pageTitle = view?.title?.trim().orEmpty()
                                    if (pageTitle.isNotEmpty() && !pageTitle.startsWith("http", ignoreCase = true)) {
                                        currentTitle = pageTitle
                                    }
                                }
                            }
                            loadUrl(safeInitialUrl)
                        }
                    }
                )
            }
        }
    }
}
