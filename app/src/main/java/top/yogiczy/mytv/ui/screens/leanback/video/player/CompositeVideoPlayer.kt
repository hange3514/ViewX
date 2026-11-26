package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope

/**
 * 组合播放器：当前仅使用 ExoPlayer（Media3）。
 * 已移除 VLC 回退逻辑以简化架构；如后续需要软解回退，可在此重新扩展。
 */
class LeanbackCompositeVideoPlayer(
    context: Context,
    coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {

    private val media3Player = LeanbackMedia3VideoPlayer(
        context = context,
        coroutineScope = coroutineScope,
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
        player.onEnded { triggerEnded() }
        player.onFirstFrame { triggerFirstFrame(it) }
    }

    @OptIn(UnstableApi::class)
    override fun prepare(url: String) {
        media3Player.prepare(url)
    }

    override fun preloadLive(url: String) = media3Player.preloadLive(url)

    override fun cancelLivePreload() = media3Player.cancelLivePreload()

    override fun prepareLive(url: String, allowPreload: Boolean): Boolean =
        media3Player.prepareLive(url, allowPreload)

    override fun play() {
        media3Player.play()
    }

    override fun pause() {
        media3Player.pause()
    }

    override fun stop() {
        media3Player.stop()
    }

    override fun seekTo(positionMs: Long) {
        media3Player.seekTo(positionMs)
    }

    override val currentPositionMs: Long
        get() = media3Player.currentPositionMs

    override val bufferedDurationMs: Long
        get() = media3Player.bufferedDurationMs

    override val isPlaying: Boolean
        get() = media3Player.isPlaying

    override val hasLivePreload: Boolean
        get() = media3Player.hasLivePreload

    override val hasVideoTrack: Boolean
        get() = media3Player.hasVideoTrack

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
