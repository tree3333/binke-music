package com.binke.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.binke.music.R

/**
 * 批 1 占位 Fragment - 验证 minSdk 19 APK 能装上
 * 后续 Batch 2-3 会被 HomeScreen/MusicScreen/MineScreen 替换
 */
class MusicFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_music, container, false)
}
