package top.yogiczy.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackCompositeVideoPlayer
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer

/**
 * 播放器状态
 */
@Stable
class LeanbackVideoPlayerState(
    private val instance: LeanbackVideoPlayer,
    private val defaultAspectRatioProvider: () -> Float? = { null },
) {
    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 错误 */
    var error by mutableStateOf<String?>(null)

    internal var isReady = false
        private set

    var hasRenderedFirstFrame by mutableStateOf(false)
        private set
    private var audioOnlyStarted by mutableStateOf(false)
    val hasStartedPlayback get() = hasRenderedFirstFrame || audioOnlyStarted

    /** 元数据 */
    var metadata by mutableStateOf(LeanbackVideoPlayer.Metadata())

    /** 当前播放位置（毫秒） */
    val currentPositionMs get() = instance.currentPositionMs
    val bufferedDurationMs get() = instance.bufferedDurationMs
    val isPlaying get() = instance.isPlaying
    val hasLivePreload get() = instance.hasLivePreload

    fun prepare(url: String) {
        error = null
        resetPlaybackReadiness()
        instance.prepare(url)
    }

    fun preloadLive(url: String) = instance.preloadLive(url)

    fun cancelLivePreload() = instance.cancelLivePreload()

    fun prepareLive(url: String, allowPreload: Boolean): Boolean {
        error = null
        resetPlaybackReadiness()
        return instance.prepareLive(url, allowPreload)
    }

    fun play() {
        instance.play()
    }

    fun pause() {
        instance.pause()
    }

    fun stop() {
        resetPlaybackReadiness()
        instance.stop()
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        hasRenderedFirstFrame = false
        instance.setVideoSurfaceView(surfaceView)
    }

    fun clearVideoSurface() {
        instance.clearVideoSurface()
    }

    fun seekTo(positionMs: Long) {
        instance.seekTo(positionMs)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onBufferingListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()
    private val onEndedListeners = mutableListOf<() -> Unit>()
    private val onFirstFrameListeners = mutableListOf<(Long) -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: () -> Unit) {
        onBufferingListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    fun onEnded(listener: () -> Unit) {
        onEndedListeners.add(listener)
    }

    fun onFirstFrame(listener: (renderTimeMs: Long) -> Unit) {
        onFirstFrameListeners.add(listener)
    }

    private fun resetPlaybackReadiness() {
        isReady = false
        hasRenderedFirstFrame = false
        audioOnlyStarted = false
    }

    fun initialize() {
        instance.initialize()
        instance.onResolution { width, height ->
            val defaultAspectRatio = defaultAspectRatioProvider()

            if (defaultAspectRatio == null) {
                if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
            } else {
                aspectRatio = defaultAspectRatio
            }
        }
        instance.onError { ex ->
            error = if (ex != null) "${ex.errorCodeName}(${ex.errorCode})"
            else null

            if (error != null) {
                isReady = false
                onErrorListeners.forEach { it.invoke() }
            }
        }
        instance.onReady {
            error = null
            isReady = true
            audioOnlyStarted = !instance.hasVideoTrack
            onReadyListeners.forEach { it.invoke() }
        }
        instance.onBuffering {
            if (it) {
                error = null
                isReady = false
                onBufferingListeners.forEach { it.invoke() }
            }
        }
        instance.onPrepared { resetPlaybackReadiness() }
        instance.onMetadata { metadata = it }
        instance.onCutoff {
            isReady = false
            onCutoffListeners.forEach { it.invoke() }
        }
        instance.onEnded {
            isReady = false
            onEndedListeners.forEach { it.invoke() }
        }
        instance.onFirstFrame { renderTimeMs ->
            hasRenderedFirstFrame = true
            onFirstFrameListeners.forEach { it(renderTimeMs) }
        }
    }

    fun release() {
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onErrorListeners.clear()
        onCutoffListeners.clear()
        onEndedListeners.clear()
        onFirstFrameListeners.clear()
        instance.release()
    }
}

@Composable
fun rememberLeanbackVideoPlayerState(
    defaultAspectRatioProvider: () -> Float? = { null },
): LeanbackVideoPlayerState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 设置项变化后 provider 会重建，用 rememberUpdatedState 保证 state 内始终读最新值
    val latestAspectRatioProvider by rememberUpdatedState(defaultAspectRatioProvider)
    val state = remember {
        LeanbackVideoPlayerState(
            LeanbackCompositeVideoPlayer(
                context = context,
                coroutineScope = coroutineScope,
            ),
            defaultAspectRatioProvider = { latestAspectRatioProvider() },
        )
    }

    DisposableEffect(Unit) {
        state.initialize()

        onDispose {
            state.release()
        }
    }

    return state
}
