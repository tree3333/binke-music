package com.binke.music.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 拦截系统 MEDIA_BUTTON 广播，转发给 PlaybackService 处理。
 *
 * 必须在 manifest 声明并设高优先级（999），确保在所有其他 receiver 之前收到事件。
 * 收到后通过 startService/startForegroundService 拉起 PlaybackService，
 * 由其 onStartCommand 处理按钮事件。
 *
 * 注意：
 * 1. Android 8.0+ 后台限制下，应用不在前台时必须用 startForegroundService
 * 2. abortBroadcast() 只对 ordered broadcast 有效，ACTION_MEDIA_BUTTON 通常是 ordered
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        Log.d(TAG, "MediaButtonReceiver intercepted MEDIA_BUTTON, forwarding to PlaybackService")

        // 拉起 PlaybackService，把原始 intent 原封不动传过去
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = intent.action
            setData(intent.data)
            putExtras(intent)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PlaybackService", e)
        }

        // 消费事件，阻止后续 receiver 处理（abortBroadcast 对 ordered broadcast 生效）
        try {
            abortBroadcast()
        } catch (e: Exception) {
            // 不是 ordered broadcast 时会抛异常，忽略
        }
    }

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }
}
