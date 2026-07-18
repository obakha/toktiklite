package com.toktiklite

import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
            val embedUrl = toEmbedUrl(url) ?: url
            webView.loadUrl(embedUrl)
        } else {
            // No URL provided, load homepage
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
                
                // Check if it's a TikTok URL
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

    /**
     * Converts a TikTok video URL to its embeddable form.
     * Returns null if the URL is already an embed URL or not a video URL
     * (so the WebView can load it as-is, e.g. CDN assets or short-link redirects).
     */
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
        // Matches: www.tiktok.com/@user/video/1234567890
        //          tiktok.com/@user/video/1234567890
        //          m.tiktok.com/v/1234567890.html
        private val VIDEO_URL_REGEX = Regex(
            """tiktok\.com/(?:@[^/]+/video|v)/(\d+)"""
        )
    }
}
