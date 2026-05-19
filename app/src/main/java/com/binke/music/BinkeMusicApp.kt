package com.binke.music

import android.app.Application
import com.binke.music.data.api.KuwoApiService
import com.binke.music.data.repository.MusicRepository
import com.binke.music.player.MusicPlayer

class BinkeMusicApp : Application() {
    
    lateinit var apiService: KuwoApiService
        private set
    
    lateinit var musicRepository: MusicRepository
        private set
    
    lateinit var musicPlayer: MusicPlayer
        private set
    
    override fun onCreate() {
        super.onCreate()
        apiService = KuwoApiService()
        musicRepository = MusicRepository(this)
        musicPlayer = MusicPlayer(this)
        musicPlayer.initialize()
    }
}
