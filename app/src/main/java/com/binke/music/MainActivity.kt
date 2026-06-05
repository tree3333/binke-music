package com.binke.music

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.binke.music.databinding.ActivityMainBinding
import com.binke.music.player.BinkeMediaCallbacks
import com.binke.music.player.MediaControllerCallback
import com.binke.music.player.PlaybackService
import com.binke.music.ui.MusicFragment

/**
 * 批 1 简化 MainActivity - 验证 minSdk 19 APK 能装上
 * Batch 2 会接入完整 ViewModel + 4 Fragment 切换
 */
class MainActivity : AppCompatActivity(), MediaControllerCallback {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 启动播放服务（API 19 没有 startForegroundService，用 startService）
        startPlaybackService()
        BinkeMediaCallbacks.callback = this

        // 批 1: 占位 fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, MusicFragment())
                .commit()
        }
    }

    override fun onDestroy() {
        BinkeMediaCallbacks.callback = null
        super.onDestroy()
    }

    // MediaControllerCallback (占位实现，Batch 2 接 ViewModel)
    override fun onMediaPlay() { /* TODO Batch 2 */ }
    override fun onMediaPause() { /* TODO Batch 2 */ }
    override fun onMediaNext() { /* TODO Batch 2 */ }
    override fun onMediaPrevious() { /* TODO Batch 2 */ }
    override fun onMediaStop() { /* TODO Batch 2 */ }

    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        try {
            // 批 2: 启动 MediaSessionCompat 后台服务（API 19 用 startService，无 startForegroundService）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PlaybackService", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
