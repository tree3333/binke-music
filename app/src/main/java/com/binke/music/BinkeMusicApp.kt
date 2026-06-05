package com.binke.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
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

    /** 全局共享 ImageLoader：只走内存缓存，不落磁盘，保证预加载命中 */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(0L)  // 禁用磁盘缓存
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            apiService = KuwoApiService()
            musicRepository = MusicRepository(this)
            musicPlayer = MusicPlayer(this)
            musicPlayer.initialize()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "初始化失败", e)
        }
    }

    companion object {
        private const val TAG = "BinkeMusicApp"
    }
}
