package com.mcbot.manager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var splash: LinearLayout
    private lateinit var splashText: TextView
    private lateinit var splashBar: ProgressBar
    private lateinit var splashCopy: Button
    private lateinit var splashClose: Button

    @Volatile
    private var alive = true
    private var loaded = false
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        NodeService.start(this)
        NodeRuntime.start(applicationContext)
        waitForNode()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        val bg = Color.parseColor("#0F1115")
        val root = FrameLayout(this)
        root.setBackgroundColor(bg)

        web = WebView(this)
        web.setBackgroundColor(bg)
        web.visibility = View.INVISIBLE
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "127.0.0.1") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (_: Exception) {
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !loaded) {
                    failed = true
                    showError("Không mở được giao diện nội bộ (${error.description})")
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!loaded && !failed) {
                    loaded = true
                    web.visibility = View.VISIBLE
                    splash.visibility = View.GONE
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")

        splash = LinearLayout(this)
        splash.orientation = LinearLayout.VERTICAL
        splash.gravity = Gravity.CENTER
        splash.setPadding(48, 48, 48, 48)
        splashBar = ProgressBar(this)
        splashText = TextView(this)
        splashText.setTextColor(Color.parseColor("#E7E9EE"))
        splashText.textSize = 15f
        splashText.gravity = Gravity.CENTER
        splashText.text = "Đang khởi động..."
        splashCopy = Button(this)
        splashCopy.text = "Sao chép lỗi"
        splashCopy.visibility = View.GONE
        splashClose = Button(this)
        splashClose.text = "Đóng app (rồi mở lại)"
        splashClose.visibility = View.GONE
        splash.addView(splashBar)
        splash.addView(splashText)
        splash.addView(splashCopy)
        splash.addView(splashClose)

        val lp = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(web, FrameLayout.LayoutParams(lp, lp))
        root.addView(splash, FrameLayout.LayoutParams(lp, lp))
        setContentView(root)
    }

    private fun ping(): Boolean {
        return try {
            val c = URL("http://127.0.0.1:${NodeRuntime.port}/api/ping?token=${NodeRuntime.token}")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 800
            c.readTimeout = 800
            c.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun waitForNode() {
        Thread {
            val deadline = System.currentTimeMillis() + 180_000
            while (alive) {
                val err = NodeRuntime.error
                if (err != null) {
                    runOnUiThread { showError(err) }
                    return@Thread
                }
                runOnUiThread { splashText.text = NodeRuntime.phase }
                if (NodeRuntime.port != 0 && NodeRuntime.token.isNotEmpty() && ping()) {
                    runOnUiThread {
                        web.loadUrl("http://127.0.0.1:${NodeRuntime.port}/?token=${NodeRuntime.token}")
                    }
                    return@Thread
                }
                if (System.currentTimeMillis() > deadline) {
                    runOnUiThread { showError("Quá thời gian chờ Node.js khởi động") }
                    return@Thread
                }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.start()
    }

    private fun showError(msg: String) {
        splashBar.visibility = View.GONE
        splash.visibility = View.VISIBLE
        val report = "MC Bot Manager ${BuildConfig.VERSION_NAME} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ${Build.SUPPORTED_ABIS.joinToString()}\n$msg"
        splashText.text = "Lỗi khởi động:\n$msg\n\nBấm \"Sao chép lỗi\" rồi gửi lại để được hỗ trợ."
        splashCopy.visibility = View.VISIBLE
        splashCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("mcbot-error", report))
            Toast.makeText(this, "Đã sao chép lỗi", Toast.LENGTH_SHORT).show()
        }
        splashClose.visibility = View.VISIBLE
        splashClose.setOnClickListener {
            NodeRuntime.exitAll(this)
            finishAndRemoveTask()
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("mcbot", text))
                Toast.makeText(this@MainActivity, "Đã sao chép", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun batterySettings() {
            runOnUiThread {
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val i = if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    } else {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }
                    startActivity(i)
                } catch (_: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread {
                NodeRuntime.exitAll(this@MainActivity)
                finishAndRemoveTask()
            }
        }

        @JavascriptInterface
        fun appVersion(): String = BuildConfig.VERSION_NAME
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Không thoát app khi bấm Back: bot vẫn chạy nền. Muốn thoát hẳn dùng nút "Thoát" trong Cài đặt.
        if (loaded && web.canGoBack()) web.goBack() else moveTaskToBack(true)
    }

    override fun onDestroy() {
        alive = false
        web.destroy()
        super.onDestroy()
    }
}
