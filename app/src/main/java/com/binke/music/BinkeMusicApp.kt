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

    /**
     * 全局共享 ImageLoader 单例：AsyncImage / SongCache.preloadPics 都必须共享同一实例 + memoryCache，
     * 否则 preloadPics 写入的 cover 不会被 AsyncImage 命中 → AsyncImage 重新下载 → 切歌 1 秒闪旧 cover。
     *
     * Coil 2.x 的 `coil.Coil.imageLoader(context)` 走 `ImageLoaderFactory.newImageLoader()` 路径，
     * 如果 newImageLoader() 每次 new ImageLoader.Builder(this).build() 拿到的就是新实例（memoryCache 不共享）。
     * 改成返回 imageLoader 单例（by lazy 缓存），让 Coil Compose AsyncImage 内部 Coil.imageLoader(context)
     * 调到这里时拿到的是同一实例。
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
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
            // 关键：切歌时 cover 必须瞬时切换，100ms crossfade 渐变会让 user 感觉"不像命中 cache"
            .crossfade(false)
            .build()
    }

    override fun newImageLoader(): ImageLoader = imageLoader

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
