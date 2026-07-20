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
                    // NOTE: this used to be `if (!('userAgentData' in navigator)) return;` at
                    // the top, which would bail out of the ENTIRE script - including the CSS
                    // rules below - on any WebView build that doesn't expose Client Hints. Each
                    // piece is now independently guarded so one missing API can't silently
                    // disable everything else.
                    if ('userAgentData' in navigator) {
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
                    }

                    // --- Selector-independent readability baseline ---
                    // Deliberately does NOT target any TikTok class name: their CSS classes are
                    // build-hashed (e.g. "components.6ec42283.css") and reshuffle on every
                    // deploy, so anything keyed to a specific class breaks on their next release.
                    // These rules only touch document-level, tag-level, and standard-attribute
                    // selectors, which are far more stable.
                    var style = document.createElement('style');
                    style.setAttribute('data-toktiklite', 'baseline');
                    style.textContent = [
                        // (Previously forced overflow-x: hidden here to avoid a horizontal
                        // scroll gutter. Removed: it was clipping legitimate wider content
                        // - the side icons, comments, repost button - instead of leaving it
                        // reachable via scroll/pinch-zoom, which is what real "Request Desktop
                        // Site" mode does in an actual browser. Zoom controls are already
                        // enabled below in WebSettings for exactly this reason.
                        // Desktop hover-only affordances (tooltips, hover-reveal menus) are dead
                        // weight on a touchscreen; this is a tag/attribute-level, not class-level,
                        // best-effort softening and safe to leave in even where it does nothing.
                        '* { -webkit-tap-highlight-color: transparent; }',
                        // Videos and images should never overflow their column on the shrunk
                        // desktop layout, regardless of what class TikTok gives them this week.
                        'video, img { max-width: 100%; height: auto; }',

                        // --- Targeted rules, confirmed against an actual rendered-DOM capture ---
                        // TikTok's build hashes the class-name PREFIX on every deploy, but the
                        // "--ComponentName" suffix comes straight from their styled-components
                        // source and only changes when they rename the component itself - a
                        // substring match on that suffix survives deploys the exact class would not.
                        //
                        // Right-side "related videos" panel on the video-detail page: pure bonus
                        // content, nothing lost by hiding it, and it frees real width back to the
                        // actual player/content column on a narrow screen.
                        '[class*="RightPanelContainer"] { display: none !important; }',
                        // Desktop footer link columns (About/Careers/legal/etc.): irrelevant
                        // clutter in an app that only ever shows one video/feed at a time.
                        // Two different naming schemes show up across TikTok's own page bundles
                        // (video-detail vs. homepage), so both are covered.
                        '[class*="DivFooterContainer"], [class*="SubMainNavFooterContainer"], [class*="FooterRoot"], [class*="FooterContainer"], [class*="StyledFooterItemLink"] { display: none !important; }',
                        // The floating "Open App" nag button confirmed on the homepage
                        // (DivOpenTikTokButtonWrapper / ButtonCTAOpenApp) - this is almost
                        // certainly the animated tap-prompt button. We ARE the app already, so
                        // this has zero function for us; hiding it outright, not just muting
                        // the animation. Also hides the small "open app" icon badge next to the
                        // Discover nav link (data-e2e is TikTok's own stable QA hook, safer than
                        // any class name for this one).
                        '[class*="DivOpenTikTokButtonWrapper"], [class*="ButtonCTAOpenApp"], [data-e2e="open-titok-icon"] { display: none !important; }'
                    ].join('\\n');
                    document.documentElement.appendChild(style);

                    // --- Auto-fit content width to the actual device screen ---
                    // TikTok's desktop layout doesn't shrink itself for a phone-width screen
                    // (that's the whole reason we removed overflow-x:hidden - the extra width
                    // is real content, not a bug). Rather than requiring a manual pinch-zoom on
                    // every single page, measure how much wider the real content is than the
                    // visible screen and shrink it by exactly that ratio automatically. Chromium's
                    // (non-standard, but fully supported in WebView) `zoom` CSS property is used
                    // instead of `transform: scale()` because it recalculates layout/scroll size
                    // at the new scale, rather than just visually stretching a fixed-size box.
                    var MIN_SCALE = 0.3, MAX_SCALE = 1.0;
                    var fitPending = false, fitDebounce = null;

                    function fitContentToScreen() {
                        if (fitPending) return;
                        fitPending = true;
                        requestAnimationFrame(function() {
                            fitPending = false;
                            var root = document.documentElement;
                            // Reset before measuring - scrollWidth would otherwise reflect our
                            // own previous zoom instead of the page's true, unscaled width.
                            root.style.zoom = '1';
                            var naturalWidth = root.scrollWidth;
                            var viewportWidth = window.innerWidth;
                            if (!naturalWidth || naturalWidth <= viewportWidth) {
                                root.style.zoom = '1';
                                return;
                            }
                            var scale = viewportWidth / naturalWidth;
                            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
                            root.style.zoom = String(scale);
                        });
                    }

                    function scheduleFit() {
                        clearTimeout(fitDebounce);
                        // TikTok's SPA mutates the DOM constantly (view counters, timers,
                        // animations); debouncing avoids re-measuring on every single one of
                        // those and only reacts once things settle after a real change (route
                        // navigation, content swap).
                        fitDebounce = setTimeout(fitContentToScreen, 400);
                    }

                    window.addEventListener('load', scheduleFit);
                    window.addEventListener('resize', scheduleFit);
                    if ('ResizeObserver' in window) {
                        new ResizeObserver(scheduleFit).observe(document.documentElement);
                    }
                    var mutationObserver = new MutationObserver(scheduleFit);
                    function startObserving() {
                        mutationObserver.observe(document.body || document.documentElement, {
                            childList: true,
                            subtree: false // shallow: route-level swaps only, not every leaf update
                        });
                    }
                    if (document.body) {
                        startObserving();
                    } else {
                        document.addEventListener('DOMContentLoaded', startObserving);
                    }
                    scheduleFit();
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
