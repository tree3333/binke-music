package com.binke.music

import android.app.Application
import androidx.multidex.MultiDex
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.binke.music.data.api.KuwoApiService
import com.binke.music.data.repository.MusicRepository
import com.binke.music.player.MusicPlayer

class BinkeMusicApp : Application(), ImageLoaderFactory {

    lateinit var apiService: KuwoApiService
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var musicPlayer: MusicPlayer
        private set

    /** 全局共享 ImageLoader：Coil 1.4.0 API（API 19 兼容），用默认内存缓存 */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this).build()
    }

    override fun onCreate() {
        super.onCreate()
        // 批 1: API 19 需要手动 install MultiDex
        MultiDex.install(this)
        try {
            apiService = KuwoApiService()
            musicRepository = MusicRepository(this)
            // 批 1: musicPlayer 暂时不 initialize（ExoPlayer/Media3 重写放到 Batch 2）
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "初始化失败", e)
        }
    }

    companion object {
        private const val TAG = "BinkeMusicApp"
    }
}
