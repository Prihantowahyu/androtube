package com.whytube.app.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.whytube.app.R
import com.whytube.app.api.InvidiousApi
import com.whytube.app.api.VideoDetails
import com.whytube.app.api.VideoItem
import com.whytube.app.api.VideoStream
import com.whytube.app.databinding.ActivityPlayerBinding
import com.whytube.app.utils.DownloadHelper
import com.whytube.app.utils.FavoritesManager
import com.whytube.app.utils.MemoryOptimizer
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var relatedAdapter: VideoAdapter

    private var currentVideo: VideoItem? = null
    private var videoDetails: VideoDetails? = null
    private var isAudioOnlyMode = false
    private var currentStream: VideoStream? = null
    private var isWebPlayerActive = false

    // Sleep Timer
    private var sleepTimer: CountDownTimer? = null
    private var sleepTimerMinutesLeft: Int = 0

    // Audio & Kecerahan
    private lateinit var audioManager: AudioManager
    private var maxVolume = 15
    private var initialTouchY = 0f
    private var isLeftSwipe = false
    private val hideFeedbackRunnable = Runnable { binding.tvGestureFeedback.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        favoritesManager = FavoritesManager(this)

        @Suppress("DEPRECATION")
        currentVideo = intent.getSerializableExtra("VIDEO_ITEM") as? VideoItem

        if (currentVideo == null) {
            Toast.makeText(this, "Video tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupInitialUi()
        setupPlayer()
        setupWebPlayer()
        setupGestures()
        setupRelatedList()
        setupButtons()

        loadDetails(currentVideo!!.id)
    }

    private fun setupInitialUi() {
        val video = currentVideo ?: return
        binding.tvPlayerTitle.text = video.title
        binding.tvPlayerStats.text = "${video.uploaderName} • ${video.viewsFormatted}"
        binding.tvPlayerChannelName.text = video.uploaderName
        updateBookmarkIcon(favoritesManager.isFavorite(video.id))
    }

    private fun setupPlayer() {
        exoPlayer = MemoryOptimizer.createLowRamExoPlayer(this).apply {
            binding.playerView.player = this
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> binding.playerLoading.visibility = View.VISIBLE
                        Player.STATE_READY -> binding.playerLoading.visibility = View.GONE
                        Player.STATE_ENDED -> binding.playerLoading.visibility = View.GONE
                        Player.STATE_IDLE -> binding.playerLoading.visibility = View.GONE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    binding.playerLoading.visibility = View.GONE
                    // Jika ekstraksi stream diblokir oleh YouTube, otomatis fallback ke Web Player
                    currentVideo?.let { playWebFallback(it.id) }
                }
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        binding.playerContainer.setOnTouchListener { view, event ->
            val screenWidth = view.width.toFloat()
            val screenHeight = view.height.toFloat()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchY = event.y
                    isLeftSwipe = event.x < (screenWidth / 2)
                    view.handler?.removeCallbacks(hideFeedbackRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialTouchY - event.y
                    if (abs(deltaY) > 15) {
                        val factor = deltaY / screenHeight
                        if (isLeftSwipe) {
                            // Kecerahan Layar (Sisi Kiri)
                            adjustBrightness(factor)
                        } else {
                            // Volume Media (Sisi Kanan)
                            adjustVolume(factor)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.handler?.postDelayed(hideFeedbackRunnable, 1200)
                    true
                }
                else -> false
            }
        }
    }

    private fun adjustBrightness(factor: Float) {
        val layoutParams = window.attributes
        var currentBrightness = layoutParams.screenBrightness
        if (currentBrightness < 0) currentBrightness = 0.5f

        var newBrightness = currentBrightness + (factor * 0.1f)
        newBrightness = newBrightness.coerceIn(0.01f, 1.0f)

        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        val percent = (newBrightness * 100).toInt()
        showGestureFeedback("☀️ Kecerahan: $percent%")
    }

    private fun adjustVolume(factor: Float) {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val delta = if (factor > 0) 1 else -1
        val newVol = (currentVol + delta).coerceIn(0, maxVolume)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        val percent = ((newVol.toFloat() / maxVolume) * 100).toInt()
        showGestureFeedback("🔊 Volume: $percent%")
    }

    private fun showGestureFeedback(text: String) {
        binding.tvGestureFeedback.text = text
        binding.tvGestureFeedback.visibility = View.VISIBLE
    }

    private fun setupRelatedList() {
        relatedAdapter = VideoAdapter(onVideoClick = { selectedVideo ->
            currentVideo = selectedVideo
            setupInitialUi()
            loadDetails(selectedVideo.id)
        })

        binding.rvRelatedVideos.layoutManager = LinearLayoutManager(this)
        binding.rvRelatedVideos.adapter = relatedAdapter
    }

    private fun setupButtons() {
        // Toggle Audio-Only Mode (Hemat 80% RAM & Baterai)
        binding.btnToggleAudioMode.setOnClickListener {
            toggleAudioMode()
        }

        // Quality Selector
        binding.btnQuality.setOnClickListener {
            showQualityDialog()
        }

        // Playback Speed
        binding.btnSpeed.setOnClickListener {
            showSpeedDialog()
        }

        // Download Video & Audio (Offline)
        binding.btnDownload.setOnClickListener {
            showDownloadDialog()
        }

        // Sleep Timer
        binding.btnTimer.setOnClickListener {
            showTimerDialog()
        }

        // Bookmark
        binding.btnPlayerBookmark.setOnClickListener {
            currentVideo?.let { video ->
                val newState = favoritesManager.toggleFavorite(video)
                updateBookmarkIcon(newState)
                val msg = if (newState) "Tersimpan ke favorit" else "Dihapus dari favorit"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // PiP (Picture-in-Picture)
        binding.btnPiP.setOnClickListener {
            enterPipMode()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebPlayer() {
        val webView = binding.playerWebView
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Mencegah redirect ke YouTube app atau market://
                if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("vnd.youtube:")) {
                    return true
                }
                return false
            }
        }
    }

    private fun playWebFallback(videoId: String) {
        if (videoId.isEmpty()) return
        isWebPlayerActive = true

        // Hentikan ExoPlayer untuk menghemat RAM
        exoPlayer?.stop()
        binding.playerView.visibility = View.GONE
        binding.layoutAudioOnly.visibility = View.GONE
        binding.playerWebView.visibility = View.VISIBLE
        binding.playerLoading.visibility = View.GONE

        val embedHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin:0; padding:0; background:#000; overflow:hidden; }
                    html, body { width:100%; height:100%; }
                    iframe { width:100%; height:100%; border:none; }
                </style>
            </head>
            <body>
                <iframe 
                    src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&modestbranding=1&rel=0&iv_load_policy=3" 
                    allow="autoplay; encrypted-media; picture-in-picture; fullscreen" 
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()

        binding.playerWebView.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            embedHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun loadDetails(videoId: String) {
        binding.playerLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = InvidiousApi.getVideoDetails(videoId)
            binding.playerLoading.visibility = View.GONE

            result.onSuccess { details ->
                videoDetails = details
                binding.tvPlayerTitle.text = details.title
                binding.tvPlayerStats.text = "${details.viewsFormatted} • ${details.publishedTime}"
                binding.tvPlayerChannelName.text = details.uploaderName
                binding.tvPlayerSubscribers.text = details.subscriberCount
                binding.tvDescription.text = if (details.description.isNotEmpty()) {
                    details.description
                } else {
                    "Tidak ada deskripsi tersedia."
                }

                relatedAdapter.submitList(details.relatedVideos)

                // Pilih stream sesuai kualitas default hemat kuota (Default: 360p)
                val prefs = getSharedPreferences("whytube_prefs", Context.MODE_PRIVATE)
                val defaultPref = prefs.getString("default_quality", "360p") ?: "360p"

                val preferredStream = details.streams.find { it.quality.contains(defaultPref, ignoreCase = true) }
                    ?: details.streams.find { it.quality.contains("360p") }
                    ?: details.streams.firstOrNull { !it.isAudioOnly }
                    ?: details.streams.firstOrNull()

                if (preferredStream != null) {
                    playStream(preferredStream)
                } else {
                    // Jika ekstraksi stream diblokir bot check, putar langsung via Web Player
                    playWebFallback(videoId)
                }
            }.onFailure {
                // Fallback aman: Putar langsung via Web Player
                playWebFallback(videoId)
            }
        }
    }

    private fun playStream(stream: VideoStream) {
        isWebPlayerActive = false
        binding.playerWebView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        currentStream = stream
        binding.btnQuality.text = stream.quality

        val currentPosition = exoPlayer?.currentPosition ?: 0L
        val mediaItem = MediaItem.fromUri(stream.url)
        exoPlayer?.setMediaItem(mediaItem, currentPosition)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun toggleAudioMode() {
        val details = videoDetails ?: return
        isAudioOnlyMode = !isAudioOnlyMode

        if (isAudioOnlyMode) {
            binding.layoutAudioOnly.visibility = View.VISIBLE
            binding.btnToggleAudioMode.text = "Mode Video"

            val audioStream = details.streams.find { it.isAudioOnly }
            if (audioStream != null) {
                playStream(audioStream)
            }
            Toast.makeText(this, "Mode Audio Aktif - Sangat hemat RAM & data", Toast.LENGTH_SHORT).show()
        } else {
            binding.layoutAudioOnly.visibility = View.GONE
            binding.btnToggleAudioMode.text = "Mode Audio"

            val videoStream = details.streams.find { it.quality.contains("360p") }
                ?: details.streams.firstOrNull { !it.isAudioOnly }

            videoStream?.let { playStream(it) }
        }
    }

    private fun showQualityDialog() {
        val streams = videoDetails?.streams ?: return
        if (streams.isEmpty()) return

        val qualityLabels = streams.map { "${it.quality} (Hemat)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih Resolusi Video")
            .setItems(qualityLabels) { _, which ->
                val selected = streams[which]
                playStream(selected)

                // Simpan preferensi untuk video berikutnya
                getSharedPreferences("whytube_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("default_quality", selected.quality)
                    .apply()
                Toast.makeText(this, "Disetel sebagai default: ${selected.quality}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "1.75x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        AlertDialog.Builder(this)
            .setTitle("Kecepatan Pemutaran")
            .setItems(speeds) { _, which ->
                val selectedSpeed = speedValues[which]
                exoPlayer?.setPlaybackSpeed(selectedSpeed)
                binding.btnSpeed.text = "${selectedSpeed}x"
            }
            .show()
    }

    private fun showDownloadDialog() {
        val details = videoDetails ?: return
        if (details.streams.isEmpty()) {
            Toast.makeText(this, "Link stream belum siap", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadOptions = mutableListOf<Pair<String, VideoStream>>()
        for (s in details.streams) {
            val label = if (s.isAudioOnly) "🎵 Audio Saja (${s.quality})" else "🎬 Video (${s.quality})"
            downloadOptions.add(Pair(label, s))
        }

        val labels = downloadOptions.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Unduh untuk Ditonton Offline")
            .setItems(labels) { _, which ->
                val selected = downloadOptions[which].second
                DownloadHelper.downloadMedia(
                    this,
                    details.title,
                    selected.url,
                    selected.isAudioOnly
                )
            }
            .show()
    }

    private fun showTimerDialog() {
        val timerOptions = arrayOf("Matikan Timer", "15 Menit", "30 Menit", "45 Menit", "60 Menit")
        val minutesArray = intArrayOf(0, 15, 30, 45, 60)

        AlertDialog.Builder(this)
            .setTitle("Sleep Timer (Otomatis Berhenti)")
            .setItems(timerOptions) { _, which ->
                val minutes = minutesArray[which]
                sleepTimer?.cancel()
                if (minutes == 0) {
                    binding.btnTimer.text = "Timer"
                    Toast.makeText(this, "Sleep timer dinonaktifkan", Toast.LENGTH_SHORT).show()
                } else {
                    val durationMs = minutes * 60 * 1000L
                    sleepTimer = object : CountDownTimer(durationMs, 60000) {
                        override fun onTick(millisUntilFinished: Long) {
                            sleepTimerMinutesLeft = (millisUntilFinished / 60000).toInt() + 1
                            binding.btnTimer.text = "${sleepTimerMinutesLeft}m"
                        }

                        override fun onFinish() {
                            exoPlayer?.pause()
                            binding.btnTimer.text = "Timer"
                            Toast.makeText(this@PlayerActivity, "Sleep timer selesai: Pemutaran dihentikan", Toast.LENGTH_LONG).show()
                        }
                    }.start()
                    Toast.makeText(this, "Timer disetel $minutes menit", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun updateBookmarkIcon(isFav: Boolean) {
        binding.btnPlayerBookmark.setImageResource(
            if (isFav) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
        )
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal masuk mode PiP: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Fitur PiP membutuhkan Android 8.0 ke atas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isAudioOnlyMode && (exoPlayer?.isPlaying == true || isWebPlayerActive)) {
            enterPipMode()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.scrollDetails.visibility = View.GONE
            binding.playerView.useController = false
            binding.playerContainer.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            binding.scrollDetails.visibility = View.VISIBLE
            binding.playerView.useController = true
            val density = resources.displayMetrics.density
            binding.playerContainer.layoutParams.height = (220 * density).toInt()
        }
    }

    override fun onPause() {
        super.onPause()
        val isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isAudioOnlyMode && !isInPip) {
            exoPlayer?.pause()
            if (isWebPlayerActive) {
                binding.playerWebView.onPause()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isWebPlayerActive) {
            binding.playerWebView.onResume()
        }
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        sleepTimer = null
        exoPlayer?.release()
        exoPlayer = null
        try {
            binding.playerWebView.stopLoading()
            binding.playerWebView.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
