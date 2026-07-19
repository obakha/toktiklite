package com.toktiklite

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.toktiklite.browser.BrowserChromeClient
import com.toktiklite.browser.BrowserWebViewClient
import com.toktiklite.browser.WebViewConfigurator
import com.toktiklite.download.DownloadHandler
import com.toktiklite.intent.IntentRouter
import com.toktiklite.permission.PermissionManager
import java.io.File

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusPage: LinearLayout
    private lateinit var statusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView

    private lateinit var intentRouter: IntentRouter
    private lateinit var permissionManager: PermissionManager

    private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraCaptureUri: Uri? = null
    private var lastLoadedUrl: String = Constants.HOME_URL

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileChooserCallback
            pendingFileChooserCallback = null
            if (callback == null) return@registerForActivityResult

            val data = result.data
            val uris: Array<Uri>? = when {
                result.resultCode != Activity.RESULT_OK -> null
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                pendingCameraCaptureUri != null -> arrayOf(pendingCameraCaptureUri!!)
                else -> null
            }
            callback.onReceiveValue(uris)
            pendingCameraCaptureUri = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() per the SplashScreen API contract.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        // Android 15+ (targetSdk 35+) enforces edge-to-edge; we opt in explicitly for all
        // supported versions so behavior is identical rather than version-dependent.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyEdgeToEdgeInsets()

        bindViews()

        intentRouter = IntentRouter(this)
        permissionManager = PermissionManager(this)

        setupWebView(savedInstanceState)
        setupSwipeToRefresh()
        setupBackNavigation()

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun bindViews() {
        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        statusPage = findViewById(R.id.statusPage)
        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusMessage = findViewById(R.id.statusMessage)
        findViewById<MaterialButton>(R.id.statusRetryButton).setOnClickListener {
            statusPage.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }
    }

    private fun applyEdgeToEdgeInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The WebView itself is left full-bleed (TikTok's own app draws under the system
            // bars), but the progress bar and the offline/error page - which have their own
            // opaque background - are padded so their content and controls stay reachable.
            progressBar.setPadding(0, bars.top, 0, 0)
            statusPage.setPadding(bars.left + 32, bars.top + 32, bars.right + 32, bars.bottom + 32)
            insets
        }
    }

    private fun setupWebView(savedInstanceState: Bundle?) {
        // Lets you inspect the real Network tab (status codes, CORS, redirects) via
        // chrome://inspect on a desktop with the device connected over USB debugging.
        // Gated on FLAG_DEBUGGABLE rather than a BuildConfig field so it never needs the
        // buildConfig feature enabled, and never turns on in a release build.
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        WebViewConfigurator.configure(webView)
        WebViewConfigurator.enableThirdPartyCookies(webView)

        webView.webViewClient = BrowserWebViewClient(intentRouter, object : BrowserWebViewClient.Callbacks {
            override fun onPageStarted(url: String) {
                lastLoadedUrl = url
                progressBar.visibility = View.VISIBLE
                statusPage.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onPageFinished(url: String) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }

            override fun onLoadError(errorCode: Int, description: String, failingUrl: String?) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                showStatusPage(isOffline = !isNetworkAvailable())
            }

            override fun onRendererCrashed(didCrash: Boolean): Boolean {
                return recreateWebViewAfterCrash(didCrash)
            }
        })

        webView.webChromeClient = BrowserChromeClient(permissionManager, object : BrowserChromeClient.Callbacks {
            override fun onProgressChanged(progress: Int) {
                progressBar.progress = progress
                if (progress >= 100) progressBar.visibility = View.GONE
            }

            override fun onShowFileChooser(
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                launchFileChooser(filePathCallback, fileChooserParams)
                return true
            }

            override fun onRequestNewWindow(resultMsg: Message): WebView = webView
        })

        webView.setDownloadListener(DownloadHandler(this))

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.brand_accent)
        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (val route = intentRouter.routeIntent(intent)) {
            is IntentRouter.Route.LoadInApp -> webView.loadUrl(route.url)
            is IntentRouter.Route.Handoff -> {
                // A non-TikTok deep link was somehow routed to us; forward it and finish only
                // if we have nothing else to show.
                intentRouter.handOff(route.uri)
                if (webView.url == null) webView.loadUrl(Constants.HOME_URL)
            }
            IntentRouter.Route.Home -> webView.loadUrl(Constants.HOME_URL)
        }
    }

    private fun showStatusPage(isOffline: Boolean) {
        webView.visibility = View.GONE
        statusPage.visibility = View.VISIBLE
        statusIcon.setImageResource(if (isOffline) R.drawable.ic_status_offline else R.drawable.ic_status_error)
        statusTitle.text = getString(if (isOffline) R.string.status_offline_title else R.string.status_error_title)
        statusMessage.text = getString(if (isOffline) R.string.status_offline_message else R.string.status_error_message)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Rebuilds the WebView in place after its renderer process is killed. We never reuse a
     * WebView instance whose render process is gone - Chromium documents that as unsafe - so we
     * tear the old one down and swap in a fresh, reconfigured instance restoring the last URL.
     */
    private fun recreateWebViewAfterCrash(didCrash: Boolean): Boolean {
        val parent = webView.parent as? SwipeRefreshLayout ?: return false
        val urlToRestore = lastLoadedUrl

        parent.removeView(webView)
        webView.destroy()

        val freshWebView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            id = R.id.webView
        }
        parent.addView(freshWebView)
        webView = freshWebView
        setupWebView(savedInstanceState = null)
        webView.loadUrl(urlToRestore)
        return true
    }

    private fun launchFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ) {
        pendingFileChooserCallback?.onReceiveValue(null)
        pendingFileChooserCallback = filePathCallback

        val contentSelectionIntent = fileChooserParams.createIntent().apply {
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        val initialIntents = mutableListOf<Intent>()
        createCameraCaptureIntent()?.let { initialIntents.add(it) }

        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
            putExtra(Intent.EXTRA_TITLE, "Upload")
        }
        fileChooserLauncher.launch(chooserIntent)
    }

    private fun createCameraCaptureIntent(): Intent? {
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (captureIntent.resolveActivity(packageManager) == null) return null

        val capturesDir = File(cacheDir, "captures").apply { mkdirs() }
        val captureFile = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
        val captureUri = FileProvider.getUriForFile(this, Constants.AUTHORITY_FILE_PROVIDER, captureFile)

        pendingCameraCaptureUri = captureUri
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
        captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return captureIntent
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        WebViewConfigurator.flushCookies()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            destroy()
        }
        super.onDestroy()
    }
}
