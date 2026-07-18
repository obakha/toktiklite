package com.toktiklite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        configureWebView()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.data?.toString()
        if (url != null) {
            loadUrlSafely(url)
        } else {
            webView.loadUrl("https://www.tiktok.com")
        }
    }

    private fun loadUrlSafely(url: String) {
        when {
            url.startsWith("snssdk") -> handleDeepLink(url)
            url.startsWith("onelink") -> handleDeepLink(url)
            url.contains("tiktok.com") -> {
                val embedUrl = toEmbedUrl(url) ?: url
                webView.loadUrl(embedUrl)
            }
            else -> webView.loadUrl(url)
        }
    }

    private fun handleDeepLink(url: String) {
        try {
            val uri = Uri.parse(url)
            
            // Extract search keyword
            val keyword = uri.getQueryParameter("keyword")
            if (!keyword.isNullOrEmpty()) {
                webView.loadUrl("https://www.tiktok.com/search?q=$keyword")
                return
            }
            
            // Handle user profile deep links (snssdk://user/profile/...)
            if (url.contains("/user/profile/")) {
                val profileId = url.substringAfterLast("/").takeWhile { it.isDigit() }
                if (profileId.isNotEmpty()) {
                    webView.loadUrl("https://www.tiktok.com/@user$profileId")
                    return
                }
            }
            
            // Handle video deep links (snssdk://video/...)
            if (url.contains("/video/")) {
                val videoId = url.substringAfterLast("/").takeWhile { it.isDigit() }
                if (videoId.isNotEmpty()) {
                    val embedUrl = "https://www.tiktok.com/embed/v2/$videoId"
                    webView.loadUrl(embedUrl)
                    return
                }
            }
            
            // Try to extract params_url (the actual TikTok web URL)
            val paramsUrl = uri.getQueryParameter("params_url")
            if (!paramsUrl.isNullOrEmpty()) {
                try {
                    val decodedUrl = java.net.URLDecoder.decode(paramsUrl, "UTF-8")
                    if (decodedUrl.contains("tiktok.com")) {
                        webView.loadUrl(decodedUrl)
                        return
                    }
                } catch (e: Exception) {
                    // Continue to fallback
                }
            }
            
            // Fallback to homepage
            webView.loadUrl("https://www.tiktok.com")
        } catch (e: Exception) {
            webView.loadUrl("https://www.tiktok.com")
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                
                // Intercept deep links before WebView tries to load them
                if (url.startsWith("snssdk") || url.startsWith("onelink")) {
                    handleDeepLink(url)
                    return true
                }
                
                // Handle TikTok URLs
                if (url.contains("tiktok.com")) {
                    val embedUrl = toEmbedUrl(url)
                    if (embedUrl != null) {
                        view.loadUrl(embedUrl)
                        return true
                    }
                }
                
                // Let WebView handle other URLs normally
                return false
            }
        }
    }

    private fun toEmbedUrl(url: String): String? {
        if (url.contains("tiktok.com/embed/")) return null

        val match = VIDEO_URL_REGEX.find(url) ?: return null
        val videoId = match.groupValues[1]
        return "https://www.tiktok.com/embed/v2/$videoId"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private val VIDEO_URL_REGEX = Regex(
            """tiktok\.com/(?:@[^/]+/video|v)/(\d+)"""
        )
    }
}
