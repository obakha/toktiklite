package com.toktiklite.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.toktiklite.Constants

/**
 * Decides what should happen with a [Uri] the app is asked to open, whether that Uri arrives
 * as the launching [Intent] (from another app / the launcher) or as in-page navigation inside
 * the WebView (from [com.toktiklite.browser.BrowserWebViewClient]).
 *
 * This class deliberately does NOT rewrite TikTok web URLs (no embed conversion, no path
 * rewriting). Its only jobs are:
 *  1. Normalize non-http(s) TikTok schemes (`tiktok://`, `snssdk://`) into the equivalent
 *     `https://www.tiktok.com/...` URL, since WebView cannot load a custom scheme directly.
 *  2. Classify a Uri as "load inside our WebView" vs "hand off to the system" based on host,
 *     never based on URL shape/content.
 */
class IntentRouter(private val context: Context) {

    sealed class Route {
        /** Load this exact URL in the app's WebView, unmodified. */
        data class LoadInApp(val url: String) : Route()

        /** Not ours to render; send it to whatever app the system resolves it to. */
        data class Handoff(val uri: Uri) : Route()

        /** Nothing usable was found (e.g. a MAIN launch with no data URI). */
        object Home : Route()
    }

    /** Entry point for [android.app.Activity.onCreate] / [android.app.Activity.onNewIntent]. */
    fun routeIntent(intent: Intent?): Route {
        val data = intent?.data ?: return Route.Home
        return routeUri(data)
    }

    /** Entry point for in-page navigation decisions inside the WebView. */
    fun routeUri(uri: Uri): Route {
        val normalized = normalizeNativeScheme(uri) ?: uri

        val scheme = normalized.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            // Some other app-specific scheme we don't understand (e.g. mailto:, intent:).
            return Route.Handoff(normalized)
        }

        val host = normalized.host?.lowercase().orEmpty()
        val isInAppHost = Constants.IN_APP_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }

        return if (isInAppHost) {
            Route.LoadInApp(normalized.toString())
        } else {
            Route.Handoff(normalized)
        }
    }

    /**
     * Converts `tiktok://` and `snssdk://` deep links into the `https://www.tiktok.com/...`
     * equivalent. The official app registers paths like `tiktok://video/1234567890` or
     * `snssdk1233://aweme/detail/1234567890`; we fall back to the TikTok homepage when a
     * path can't be confidently mapped, rather than guessing.
     */
    private fun normalizeNativeScheme(uri: Uri): Uri? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val isNativeScheme = Constants.NATIVE_SCHEMES.any { scheme == it || scheme.startsWith(it) }
        if (!isNativeScheme) return null

        val segments = uri.pathSegments
        val videoId = segments.lastOrNull { it.toLongOrNull() != null }

        return if (videoId != null) {
            Uri.parse("https://www.tiktok.com/@_/video/$videoId")
        } else {
            Uri.parse(Constants.HOME_URL)
        }
    }

    /**
     * Hands a Uri off to the system (an external browser, the real TikTok app if the user has
     * one, a mail client, etc.). Never used for TikTok web content.
     */
    fun handOff(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
        }
    }
}
