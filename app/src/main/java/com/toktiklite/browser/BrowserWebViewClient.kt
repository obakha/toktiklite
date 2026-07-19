package com.toktiklite.browser

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.toktiklite.intent.IntentRouter

/**
 * Every navigation decision, error state, and crash-recovery hook for the main WebView goes
 * through this client. It never rewrites TikTok URLs; it only decides in-app vs. hand-off via
 * [IntentRouter], and reports state changes to [callbacks] so the Activity can drive UI
 * (progress, offline page, error page).
 */
class BrowserWebViewClient(
    private val intentRouter: IntentRouter,
    private val callbacks: Callbacks
) : WebViewClient() {

    interface Callbacks {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onLoadError(errorCode: Int, description: String, failingUrl: String?)
        fun onRendererCrashed(didCrash: Boolean): Boolean
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val route = intentRouter.routeUri(request.url)
        return when (route) {
            is IntentRouter.Route.LoadInApp -> {
                when {
                    route.url == request.url.toString() -> {
                        // Ordinary https:// navigation, nothing to rewrite; let WebView load it
                        // directly so referrer/POST-data/back-stack semantics stay normal.
                        false
                    }
                    request.isForMainFrame -> {
                        // A real native-scheme navigation (tiktok://, snssdk://, versioned
                        // variants like snssdk1233://) on the visible page. WebView cannot fetch
                        // a non-http(s) scheme itself - returning false would make it try to load
                        // the *original* request URL and fail immediately with
                        // net::ERR_UNKNOWN_URL_SCHEME. We take over and load the rewritten URL.
                        view.loadUrl(route.url)
                        true
                    }
                    else -> {
                        // Same native-scheme rewrite, but in a sub-frame - almost always one of
                        // TikTok's hidden "silently try to wake the native app" probe iframes
                        // (visible in the wild as gd_label=click_wap_silence_awaken). There's no
                        // visible content to recover for an invisible frame, and loading a full
                        // page into it would be wasted work (a second video player instance,
                        // etc.), so we just swallow the navigation the same way a real native
                        // app's absence would in an ordinary browser.
                        true
                    }
                }
            }
            is IntentRouter.Route.Handoff -> {
                intentRouter.handOff(route.uri)
                true
            }
            IntentRouter.Route.Home -> false
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        callbacks.onPageStarted(url.orEmpty())
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        WebViewConfigurator.flushCookies()
        callbacks.onPageFinished(url.orEmpty())
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // Only surface an error page for the top-level document failing to load; a failing
        // sub-resource (an ad pixel, a font, a beacon) shouldn't blank out a working page.
        if (request.isForMainFrame) {
            callbacks.onLoadError(error.errorCode, error.description?.toString().orEmpty(), request.url?.toString())
        }
    }

    /**
     * We do NOT blindly proceed past SSL errors. TikTok is always served over valid, trusted
     * certificates; a cert error here almost always means a captive portal or a
     * man-in-the-middle proxy, and silently accepting it would be a serious security regression.
     * We cancel the load either way.
     *
     * This older WebViewClient overload doesn't tell us whether the failing resource is the
     * top-level document or one of the many sub-resources a TikTok page loads (video segments,
     * CDN thumbnails, ad/analytics beacons) - there is no WebResourceRequest here, just a bare
     * SslError. Comparing the failing URL against the page's own URL is the closest available
     * heuristic for "was this the main navigation". Without it, a single cert hiccup on any
     * background resource would blank out an otherwise working page.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        val isMainDocument = error.url == view.url
        if (isMainDocument) {
            callbacks.onLoadError(-1, "A secure connection could not be established.", error.url)
        }
    }

    /**
     * Chromium's renderer process can be killed by the OS under memory pressure independently
     * of the app process. Returning true here tells the system we handled it (by rebuilding the
     * WebView in the Activity) instead of letting the whole app crash.
     */
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        return callbacks.onRendererCrashed(detail.didCrash())
    }
}
