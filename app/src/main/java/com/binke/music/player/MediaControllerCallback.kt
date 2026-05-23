package com.binke.music.player

/**
 * 媒体控制器回调，PlaybackService 通过此接口将媒体按钮事件传递给 ViewModel。
 * 由 MainActivity 在创建时注入。
 */
interface MediaControllerCallback {
    fun onMediaPlay()
    fun onMediaPause()
    fun onMediaNext()
    fun onMediaPrevious()
    fun onMediaStop()
}
