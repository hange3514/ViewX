package top.yogiczy.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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

    /** 元数据 */
    var metadata by mutableStateOf(LeanbackVideoPlayer.Metadata())

    /** 当前播放位置（毫秒） */
    var currentPositionMs by mutableLongStateOf(0L)

    fun prepare(url: String) {
        error = null
        instance.prepare(url)
    }

    fun play() {
        instance.play()
    }

    fun pause() {
        instance.pause()
    }

    /** 音量（0.0~1.0） */
    fun setVolume(volume: Float) {
        instance.setVolume(volume)
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }

    fun clearVideoSurface() {
        instance.clearVideoSurface()
    }

    fun seekTo(positionMs: Long) {
        instance.seekTo(positionMs)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
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

            if (error != null) onErrorListeners.forEach { it.invoke() }

        }
        instance.onReady { onReadyListeners.forEach { it.invoke() } }
        instance.onBuffering { if (it) error = null }
        instance.onPrepared { }
        instance.onMetadata { metadata = it }
        instance.onCutoff { onCutoffListeners.forEach { it.invoke() } }
        instance.onCurrentPosition { currentPositionMs = it }
    }

    fun release() {
        onReadyListeners.clear()
        onErrorListeners.clear()
        onCutoffListeners.clear()
        instance.release()
    }
}

@Composable
fun rememberLeanbackVideoPlayerState(
    defaultAspectRatioProvider: () -> Float? = { null },
    minBufferMs: Int = 60_000,
    maxBufferMs: Int = 120_000,
    bufferForPlaybackMs: Int = 2_000,
    bufferForPlaybackAfterRebufferMs: Int = 3_000,
    initialPlayWhenReady: Boolean = true,
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
                minBufferMs = minBufferMs,
                maxBufferMs = maxBufferMs,
                bufferForPlaybackMs = bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs,
                initialPlayWhenReady = initialPlayWhenReady,
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
