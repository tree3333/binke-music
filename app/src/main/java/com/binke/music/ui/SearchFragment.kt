package com.binke.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.binke.music.R
import com.binke.music.data.model.Song
import com.binke.music.ui.adapter.SearchResultAdapter
import kotlinx.coroutines.launch

/**
 * 独立搜索页 (替代 Compose SearchScreen.kt)
 *
 * 入口: HomeScreen 搜索框点击后 push 到 fragment_container (addToBackStack)
 *
 * 布局 (fragment_search.xml):
 *  - 顶部 64dp 搜索栏: 返回按钮 + SearchView + 搜索按钮
 *  - 中部 RecyclerView 展示搜索结果 (item_search_result.xml)
 *  - 加载中 ProgressBar / 空态 TextView / 底部结果统计
 *
 * 数据源 (MainViewModel):
 *  - searchQuery    : 当前输入框文本
 *  - searchResults  : List<Song>
 *  - isSearching    : Boolean
 *
 * 交互:
 *  - 输入文本 → viewModel.updateSearchQuery(query) (走 ViewModel 250ms 防抖自动搜索)
 *  - 键盘回车 / 点击"搜索"按钮 → viewModel.search(query) (显式搜索, 写入历史)
 *  - 点击返回 → activity.onBackPressedDispatcher.onBackPressed()
 *  - 点击结果项 / 播放按钮 → viewModel.playSong(song)
 *    (playSong 内部: 不在当前列表中时把 _playlist 设为单首, 正好对应搜索场景)
 *
 * 注:
 *  - SearchView 内部 EditText (search_src_text) 需手动设置白色文字 + 灰色 hint,
 *    因为父主题 Theme.MaterialComponents.NoActionBar 默认文字是黑色, 与 #121212
 *    深色背景冲突。
 *  - onQueryTextChange 把 query 推到 ViewModel 走 updateSearchQuery 路径, 由
 *    ViewModel 内部 250ms 防抖触发自动搜索; onQueryTextSubmit 走 search() 路径
 *    (显式搜索, 写入历史, 立即触发)。
 *  - 历史搜索 / 热搜词 功能 SearchScreen 原本就没完整实现, 故不展示。
 */
class SearchFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as com.binke.music.BinkeMusicApp
        MainViewModelFactory(app, app.apiService, app.musicRepository, app.musicPlayer)
    }

    private var searchView: SearchView? = null
    private var btnBack: ImageButton? = null
    private var btnSearchSubmit: android.widget.Button? = null
    private var recyclerView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var emptyView: TextView? = null
    private var resultCount: TextView? = null

    private val adapter by lazy {
        SearchResultAdapter(
            onClick = { song -> onSongClick(song) },
            onPlayClick = { song -> onSongClick(song) }
        )
    }

    /**
     * 避免在 onQueryTextChange 与 onQueryTextSubmit 之间出现回环: 用户敲回车后,
     * searchView.setQuery 也会触发 onQueryTextChange, 但此时 _searchQuery.value
     * 已经是新 query, 不会重复搜索。
     */
    private var isProgrammaticQueryChange = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchView = view.findViewById(R.id.search_view)
        btnBack = view.findViewById(R.id.btn_back)
        btnSearchSubmit = view.findViewById(R.id.btn_search)
        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        emptyView = view.findViewById(R.id.empty_view)
        resultCount = view.findViewById(R.id.result_count)

        recyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchFragment.adapter
        }

        styleSearchView()
        setupListeners()
        observeViewModel()
    }

    /**
     * 把 AppCompat SearchView 内部 EditText 改为白色文字 + 灰色 hint,
     * 以适配 @color/background (#121212) 的深色主题。
     */
    private fun styleSearchView() {
        searchView?.findViewById<EditText>(
            androidx.appcompat.R.id.search_src_text
        )?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            setHintTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        }
        // 清除默认焦点, 避免进入页面就弹起软键盘
        searchView?.clearFocus()
    }

    private fun setupListeners() {
        // 返回按钮: 优先 popBackStack, 退到 Activity 时走 onBackPressed
        btnBack?.setOnClickListener {
            val fm = parentFragmentManager
            if (fm.backStackEntryCount > 0) {
                fm.popBackStack()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim().orEmpty()
                if (q.isNotBlank()) {
                    viewModel.search(q)
                    searchView?.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (isProgrammaticQueryChange) {
                    // 由外部 (如恢复 saved state) 触发的 setQuery, 不再回灌到 ViewModel
                    return true
                }
                // 实时把 query 推到 ViewModel (走自动搜索 + 联想词)
                viewModel.updateSearchQuery(newText.orEmpty())
                return true
            }
        })

        // 右侧"搜索"按钮: 与键盘回车行为一致 (显式搜索 + 写历史)
        btnSearchSubmit?.setOnClickListener {
            val q = searchView?.query?.toString()?.trim().orEmpty()
            if (q.isNotBlank()) {
                viewModel.search(q)
                searchView?.clearFocus()
            }
        }
    }

    private fun onSongClick(song: Song) {
        // playSong(song) 内部: 不在当前 _playlist 中时, 把 _playlist 设为单首 (搜索场景)
        // 在当前 _playlist 中时, 视为列表内切歌。
        viewModel.playSong(song)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults.collect { results ->
                        adapter.submitList(results)
                        updateState()
                    }
                }
                launch {
                    viewModel.isSearching.collect { searching ->
                        progressBar?.visibility =
                            if (searching) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.searchQuery.collect { q ->
                        // 仅当 SearchView 当前 query 与 ViewModel 不一致时才同步,
                        // 避免 onQueryTextChange -> ViewModel -> collect -> setQuery 回环
                        val current = searchView?.query?.toString().orEmpty()
                        if (current != q) {
                            isProgrammaticQueryChange = true
                            searchView?.setQuery(q, false)
                            isProgrammaticQueryChange = false
                        }
                    }
                }
            }
        }
    }

    /**
     * 根据 (searchResults, isSearching, searchQuery) 三态决定各 view 的可见性:
     *  - 加载中: 进度条显示, RV 保留 (旧结果不闪)
     *  - 有结果: RV 显示, 底部统计显示
     *  - 无结果 + 有 query: 空态显示 "没有找到相关结果"
     *  - 无结果 + 无 query: 空态显示 "输入关键词搜索歌曲"
     */
    private fun updateState() {
        val results = viewModel.searchResults.value
        val searching = viewModel.isSearching.value
        val query = viewModel.searchQuery.value

        when {
            searching -> {
                recyclerView?.visibility =
                    if (results.isEmpty()) View.GONE else View.VISIBLE
                emptyView?.visibility = View.GONE
                resultCount?.visibility = View.GONE
            }
            results.isNotEmpty() -> {
                recyclerView?.visibility = View.VISIBLE
                emptyView?.visibility = View.GONE
                resultCount?.visibility = View.VISIBLE
                resultCount?.text = getString(R.string.search_result_count, results.size)
            }
            query.isBlank() -> {
                recyclerView?.visibility = View.GONE
                emptyView?.visibility = View.VISIBLE
                emptyView?.text = getString(R.string.search_empty_initial)
                resultCount?.visibility = View.GONE
            }
            else -> {
                recyclerView?.visibility = View.GONE
                emptyView?.visibility = View.VISIBLE
                emptyView?.text = getString(R.string.search_empty_no_results)
                resultCount?.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchView = null
        btnBack = null
        btnSearchSubmit = null
        recyclerView = null
        progressBar = null
        emptyView = null
        resultCount = null
    }
}
