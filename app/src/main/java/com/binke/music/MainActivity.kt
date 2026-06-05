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
import com.binke.music.ui.HomeFragment
import com.binke.music.ui.MineFragment
import com.binke.music.ui.MusicFragment

/**
 * 批 3: 完整 MainActivity - BottomNav 切换 3 个 tab
 */
class MainActivity : AppCompatActivity(), MediaControllerCallback {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startPlaybackService()
        BinkeMediaCallbacks.callback = this

        // 批 3: BottomNav 切换 Fragment
        if (savedInstanceState == null) {
            showFragment(TAG_MUSIC)
            binding.bottomNav.selectedItemId = R.id.nav_music
        }
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showFragment(TAG_HOME); true }
                R.id.nav_music -> { showFragment(TAG_MUSIC); true }
                R.id.nav_mine -> { showFragment(TAG_MINE); true }
                else -> false
            }
        }
    }

    private fun showFragment(tag: String) {
        val fm = supportFragmentManager
        val current = fm.fragments.firstOrNull { it.isVisible }
        val target = fm.findFragmentByTag(tag)
        val tx = fm.beginTransaction()
        if (current != null) tx.hide(current)
        if (target == null) {
            val newFragment = when (tag) {
                TAG_HOME -> HomeFragment()
                TAG_MUSIC -> MusicFragment()
                TAG_MINE -> MineFragment()
                else -> MusicFragment()
            }
            tx.add(binding.fragmentContainer.id, newFragment, tag)
        } else {
            tx.show(target)
        }
        tx.commit()
    }

    override fun onDestroy() {
        BinkeMediaCallbacks.callback = null
        super.onDestroy()
    }

    // MediaControllerCallback 占位
    override fun onMediaPlay() {}
    override fun onMediaPause() {}
    override fun onMediaNext() {}
    override fun onMediaPrevious() {}
    override fun onMediaStop() {}

    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        try {
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
        private const val TAG_HOME = "home"
        private const val TAG_MUSIC = "music"
        private const val TAG_MINE = "mine"
    }
}
