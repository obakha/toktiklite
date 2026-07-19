package com.toktiklite.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.toktiklite.Constants

/**
 * Applies every WebView/WebSettings/CookieManager configuration decision in one place, with
 * the reasoning for each setting documented inline. Nothing here is TikTok-specific beyond the
 * user agent string; everything else is "what a general-purpose, security-conscious WebView
 * wrapper for a modern web app needs."
 */
object WebViewConfigurator {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        val settings = webView.settings

        // --- Required for the site to function at all ---
        // TikTok's web app is a JS-heavy SPA; without this it is a blank page.
        settings.javaScriptEnabled = true
        // Backs localStorage/sessionStorage, which TikTok uses for client-side state.
        settings.domStorageEnabled = true
        // NOTE: setDatabaseEnabled/getDatabaseEnabled are intentionally NOT called. They only
        // ever controlled WebSQL, which Chrome removed and Android deprecated as a no-op
        // starting with Android 15. Calling it does nothing and would only be misleading here.

        // --- Playback ---
        // TikTok video previews autoplay muted; requiring a gesture would break the feed.
        settings.mediaPlaybackRequiresUserGesture = false

        // --- Rendering / viewport ---
        // TikTok serves a responsive mobile layout; these two make it render as a phone browser
        // would rather than a shrunk desktop page.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

        // --- Caching / performance ---
        // Let WebView decide based on cache-control headers rather than forcing a mode; this
        // avoids both unnecessary re-fetches and stale-content bugs.
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // --- Security ---
        // The app never loads local HTML, so file:// access brings no benefit and only risk.
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // We only ever navigate to https:// origins (see IntentRouter); block http fallbacks
        // and any accidental mixed-content requests from third-party embeds.
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(true) // required for onCreateWindow (OAuth popups)
        settings.javaScriptCanOpenWindowsAutomatically = true // paired with the above; TikTok's
        // login flow opens the Google/Apple OAuth popup via window.open()
        settings.setSafeBrowsingEnabled(true)

        // --- User agent ---
        // Force a modern, generic mobile Chrome UA so tiktok.com serves its standard mobile web
        // experience instead of a reduced "unsupported browser" fallback. We append to the
        // system-provided WebView UA rather than fabricating one from scratch, so the underlying
        // Chromium/WebView version stays accurate for TikTok's feature detection.
        settings.userAgentString = buildUserAgent(settings.userAgentString)

        configureCookies()
    }

    private fun buildUserAgent(existing: String): String {
        // Strip any "wv" WebView marker some OEM builds include; TikTok's browser detection
        // treats a bare "wv" token as a signal to block/limit the session.
        val withoutWvMarker = existing.replace("; wv", "").replace(" wv", "")
        return if (withoutWvMarker.contains("Mobile Safari")) {
            withoutWvMarker
        } else {
            "$withoutWvMarker${Constants.USER_AGENT_SUFFIX_TEMPLATE}"
        }
    }

    /**
     * Enables persistent, first- and third-party cookies so a TikTok login survives an app
     * restart. Third-party cookies are required because the OAuth handoff (Google/Apple sign-in)
     * happens on a different origin than tiktok.com before redirecting back.
     */
    private fun configureCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
    }

    fun enableThirdPartyCookies(webView: WebView) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    /** Persists cookies to disk immediately; call from onPause/onStop, not just on a timer. */
    fun flushCookies() {
        CookieManager.getInstance().flush()
    }
}
