package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope

/**
 * 组合播放器：当前仅使用 ExoPlayer（Media3）。
 * 已移除 VLC 回退逻辑以简化架构；如后续需要软解回退，可在此重新扩展。
 */
class LeanbackCompositeVideoPlayer(
    context: Context,
    coroutineScope: CoroutineScope,
    minBufferMs: Int = 60_000,
    maxBufferMs: Int = 120_000,
    bufferForPlaybackMs: Int = 2_000,
    bufferForPlaybackAfterRebufferMs: Int = 3_000,
    initialPlayWhenReady: Boolean = true,
) : LeanbackVideoPlayer(coroutineScope) {

    private val media3Player = LeanbackMedia3VideoPlayer(
        context = context,
        coroutineScope = coroutineScope,
        minBufferMs = minBufferMs,
        maxBufferMs = maxBufferMs,
        bufferForPlaybackMs = bufferForPlaybackMs,
        bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs,
        initialPlayWhenReady = initialPlayWhenReady,
    )

    override fun initialize() {
        super.initialize()
        media3Player.initialize()
        setupForwarding(media3Player)
    }

    private fun setupForwarding(player: LeanbackVideoPlayer) {
        player.onResolution { width, height -> triggerResolution(width, height) }
        player.onError { error -> triggerError(error) }
        player.onReady { triggerReady() }
        player.onBuffering { buffering -> triggerBuffering(buffering) }
        player.onPrepared { notifyPrepared() }
        player.onMetadata { meta ->
            metadata = meta
            triggerMetadata(meta)
        }
        player.onCutoff { triggerCutoff() }
    }

    override fun prepare(url: String) {
        Log.d("CompositePlayer", "prepare Media3 $url")
        media3Player.prepare(url)
    }

    override fun play() {
        media3Player.play()
    }

    override fun pause() {
        media3Player.pause()
    }

    override fun setVolume(volume: Float) {
        media3Player.setVolume(volume)
    }

    override fun seekTo(positionMs: Long) {
        media3Player.seekTo(positionMs)
    }

    override val currentPositionMs: Long
        get() = media3Player.currentPositionMs

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        Log.d("CompositePlayer", "setVideoSurfaceView")
        media3Player.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurface() {
        Log.d("CompositePlayer", "clearVideoSurface")
        media3Player.clearVideoSurface()
    }

    override fun release() {
        media3Player.release()
        super.release()
    }
}
