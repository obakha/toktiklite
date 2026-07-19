package com.toktiklite.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.toktiklite.Constants

/**
 * Applies every WebView/WebSettings/CookieManager configuration decision in one place, with
 * the reasoning for each setting documented inline.
 *
 * IMPORTANT CONTEXT: this configures the WebView to request TikTok's *desktop* site rather
 * than its mobile site, deliberately. TikTok's mobile web experience is a crippled funnel
 * toward the native app (muted/non-interactive preview, a looping "open app" prompt, no
 * comments) - this is confirmed, intentional product behavior, not something specific to
 * WebViews, and it's the same behavior you'd see in any mobile browser hitting tiktok.com
 * without switching to "Desktop site" mode. The desktop site is fully functional because
 * there's no competing native app to funnel desktop users toward. This mirrors exactly what
 * "Request Desktop Site" does in Chrome/Safari on a phone - same trick, same reason it works.
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
        settings.mediaPlaybackRequiresUserGesture = false

        // --- Rendering / viewport ---
        // Desktop sites are typically NOT touch/small-screen optimized (TikTok's own desktop
        // CSS assumes a mouse: hover states, fixed-width layout). useWideViewPort + overview
        // mode shrinks that wide layout to fit the screen initially, and enabling zoom controls
        // lets the user pinch-zoom the same way they would with "Desktop site" mode in a real
        // mobile browser - this is expected, not a bug, for a desktop-rendered page on a phone.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false // pinch-to-zoom only, no on-screen +/- buttons

        // --- Caching / performance ---
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // --- Security ---
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(true) // required for onCreateWindow (OAuth popups)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSafeBrowsingEnabled(true)

        // --- User agent: impersonate desktop Chrome on Linux ---
        // This is the exact UA shape Chrome's own "Request Desktop Site" sends (confirmed via
        // Chrome DevTools network capture): no "Android", no "Mobile" token, no device model.
        // TikTok's mobile-vs-desktop split is keyed primarily off this string.
        settings.userAgentString =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/${Constants.CHROME_MAJOR_VERSION} Safari/537.36"

        // Some sites also (or instead) check the newer User-Agent Client Hints API
        // (navigator.userAgentData) rather than parsing the UA string. Real desktop Chrome
        // reports userAgentData.mobile === false and platform "Linux". Because WebView still
        // generates these based on the actual OS underneath, we patch them via a script that
        // runs before any page script (addDocumentStartJavaScript), the same mechanism
        // ad-blockers and privacy browsers use for this exact class of override.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                """
                (function() {
                    if (!('userAgentData' in navigator)) return;
                    try {
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    mobile: false,
                                    platform: 'Linux',
                                    brands: [
                                        { brand: 'Not?A_Brand', version: '8' },
                                        { brand: 'Chromium', version: '${Constants.CHROME_MAJOR_VERSION.substringBefore('.')}' },
                                        { brand: 'Google Chrome', version: '${Constants.CHROME_MAJOR_VERSION.substringBefore('.')}' }
                                    ],
                                    getHighEntropyValues: function(hints) {
                                        return Promise.resolve({
                                            mobile: false,
                                            platform: 'Linux',
                                            platformVersion: '6.5.0'
                                        });
                                    }
                                };
                            }
                        });
                    } catch (e) { /* best-effort only; never break page load over this */ }
                })();
                """.trimIndent(),
                setOf("https://*.tiktok.com", "https://*.tiktokcdn.com", "https://*.tiktokv.com")
            )
        }

        configureCookies()
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
