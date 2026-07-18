package com.toktiklite

/**
 * Central, immutable configuration for the browser. Keeping every TikTok-specific
 * string in one place means the routing/security logic in [com.toktiklite.intent.IntentRouter]
 * and [com.toktiklite.browser.BrowserWebViewClient] never hardcodes a host or scheme itself.
 */
object Constants {

    /** Home page shown when the app is launched without a link (e.g. from the launcher icon). */
    const val HOME_URL = "https://www.tiktok.com/"

    /**
     * Hosts that are allowed to load *inside* the WebView. This is intentionally broader than
     * just "tiktok.com" because the real tiktok.com web app depends on a constellation of
     * first-party and partner domains for assets, auth, and captcha challenges. Everything not
     * on this list is handed off to the system (external browser / other apps) instead of being
     * silently loaded in-app, so we never become an open-ended web browser.
     */
    val IN_APP_HOST_SUFFIXES: List<String> = listOf(
        "tiktok.com",
        "tiktokcdn.com",
        "tiktokcdn-us.com",
        "tiktokv.com",
        "ibytedtos.com",
        "ibyteimg.com",
        "byteoversea.com",
        "musical.ly",
        "bytedance.com",
        // Common OAuth / login providers TikTok's web login flow can redirect through.
        "accounts.google.com",
        "appleid.apple.com",
        "facebook.com",
        "twitter.com",
        "x.com"
    )

    /** Hosts (and their subdomains) that carry TikTok content and should be intercepted from other apps. */
    val DEEP_LINK_HOSTS: List<String> = listOf(
        "www.tiktok.com",
        "tiktok.com",
        "vm.tiktok.com",
        "vt.tiktok.com",
        "m.tiktok.com"
    )

    /** Native schemes the official TikTok app registers; we normalize these into web URLs. */
    val NATIVE_SCHEMES: List<String> = listOf("tiktok", "snssdk")

    /**
     * A realistic, current mobile Chrome UA. TikTok's web app serves a materially different
     * (and more limited) experience to UAs it can't recognize as a modern mobile browser.
     * This must be updated periodically as Chrome's version increments; it is centralized here
     * for that reason.
     */
    const val USER_AGENT_SUFFIX_TEMPLATE =
        " Mobile Safari/537.36"

    const val CHROME_MAJOR_VERSION = "126.0.6478.122"

    /** Directory (relative to the public Downloads collection) videos are saved into. */
    const val DOWNLOAD_SUBDIRECTORY = "TokTikLite"

    const val AUTHORITY_FILE_PROVIDER = "com.toktiklite.fileprovider"
}
