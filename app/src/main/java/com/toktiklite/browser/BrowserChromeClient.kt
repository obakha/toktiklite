package com.toktiklite.browser

import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.toktiklite.permission.PermissionManager

/**
 * Handles everything that isn't page navigation: load progress, native permission bridging
 * (camera/mic for TikTok's live & duet features, geolocation for location tagging), the
 * upload file chooser, and popup windows opened by TikTok's OAuth login flow.
 */
class BrowserChromeClient(
    private val permissionManager: PermissionManager,
    private val callbacks: Callbacks
) : WebChromeClient() {

    interface Callbacks {
        fun onProgressChanged(progress: Int)
        fun onShowFileChooser(
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean

        /** Return the WebView that should host a popup window, or null to block it. */
        fun onRequestNewWindow(resultMsg: android.os.Message): WebView?
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callbacks.onProgressChanged(newProgress)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        permissionManager.handleWebViewPermissionRequest(request)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        permissionManager.handleGeolocationPermissionRequest(origin, callback)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return callbacks.onShowFileChooser(filePathCallback, fileChooserParams)
    }

    /**
     * TikTok's web login (Google / Apple OAuth) calls `window.open()`, which requires the host
     * app to supply a WebView for the popup and transport the result back via [resultMsg]. We
     * reuse the caller's own WebView (single-window browsing) rather than spawning a second
     * visible window, which keeps the UI simple while still letting the popup's navigation and
     * eventual `window.close()` complete the login.
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message
    ): Boolean {
        val target = callbacks.onRequestNewWindow(resultMsg) ?: return false
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = target
        resultMsg.sendToTarget()
        return true
    }
}
