package com.rifsxd.ksunext.ui.webui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.webkit.WebViewAssetLoader
import com.topjohnwu.superuser.Shell
import com.rifsxd.ksunext.ui.util.createRootShell
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {
    private lateinit var webviewInterface: WebViewInterface

    private var rootShell: Shell? = null

    fun erudaConsole(context: android.content.Context): String {
        return context.assets.open("eruda.min.js").bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        val moduleId = intent.getStringExtra("id")!!
        val name = intent.getStringExtra("name")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("WebUI-Next | $name"))
        } else {
            val taskDescription = ActivityManager.TaskDescription.Builder().setLabel("WebUI-Next | $name").build()
            setTaskDescription(taskDescription)
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val developerOptionsEnabled = prefs.getBoolean("enable_developer_options", false)
        val enableWebDebugging = prefs.getBoolean("enable_web_debugging", false)

        WebView.setWebContentsDebuggingEnabled(developerOptionsEnabled && enableWebDebugging)

        val moduleDir = "/data/adb/modules/${moduleId}"
        val webRoot = File("${moduleDir}/webroot")
        val rootShell = createRootShell(true).also { this.rootShell = it }
        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain("mui.kernelsu.org")
            .addPathHandler(
                "/",
                SuFilePathHandler(this, webRoot, rootShell)
            )
            .build()

        val webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return webViewAssetLoader.shouldInterceptRequest(request.url)
            }
        }

        val webView = WebView(this).apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<MarginLayoutParams> {
                    leftMargin = inset.left
                    rightMargin = inset.right
                    topMargin = inset.top
                    bottomMargin = inset.bottom
                }
                return@setOnApplyWindowInsetsListener insets
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webviewInterface = WebViewInterface(this@WebUIActivity, this, moduleDir)
            addJavascriptInterface(webviewInterface, "ksu")
            setWebViewClient(object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return webViewAssetLoader.shouldInterceptRequest(request.url)
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (developerOptionsEnabled && enableWebDebugging) {
                        view?.evaluateJavascript(
                            erudaConsole(this@WebUIActivity),
                            null
                        )
                        view?.evaluateJavascript("eruda.init();", null)
                    }
                }
            })
            loadUrl("https://mui.kernelsu.org/index.html")
        }

        setContentView(webView)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { rootShell?.close() }
    }
}