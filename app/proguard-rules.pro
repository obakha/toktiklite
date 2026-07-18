# Keep the app's own classes; WebView uses reflection to invoke some callback interfaces
# (DownloadListener, WebChromeClient overrides) and the JS bridge, if one is ever added.
-keep class com.toktiklite.** { *; }

# Standard WebView JavaScript-interface keep rule (defensive; no @JavascriptInterface is
# currently used, but this avoids a silent break if one is added later).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn org.jetbrains.annotations.**
