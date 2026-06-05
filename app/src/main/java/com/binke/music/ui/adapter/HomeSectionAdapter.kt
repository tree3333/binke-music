package com.binke.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.binke.music.R
import com.binke.music.data.model.Playlist

/**
 * 首页纵向 RecyclerView 的行模型。
 *
 * - [Title]    区块标题（如 "每日推荐" / "热门榜单"）
 * - [Playlists] 横向滑动歌单列表
 */
sealed class HomeRow {
    data class Title(val text: String) : HomeRow()
    data class Playlists(val list: List<Playlist>) : HomeRow()
}

/**
 * 首页 (Home) 主 RecyclerView 适配器。
 *
 * 多 ViewType 模式：
 *   - TYPE_TITLE   : 区块标题 item
 *   - TYPE_ROW     : 内嵌一个横向 RecyclerView 的 item
 *
 * 数据组装（[submit]）根据首页 "每日推荐" + "热门榜单" 两组数据
 * 生成 4 个 HomeRow：title + list + title + list。
 * 当任一数据集为空时，对应 title+row 会被省略（避免空标题）。
 */
class HomeSectionAdapter(
    private val onPlaylistClick: (Playlist) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<HomeRow>()

    /** 当前是否完全无数据（用于空态判断） */
    fun isEmpty(): Boolean = rows.isEmpty()

    /**
     * 重置所有 row。section 顺序固定为「每日推荐 → 热门榜单」，
     * 与原 Compose HomeScreen 行为一致。
     */
    fun submit(recommendPlaylists: List<Playlist>, bangPlaylists: List<Playlist>) {
        rows.clear()
        // 每日推荐：取前 6 个
        if (recommendPlaylists.isNotEmpty()) {
            rows.add(HomeRow.Title(RECOMMEND_TITLE))
            rows.add(HomeRow.Playlists(recommendPlaylists.take(RECOMMEND_LIMIT)))
        }
        // 热门榜单：取前 8 个
        if (bangPlaylists.isNotEmpty()) {
            rows.add(HomeRow.Title(BANG_TITLE))
            rows.add(HomeRow.Playlists(bangPlaylists.take(BANG_LIMIT)))
        }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is HomeRow.Title -> TYPE_TITLE
        is HomeRow.Playlists -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TITLE -> TitleVH(inflater.inflate(R.layout.item_section_header, parent, false))
            TYPE_ROW -> RowVH(inflater.inflate(R.layout.item_horizontal_row, parent, false), onPlaylistClick)
            else -> throw IllegalStateException("Unknown viewType=$viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is HomeRow.Title -> (holder as TitleVH).bind(row.text)
            is HomeRow.Playlists -> (holder as RowVH).bind(row.list)
        }
    }

    override fun getItemCount(): Int = rows.size

    /** 标题 ViewHolder */
    class TitleVH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.section_title)
        fun bind(text: String) {
            titleView.text = text
        }
    }

    /**
     * 横向滑动行 ViewHolder。
     *
     * 内部维护一个 [PlaylistCardAdapter] + 横向 [LinearLayoutManager]，
     * 每次 bind 走 DiffUtil 增量更新。
     */
    class RowVH(
        view: View,
        private val onPlaylistClick: (Playlist) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val recycler: RecyclerView = view.findViewById(R.id.horizontal_recycler)
        private val adapter = PlaylistCardAdapter(onPlaylistClick)

        init {
            recycler.layoutManager = LinearLayoutManager(
                itemView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            recycler.adapter = adapter
            // 嵌套 RV：禁止 item 动画，避免与外层 RV 拖动冲突
            recycler.itemAnimator = null
        }

        fun bind(list: List<Playlist>) {
            adapter.submitList(list)
        }
    }

    companion object {
        private const val TYPE_TITLE = 0
        private const val TYPE_ROW = 1

        private const val RECOMMEND_LIMIT = 6
        private const val BANG_LIMIT = 8
        private const val RECOMMEND_TITLE = "每日推荐"
        private const val BANG_TITLE = "热门榜单"
    }
}
