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
            when {
                // Handle TikTok URLs
                url.contains("tiktok.com") -> {
                    val embedUrl = toEmbedUrl(url) ?: url
                    webView.loadUrl(embedUrl)
                }
                // Handle deep links - redirect to TikTok web
                url.startsWith("snssdk") || url.startsWith("onelink") -> {
                    handleDeepLink(url)
                }
                // Handle other URLs
                else -> webView.loadUrl(url)
            }
        } else {
            // No URL provided, load homepage
            webView.loadUrl("https://www.tiktok.com")
        }
    }

    private fun handleDeepLink(url: String) {
        try {
            val uri = Uri.parse(url)
            
            // Extract search keyword if it's a search deep link
            val keyword = uri.getQueryParameter("keyword")
            if (!keyword.isNullOrEmpty()) {
                val searchUrl = "https://www.tiktok.com/search?q=$keyword"
                webView.loadUrl(searchUrl)
                return
            }
            
            // Extract the actual domain_source to redirect
            val domainSource = uri.getQueryParameter("domain_source")
            if (domainSource == "tiktok") {
                // Try to extract the actual URL from af_dp parameter
                val afDp = uri.getQueryParameter("af_dp")
                if (!afDp.isNullOrEmpty()) {
                    // Deep link contains snssdk:// protocol, redirect to web version
                    webView.loadUrl("https://www.tiktok.com")
                    return
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
                
                when {
                    // Handle TikTok URLs
                    url.contains("tiktok.com") -> {
                        val embedUrl = toEmbedUrl(url)
                        if (embedUrl != null) {
                            view.loadUrl(embedUrl)
                            return true
                        }
                    }
                    // Handle deep links
                    url.startsWith("snssdk") || url.startsWith("onelink") -> {
                        handleDeepLink(url)
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
     * Returns null if the URL is already an embed URL or not a video URL.
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
        private val VIDEO_URL_REGEX = Regex(
            """tiktok\.com/(?:@[^/]+/video|v)/(\d+)"""
        )
    }
}
