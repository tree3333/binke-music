package com.binke.music.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 批 1 占位 Receiver - AndroidManifest 引用了 .player.MediaButtonReceiver
 * Batch 2 重写为 MediaSessionCompat 媒体按钮分发
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Batch 1: noop
    }
}
