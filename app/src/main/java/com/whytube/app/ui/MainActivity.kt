package com.whytube.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.whytube.app.R
import com.whytube.app.api.InvidiousApi
import com.whytube.app.api.VideoItem
import com.whytube.app.databinding.ActivityMainBinding
import com.whytube.app.utils.FavoritesManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: VideoAdapter
    private lateinit var favoritesManager: FavoritesManager

    private var currentCategory = "trending"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoritesManager = FavoritesManager(this)

        setupRecyclerView()
        setupSearch()
        setupChips()
        setupSwipeRefresh()

        loadTrending()
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter(
            onVideoClick = { video ->
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra("VIDEO_ITEM", video)
                }
                startActivity(intent)
            },
            onBookmarkClick = { _, _ ->
                if (currentCategory == "bookmarks") {
                    loadFavorites()
                }
            }
        )

        binding.rvVideos.layoutManager = LinearLayoutManager(this)
        binding.rvVideos.adapter = adapter
        binding.rvVideos.setHasFixedSize(true)
        binding.rvVideos.setItemViewCacheSize(10) // Cache optimal untuk perangkat RAM rendah
    }

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener {
            performSearch()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            loadTrending()
        }

        binding.etSearch.addTextChangedListener { text ->
            binding.btnClearSearch.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return

        hideKeyboard()
        showLoading(true)
        currentCategory = "search"

        lifecycleScope.launch {
            val result = InvidiousApi.search(query)
            showLoading(false)

            result.onSuccess { videos ->
                if (videos.isEmpty()) {
                    showError(getString(R.string.no_results))
                } else {
                    hideError()
                    adapter.submitList(videos)
                    binding.rvVideos.scrollToPosition(0)
                }
            }.onFailure { error ->
                showError(error.localizedMessage ?: getString(R.string.error_fetch))
            }
        }
    }

    private fun setupChips() {
        binding.chipTrending.setOnClickListener {
            currentCategory = "trending"
            loadTrending()
        }

        binding.chipMusic.setOnClickListener {
            currentCategory = "music"
            loadTrending(type = "music")
        }

        binding.chipGaming.setOnClickListener {
            currentCategory = "gaming"
            loadTrending(type = "gaming")
        }

        binding.chipBookmarks.setOnClickListener {
            currentCategory = "bookmarks"
            loadFavorites()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.secondary)
        binding.swipeRefresh.setOnRefreshListener {
            when (currentCategory) {
                "trending" -> loadTrending()
                "music" -> loadTrending(type = "music")
                "gaming" -> loadTrending(type = "gaming")
                "bookmarks" -> loadFavorites()
                "search" -> performSearch()
            }
        }

        binding.btnRetry.setOnClickListener {
            when (currentCategory) {
                "search" -> performSearch()
                "bookmarks" -> loadFavorites()
                else -> loadTrending()
            }
        }
    }

    private fun loadTrending(type: String = "") {
        showLoading(true)
        lifecycleScope.launch {
            val result = InvidiousApi.getTrending(type)
            showLoading(false)

            result.onSuccess { videos ->
                if (videos.isEmpty()) {
                    showError(getString(R.string.no_results))
                } else {
                    hideError()
                    adapter.submitList(videos)
                }
            }.onFailure { error ->
                showError(error.localizedMessage ?: getString(R.string.error_fetch))
            }
        }
    }

    private fun loadFavorites() {
        showLoading(false)
        val favorites = favoritesManager.getAllFavorites()
        if (favorites.isEmpty()) {
            showError("Belum ada video yang disimpan.\nKlik ikon bookmark pada video untuk menyimpan.")
        } else {
            hideError()
            adapter.submitList(favorites)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        if (!isLoading) {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun showError(message: String) {
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
        adapter.submitList(emptyList())
    }

    private fun hideError() {
        binding.layoutError.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}
