package com.whytube.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.whytube.app.R
import com.whytube.app.databinding.ActivitySocialLiteBinding
import java.io.ByteArrayInputStream

class SocialLiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySocialLiteBinding
    private var platform: String = PLATFORM_TIKTOK

    companion object {
        const val EXTRA_PLATFORM = "EXTRA_PLATFORM"
        const val PLATFORM_TIKTOK = "tiktok"
        const val PLATFORM_INSTAGRAM = "instagram"

        private const val URL_TIKTOK = "https://www.tiktok.com"
        private const val URL_INSTAGRAM = "https://www.instagram.com"

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

        setupHeader()
        setupWebView()
        setupBackHandler()

        val targetUrl = if (platform == PLATFORM_TIKTOK) URL_TIKTOK else URL_INSTAGRAM
        binding.webView.loadUrl(targetUrl)
    }

    private fun setupHeader() {
        if (platform == PLATFORM_TIKTOK) {
            binding.ivPlatformLogo.setImageResource(R.drawable.ic_tiktok)
            binding.tvPlatformTitle.text = "TikTok Lite"
        } else {
            binding.ivPlatformLogo.setImageResource(R.drawable.ic_instagram)
            binding.tvPlatformTitle.text = "Instagram Lite"
        }

        binding.btnBack.setOnClickListener {
            handleBackAction()
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        // Optimasi khusus RAM Kecil (1GB - 2GB)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = false // Matikan DB lokal browser agar tidak alokasi RAM
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT // Manfaatkan cache disk
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // User-Agent Mobile Modern namun Ringan
        settings.userAgentString = settings.userAgentString.replace("; wv", "")

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
            }

            // Blokir tracker & skrip analitik berat untuk hemat memori
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                for (tracker in BLOCKED_TRACKERS) {
                    if (url.contains(tracker)) {
                        // Mengembalikan respons kosong (0 byte) untuk skrip pelacak
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
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
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Bekukan eksekusi JavaScript agar tidak memakan CPU & baterai di background
        binding.webView.onPause()
        binding.webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    override fun onDestroy() {
        // Sangat krusial untuk RAM kecil: lepaskan dan hancurkan instans WebView seketika
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
        System.gc() // Bersihkan RAM segera
    }
}
