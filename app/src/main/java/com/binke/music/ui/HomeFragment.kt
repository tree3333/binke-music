package com.binke.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.binke.music.R
import com.binke.music.data.model.Playlist
import com.binke.music.ui.adapter.HomeSectionAdapter
import kotlinx.coroutines.launch

/**
 * 首页 tab - 推荐/榜单 (替代 Compose HomeScreen.kt)
 *
 * 布局：标题 + 横向 RV + 标题 + 横向 RV
 *  - 每日推荐：recommendPlaylists.take(6)
 *  - 热门榜单：bangPlaylists.take(8)
 *
 * 点击歌单 → viewModel.playPlaylist(playlist)
 * 加载态：ProgressBar（首次）/ SwipeRefreshLayout spinner（下拉刷新）
 * 空态：TextView "暂无内容"
 */
class HomeFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as com.binke.music.BinkeMusicApp
        MainViewModelFactory(app, app.apiService, app.musicRepository, app.musicPlayer)
    }

    private var recyclerView: RecyclerView? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var progressBar: ProgressBar? = null
    private var emptyView: TextView? = null

    private val sectionAdapter by lazy {
        HomeSectionAdapter { playlist -> onPlaylistClick(playlist) }
    }

    /** 上次见过的数据快照，避免重复刷新 section */
    private var lastRecommendHash: Int = 0
    private var lastBangHash: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        progressBar = view.findViewById(R.id.progress_bar)
        emptyView = view.findViewById(R.id.empty_view)

        recyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sectionAdapter
            // 嵌套 RV：禁止外层 item 动画，避免横向 RV 首次 attach 时抖动
            itemAnimator = null
        }

        swipeRefresh?.setColorSchemeResources(R.color.primary)
        swipeRefresh?.setOnRefreshListener { viewModel.loadHomeData() }

        observeViewModel()

        // 数据未加载过则触发一次拉取（与原 HomeScreen 行为一致）
        if (viewModel.recommendPlaylists.value.isEmpty() &&
            viewModel.bangPlaylists.value.isEmpty() &&
            !viewModel.isLoadingHome.value
        ) {
            viewModel.loadHomeData()
        }
    }

    private fun onPlaylistClick(playlist: Playlist) {
        viewModel.playPlaylist(playlist)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recommendPlaylists.collect { recommend ->
                        val h = recommend.hashCode()
                        if (h != lastRecommendHash) {
                            lastRecommendHash = h
                            refreshSections()
                        }
                    }
                }
                launch {
                    viewModel.bangPlaylists.collect { bang ->
                        val h = bang.hashCode()
                        if (h != lastBangHash) {
                            lastBangHash = h
                            refreshSections()
                        }
                    }
                }
                launch {
                    viewModel.isLoadingHome.collect { loading ->
                        // 首次加载且无数据 → 居中 ProgressBar
                        // 已有数据 → 顶部 SwipeRefresh spinner
                        progressBar?.visibility =
                            if (loading && sectionAdapter.isEmpty()) View.VISIBLE else View.GONE
                        swipeRefresh?.isRefreshing =
                            loading && !sectionAdapter.isEmpty()
                        // 加载结束 + 仍无数据 → 显示空态
                        updateEmptyView(loading)
                    }
                }
            }
        }
    }

    private fun refreshSections() {
        val recommend = viewModel.recommendPlaylists.value
        val bang = viewModel.bangPlaylists.value
        sectionAdapter.submit(recommend, bang)
        updateEmptyView(viewModel.isLoadingHome.value)
    }

    private fun updateEmptyView(loading: Boolean) {
        emptyView?.visibility =
            if (!loading && sectionAdapter.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView = null
        swipeRefresh = null
        progressBar = null
        emptyView = null
    }
}
