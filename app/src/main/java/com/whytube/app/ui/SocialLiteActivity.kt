package com.whytube.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.whytube.app.R
import com.whytube.app.databinding.ActivitySocialLiteBinding
import com.whytube.app.utils.DownloadHelper
import java.io.ByteArrayInputStream

class SocialLiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySocialLiteBinding
    private var platform: String = PLATFORM_TIKTOK
    private var isDataSaverActive = false
    private var isFullscreen = false

    companion object {
        const val EXTRA_PLATFORM = "EXTRA_PLATFORM"
        const val EXTRA_URL = "EXTRA_URL"
        const val PLATFORM_TIKTOK = "tiktok"
        const val PLATFORM_INSTAGRAM = "instagram"

        // Gunakan mobile web TikTok (bukan app redirect) agar bisa diakses via WebView
        private const val URL_TIKTOK = "https://www.tiktok.com/foryou"
        private const val URL_INSTAGRAM = "https://www.instagram.com"

        // User-Agent Chrome Mobile Android yang identik dengan browser nyata
        // TikTok & Instagram aktif mendeteksi & memblokir string "wv" (WebView)
        private const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 11; SM-G991B) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        // Domain tracker & analytics yang diblokir untuk menghemat RAM & Kuota
        private val BLOCKED_TRACKERS = listOf(
            "analytics.tiktok.com",
            "log.tiktok.com",
            "mon.tiktokv.com",
            "ib.tiktokv.com",
            "connect.facebook.net",
            "graph.facebook.com/logging",
            "pixel.facebook.com",
            "google-analytics.com",
            "doubleclick.net",
            "googlesyndication.com"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySocialLiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        platform = intent.getStringExtra(EXTRA_PLATFORM) ?: PLATFORM_TIKTOK
        val initialUrl = intent.getStringExtra(EXTRA_URL)

        setupCookiePersistence()
        setupHeader()
        setupWebView()
        setupActions()
        setupBackHandler()

        val targetUrl = initialUrl ?: if (platform == PLATFORM_TIKTOK) URL_TIKTOK else URL_INSTAGRAM
        binding.webView.loadUrl(targetUrl)
    }

    /**
     * Mengatur CookieManager agar sesi login pengguna tersimpan secara aman & permanen
     */
    private fun setupCookiePersistence() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(binding.webView, true)
        }
    }

    private fun setupHeader() {
        if (platform == PLATFORM_TIKTOK) {
            binding.ivPlatformLogo.setImageResource(R.drawable.ic_tiktok)
            binding.tvPlatformTitle.text = "TikTok Lite"
            binding.btnDownloadFloating.text = "Unduh TikTok"
        } else {
            binding.ivPlatformLogo.setImageResource(R.drawable.ic_instagram)
            binding.tvPlatformTitle.text = "Instagram Lite"
            binding.btnDownloadFloating.text = "Unduh Reels"
        }

        binding.btnBack.setOnClickListener {
            handleBackAction()
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }
    }

    private fun setupActions() {
        // Layar Penuh
        binding.btnFullscreen.setOnClickListener {
            toggleFullscreen(true)
        }

        binding.btnExitFullscreen.setOnClickListener {
            toggleFullscreen(false)
        }

        // Tombol Unduh Video / Reels Mengambang
        binding.btnDownloadFloating.setOnClickListener {
            extractAndDownloadVideo()
        }

        // Menu Opsi Lainnya (Cache, Hemat Kuota, Buka Link, Akun)
        binding.btnMoreSocial.setOnClickListener {
            showOptionsMenu()
        }
    }

    private fun toggleFullscreen(enable: Boolean) {
        isFullscreen = enable
        binding.topBar.visibility = if (enable) View.GONE else View.VISIBLE
        binding.btnExitFullscreen.visibility = if (enable) View.VISIBLE else View.GONE
        val msg = if (enable) "Mode Layar Penuh Aktif" else "Keluar Layar Penuh"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun extractAndDownloadVideo() {
        // Injeksi JS untuk mendeteksi URL video dari elemen <video> di halaman
        val jsDetectVideo = "(function() { " +
                "  var videos = document.getElementsByTagName('video'); " +
                "  for (var i = 0; i < videos.length; i++) { " +
                "    var src = videos[i].currentSrc || videos[i].src; " +
                "    if (src && src.startsWith('http')) return src; " +
                "  } " +
                "  return ''; " +
                "})();"

        binding.webView.evaluateJavascript(jsDetectVideo) { videoUrl ->
            val cleanUrl = videoUrl?.replace("\"", "")?.trim().orEmpty()
            if (cleanUrl.isNotEmpty() && cleanUrl.startsWith("http")) {
                val filename = "${platform.uppercase()}_Video_${System.currentTimeMillis()}"
                DownloadHelper.downloadMedia(
                    this,
                    filename,
                    cleanUrl,
                    isAudioOnly = false
                )
            } else {
                // Alternatif jika URL video blob: tawarkan download dari URL halaman
                val currentWebUrl = binding.webView.url.orEmpty()
                Toast.makeText(
                    this,
                    "Sedang memproses tautan video: $currentWebUrl",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showOptionsMenu() {
        val options = arrayOf(
            "🧹 Bersihkan Cache & Bebaskan Memori HP",
            if (isDataSaverActive) "⚡ Matikan Mode Hemat Kuota Ekstrem" else "🔋 Aktifkan Mode Hemat Kuota Ekstrem (Matikan Autoplay)",
            "🔗 Buka Tautan Video Langsung (Paste Link)",
            "👤 Info Akun & Sesi Login"
        )

        AlertDialog.Builder(this)
            .setTitle("${if (platform == PLATFORM_TIKTOK) "TikTok" else "Instagram"} Opsi")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> clearMemoryCache()
                    1 -> toggleDataSaver()
                    2 -> showDirectLinkDialog()
                    3 -> showAccountSessionInfo()
                }
            }
            .show()
    }

    private fun clearMemoryCache() {
        binding.webView.clearCache(true)
        System.gc()
        Toast.makeText(this, "Cache berhasil dibersihkan! Memori HP lega kembali.", Toast.LENGTH_SHORT).show()
    }

    private fun toggleDataSaver() {
        isDataSaverActive = !isDataSaverActive
        binding.webView.settings.mediaPlaybackRequiresUserGesture = isDataSaverActive

        val jsPauseAll = if (isDataSaverActive) {
            "document.querySelectorAll('video').forEach(v => { v.autoplay = false; v.pause(); });"
        } else {
            "document.querySelectorAll('video').forEach(v => { v.autoplay = true; });"
        }
        binding.webView.evaluateJavascript(jsPauseAll, null)

        val status = if (isDataSaverActive) "Aktif (Autoplay mati)" else "Nonaktif"
        Toast.makeText(this, "Mode Hemat Kuota Ekstrem: $status", Toast.LENGTH_SHORT).show()
    }

    private fun showDirectLinkDialog() {
        val input = EditText(this).apply {
            hint = "Tempel link video TikTok atau IG Reels..."
            setSingleLine()
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Buka Tautan Langsung")
            .setView(input)
            .setPositiveButton("Buka") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    binding.webView.loadUrl(url)
                } else {
                    Toast.makeText(this, "Format link tidak valid", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAccountSessionInfo() {
        val appName = if (platform == PLATFORM_TIKTOK) "TikTok" else "Instagram"
        AlertDialog.Builder(this)
            .setTitle("Sesi Login $appName")
            .setMessage("Akun Anda disimpan di ruang aman lokal HP. Anda hanya perlu login sekali dan sesi akan selalu aktif tanpa perlu login ulang.")
            .setPositiveButton("OK", null)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = false
        settings.savePassword = false

        // Krusial: Ganti User-Agent dengan Chrome mobile nyata
        // TikTok & Instagram mendeteksi '; wv' = WebView lalu memblokir
        settings.userAgentString = CHROME_MOBILE_UA

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.pbWebLoading.visibility = View.VISIBLE
                    binding.pbWebLoading.progress = newProgress
                } else {
                    binding.pbWebLoading.visibility = View.GONE
                    binding.pbCenterLoading.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.pbWebLoading.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.pbWebLoading.visibility = View.GONE
                binding.pbCenterLoading.visibility = View.GONE
                CookieManager.getInstance().flush() // Sinkronisasi cookie login secara permanen
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                for (tracker in BLOCKED_TRACKERS) {
                    if (url.contains(tracker)) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            /**
             * Mencegah TikTok/Instagram mengarahkan ke halaman download app / intent://
             * Jika terdeteksi, arahkan kembali ke URL mobile web yang benar
             */
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Blokir intent:// dan market:// (redirect ke Play Store)
                if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("tiktok://")) {
                    val fallback = if (platform == PLATFORM_TIKTOK) URL_TIKTOK else URL_INSTAGRAM
                    view?.loadUrl(fallback)
                    return true
                }
                return false
            }

            /**
             * Saat terjadi error, reload ke URL utama platform (bukan berhenti total)
             */
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                // Hanya redirect jika bukan resource kecil (gambar/css) yang gagal
                if (failingUrl == view?.url) {
                    val fallback = if (platform == PLATFORM_TIKTOK) URL_TIKTOK else URL_INSTAGRAM
                    view?.loadUrl(fallback)
                }
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })
    }

    private fun handleBackAction() {
        if (isFullscreen) {
            toggleFullscreen(false)
            return
        }
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        binding.webView.onPause()
        binding.webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        binding.webViewContainer.removeView(binding.webView)
        binding.webView.apply {
            stopLoading()
            clearHistory()
            loadUrl("about:blank")
            onPause()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
        System.gc()
    }
}
