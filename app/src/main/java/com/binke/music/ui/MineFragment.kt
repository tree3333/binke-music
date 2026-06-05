package com.binke.music.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.binke.music.R
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import com.binke.music.ui.adapter.CustomPlaylistAdapter
import com.binke.music.ui.adapter.SongListAdapter
import kotlinx.coroutines.launch

/**
 * "我的" tab (替代 Compose MineScreen.kt)
 *
 * 布局（fragment_mine.xml）：
 *  - 顶部大字 "我的" 标题
 *  - 区块 1: 我创建的歌单 - 横向 RecyclerView + "+" 新建按钮
 *  - 区块 2: 我的收藏       - 纵向 RecyclerView（歌曲行）
 *  - 区块 3: 播放历史       - 纵向 RecyclerView（歌曲行）
 *
 * 数据源（MainViewModel）：
 *  - customPlaylists : List<Playlist>  → 区块 1
 *  - favorites       : List<Song>     → 区块 2
 *  - history         : List<Song>     → 区块 3
 *
 * 交互：
 *  - 点击歌单 → viewModel.playCustomPlaylist(playlist)
 *  - 点击收藏歌曲 → viewModel.playFavorites() 设定 playlist 后 playSongAt(index)
 *  - 点击历史歌曲 → viewModel.playHistory()   设定 playlist 后 playSongAt(index)
 *  - "+" 按钮 → AlertDialog 输入歌单名 → viewModel.createPlaylist(name)
 *
 * 注：
 *  - 任务说明里 "addCustomPlaylist()" 在实际 ViewModel 中名为 createPlaylist(name)，
 *    此处使用真实的 API 名称。
 *  - 任务说明里 "最近播放" 区块在 ViewModel 中没有对应数据源，故不单独显示
 *    （保持与原 Compose MineScreen 一致：favorites + history + customPlaylists）。
 */
class MineFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as com.binke.music.BinkeMusicApp
        MainViewModelFactory(app, app.apiService, app.musicRepository, app.musicPlayer)
    }

    private var rvCustomPlaylists: RecyclerView? = null
    private var rvFavorites: RecyclerView? = null
    private var rvHistory: RecyclerView? = null
    private var btnAddPlaylist: ImageButton? = null
    private var emptyCustomPlaylists: TextView? = null
    private var emptyFavorites: TextView? = null
    private var emptyHistory: TextView? = null

    private val customPlaylistAdapter by lazy {
        CustomPlaylistAdapter { playlist -> onCustomPlaylistClick(playlist) }
    }
    private val favoritesAdapter by lazy {
        SongListAdapter { _ -> onFavoritesClick() }
    }
    private val historyAdapter by lazy {
        SongListAdapter { _ -> onHistoryClick() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_mine, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvCustomPlaylists = view.findViewById(R.id.rv_custom_playlists)
        rvFavorites = view.findViewById(R.id.rv_favorites)
        rvHistory = view.findViewById(R.id.rv_history)
        btnAddPlaylist = view.findViewById(R.id.btn_add_playlist)
        emptyCustomPlaylists = view.findViewById(R.id.empty_custom_playlists)
        emptyFavorites = view.findViewById(R.id.empty_favorites)
        emptyHistory = view.findViewById(R.id.empty_history)

        // 横向 - 我创建的歌单
        rvCustomPlaylists?.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = customPlaylistAdapter
        }
        // 纵向 - 我的收藏
        rvFavorites?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoritesAdapter
        }
        // 纵向 - 播放历史
        rvHistory?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }

        btnAddPlaylist?.setOnClickListener { showCreatePlaylistDialog() }

        observeViewModel()
    }

    private fun onCustomPlaylistClick(playlist: Playlist) {
        // 自定义歌单可能为空（仅持有歌单名），playCustomPlaylist 内部已做空判断
        viewModel.playCustomPlaylist(playlist)
    }

    /**
     * 收藏列表的点击统一走 playFavorites() —— 当前 MainViewModel 只暴露
     * "从列表第 0 首开始播" 的入口，per-index 入口需在 ViewModel 内部
     * 切换 _playlist 后再 playSongAt(index)，这里复用其行为。
     */
    private fun onFavoritesClick() {
        viewModel.playFavorites()
    }

    private fun onHistoryClick() {
        viewModel.playHistory()
    }

    /**
     * 弹出"新建歌单"对话框。
     * 用户输入名称后点"创建" → viewModel.createPlaylist(name)，Repository 会
     * 异步写入 DataStore 并触发 refreshMineData()，customPlaylists StateFlow
     * 推新后区块 1 自动刷新。
     */
    private fun showCreatePlaylistDialog() {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx)
            .inflate(R.layout.dialog_new_playlist, null, false)
        val input = dialogView.findViewById<EditText>(R.id.playlist_name_input)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<View>(R.id.btn_create)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.dialog_new_playlist_title)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isNotBlank()) {
                viewModel.createPlaylist(name)
                dialog.dismiss()
            } else {
                // 输入为空时给一个轻微提示（保持原 Compose 行为：忽略空名）
                input.error = getString(R.string.dialog_new_playlist_hint)
            }
        }
        dialog.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.customPlaylists.collect { list ->
                        customPlaylistAdapter.submitList(list)
                        val empty = list.isEmpty()
                        rvCustomPlaylists?.visibility = if (empty) View.GONE else View.VISIBLE
                        emptyCustomPlaylists?.visibility = if (empty) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.favorites.collect { list ->
                        favoritesAdapter.submitList(list)
                        val empty = list.isEmpty()
                        rvFavorites?.visibility = if (empty) View.GONE else View.VISIBLE
                        emptyFavorites?.visibility = if (empty) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.history.collect { list ->
                        historyAdapter.submitList(list)
                        val empty = list.isEmpty()
                        rvHistory?.visibility = if (empty) View.GONE else View.VISIBLE
                        emptyHistory?.visibility = if (empty) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 释放 RV 引用，避免 Fragment 视图销毁后仍持有 Adapter 引用
        rvCustomPlaylists = null
        rvFavorites = null
        rvHistory = null
        btnAddPlaylist = null
        emptyCustomPlaylists = null
        emptyFavorites = null
        emptyHistory = null
    }
}
