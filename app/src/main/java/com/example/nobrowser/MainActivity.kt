package com.example.nobrowser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvUrl: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton

    // Double-back-to-exit state
    private var backPressedOnce = false
    private val exitWindowMs = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        tvUrl = findViewById(R.id.tvUrl)
        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)

        setupWebView()
        setupPullToRefresh()
        setupAddressBarButtons()
        setupBackGestureHandling()

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launch mode routes new "open with" / "share" requests here
        // instead of spawning a second instance of the activity.
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                // Ordinary web navigation (including HTTP redirects,
                // meta-refresh, JS redirects) is left alone so the WebView
                // just follows the chain itself, like a normal browser tab.
                if (uri.scheme == "http" || uri.scheme == "https") {
                    return false
                }
                // Anything else (custom app schemes, intent:// links, etc.)
                // is an attempt to hand off to another app. Ask first.
                return handleExternalAppRedirect(uri)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { tvUrl.text = it }
                updateBackButtonState()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                url?.let { tvUrl.text = it }
                updateBackButtonState()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupPullToRefresh() {
        swipeRefresh.setOnRefreshListener {
            if (webView.url != null) {
                webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupAddressBarButtons() {
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        btnRefresh.setOnClickListener {
            if (webView.url != null) {
                swipeRefresh.isRefreshing = true
                webView.reload()
            }
        }
    }

    /**
     * Called when the page tries to navigate to a non-http(s) URL — almost
     * always a sign it wants to hand off to another app (a custom scheme,
     * or an `intent://...` link like TikTok/Instagram/etc. use). Rather
     * than silently failing (the old default WebView behavior) or silently
     * launching the other app, this asks the user first.
     */
    private fun handleExternalAppRedirect(uri: Uri): Boolean {
        val targetIntent: Intent? = try {
            if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        } catch (e: Exception) {
            null
        }

        if (targetIntent == null) {
            // Malformed / unparsable — nothing we can do with it.
            return true
        }

        val resolved = targetIntent.resolveActivity(packageManager)
        if (resolved == null) {
            // No installed app can handle it. intent:// links often carry a
            // browser_fallback_url for exactly this case — use it if present.
            val fallbackUrl = extractFallbackUrl(uri.toString())
            if (fallbackUrl != null) {
                webView.loadUrl(fallbackUrl)
            }
            return true
        }

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(resolved.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            resolved.packageName
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.open_in_app_title))
            .setMessage(getString(R.string.open_in_app_message, appLabel))
            .setPositiveButton(getString(R.string.open)) { _, _ ->
                try {
                    startActivity(targetIntent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, getString(R.string.could_not_open_app), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.stay), null)
            .show()

        return true
    }

    /** Extracts `S.browser_fallback_url=` from an intent:// URI string, if present. */
    private fun extractFallbackUrl(intentUriString: String): String? {
        val match = Regex("S\\.browser_fallback_url=([^;]+)").find(intentUriString) ?: return null
        return try {
            URLDecoder.decode(match.groupValues[1], "UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    private fun updateBackButtonState() {
        val canGoBack = webView.canGoBack()
        btnBack.isEnabled = canGoBack
        btnBack.alpha = if (canGoBack) 1.0f else 0.4f
    }

    /**
     * The system back gesture/button is intentionally NOT wired to page
     * history (that's what the dedicated back button in the address bar
     * is for). Instead it always means "leave the app," guarded by a
     * double-press-to-confirm so a single accidental swipe doesn't close it.
     */
    private fun setupBackGestureHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedOnce) {
                    finish()
                    return
                }
                backPressedOnce = true
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.press_back_again_to_exit),
                    Toast.LENGTH_SHORT
                ).show()
                webView.postDelayed({ backPressedOnce = false }, exitWindowMs)
            }
        })
    }

    private fun handleIncomingIntent(intent: Intent) {
        val url: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    extractFirstUrl(text)
                } else null
            }
            else -> null
        }

        if (url != null) {
            webView.loadUrl(url)
        } else if (webView.url == null) {
            // App launched directly (icon tap) with nothing to open yet.
            tvUrl.text = getString(R.string.no_link_provided)
        }
    }

    /** Shared text might be "check this out: https://..." rather than a bare URL. */
    private fun extractFirstUrl(text: String?): String? {
        if (text == null) return null
        val trimmed = text.trim()
        if (Uri.parse(trimmed).scheme != null) return trimmed
        val regex = Regex("""https?://\S+""")
        return regex.find(trimmed)?.value
    }
}
