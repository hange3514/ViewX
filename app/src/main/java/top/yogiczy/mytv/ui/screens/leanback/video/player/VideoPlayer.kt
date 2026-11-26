package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Loggable

abstract class LeanbackVideoPlayer(
    private val coroutineScope: CoroutineScope,
) : Loggable() {
    private var loadTimeoutJob: Job? = null
    private var cutoffTimeoutJob: Job? = null
    private var positionCheckpoint = -1L

    protected var metadata = Metadata()

    open fun initialize() {
        clearAllListeners()
    }

    open fun release() {
        cancelLoadTimeout()
        resetCutoff()
        clearAllListeners()
    }

    abstract fun prepare(url: String)

    open fun preloadLive(url: String) {}

    open fun cancelLivePreload() {}

    open fun prepareLive(url: String, allowPreload: Boolean): Boolean {
        prepare(url)
        return false
    }

    abstract fun play()

    abstract fun pause()

    open fun stop() = pause()

    abstract fun setVideoSurfaceView(surfaceView: SurfaceView)

    open fun clearVideoSurface() {}

    abstract fun seekTo(positionMs: Long)

    abstract val currentPositionMs: Long
    open val bufferedDurationMs: Long get() = 0L
    open val isPlaying: Boolean get() = false
    open val hasLivePreload: Boolean get() = false
    open val hasVideoTrack: Boolean get() = true

    private val onResolutionListeners = mutableListOf<(width: Int, height: Int) -> Unit>()
    private val onErrorListeners = mutableListOf<(error: PlaybackException?) -> Unit>()
    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onBufferingListeners = mutableListOf<(buffering: Boolean) -> Unit>()
    private val onPreparedListeners = mutableListOf<() -> Unit>()
    private val onMetadataListeners = mutableListOf<(metadata: Metadata) -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()
    private val onEndedListeners = mutableListOf<() -> Unit>()
    private val onFirstFrameListeners = mutableListOf<(renderTimeMs: Long) -> Unit>()

    private fun clearAllListeners() {
        onResolutionListeners.clear()
        onErrorListeners.clear()
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onPreparedListeners.clear()
        onMetadataListeners.clear()
        onCutoffListeners.clear()
        onEndedListeners.clear()
        onFirstFrameListeners.clear()
    }

    protected fun triggerResolution(width: Int, height: Int) {
        onResolutionListeners.forEach { it(width, height) }
    }

    protected fun triggerError(error: PlaybackException?) {
        // 错误回调可同步开始下一次 prepare，先清理本次任务。
        // BUFFERING 的 null 事件只清理界面错误，加载超时仍须保留。
        if (error != null) {
            cancelLoadTimeout()
            resetCutoff()
        }
        onErrorListeners.forEach { it(error) }
    }

    protected fun triggerReady() {
        cancelLoadTimeout()
        onReadyListeners.forEach { it() }
    }

    protected fun triggerBuffering(buffering: Boolean) {
        onBufferingListeners.forEach { it(buffering) }
    }

    protected fun triggerPrepared(loadTimeoutMillis: Long = SP.videoPlayerLoadTimeout) {
        cancelLoadTimeout()
        resetCutoff()
        startLoadTimeout(loadTimeoutMillis)
        notifyPrepared()
    }

    protected fun startLoadTimeout(loadTimeoutMillis: Long = SP.videoPlayerLoadTimeout) {
        if (loadTimeoutJob?.isActive == true) return
        loadTimeoutJob = coroutineScope.launch {
            delay(loadTimeoutMillis)
            loadTimeoutJob = null
            triggerError(PlaybackException.LOAD_TIMEOUT)
        }
    }

    protected fun cancelLoadTimeout() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
    }

    /**
     * 仅通知 prepared 事件，不启动加载超时任务。
     * 供组合播放器转发子播放器事件时使用，避免两层各武装一个超时。
     */
    protected fun notifyPrepared() {
        onPreparedListeners.forEach { it() }
    }

    protected fun triggerMetadata(metadata: Metadata) {
        onMetadataListeners.forEach { it(metadata) }
    }

    protected fun triggerCurrentPosition(newPosition: Long) {
        // 只有进度真正前进（或回退）时才重置卡死检测；
        // 进度仅小幅抖动（如软解掉帧）时不重置，防止卡死后一直无法触发恢复。
        if (positionCheckpoint < 0 || newPosition < positionCheckpoint || newPosition - positionCheckpoint >= 500) {
            positionCheckpoint = newPosition
            resetCutoffTimeoutJob()
        }
    }

    protected fun resetCutoff() {
        positionCheckpoint = -1
        cutoffTimeoutJob?.cancel()
        cutoffTimeoutJob = null
    }

    private fun resetCutoffTimeoutJob() {
        cutoffTimeoutJob?.cancel()
        cutoffTimeoutJob = coroutineScope.launch {
            delay(SP.videoPlayerLoadTimeout)
            cutoffTimeoutJob = null
            triggerCutoff()
        }
    }

    protected fun triggerCutoff() {
        cutoffTimeoutJob?.cancel()
        cutoffTimeoutJob = null
        onCutoffListeners.forEach { it() }
    }

    protected fun triggerEnded() {
        cancelLoadTimeout()
        resetCutoff()
        onEndedListeners.forEach { it() }
    }

    protected fun triggerFirstFrame(renderTimeMs: Long) {
        onFirstFrameListeners.forEach { it(renderTimeMs) }
    }

    fun onResolution(listener: (width: Int, height: Int) -> Unit) {
        onResolutionListeners.add(listener)
    }

    fun onError(listener: (error: PlaybackException?) -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: (buffering: Boolean) -> Unit) {
        onBufferingListeners.add(listener)
    }

    fun onPrepared(listener: () -> Unit) {
        onPreparedListeners.add(listener)
    }

    fun onMetadata(listener: (metadata: Metadata) -> Unit) {
        onMetadataListeners.add(listener)
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

    data class PlaybackException(
        val errorCodeName: String,
        val errorCode: Int,
    ) : Exception(errorCodeName) {
        companion object {
            val UNSUPPORTED_TYPE =
                PlaybackException("UNSUPPORTED_TYPE", 10002)
            val LOAD_TIMEOUT =
                PlaybackException("LOAD_TIMEOUT", 10003)
        }
    }

    /** 元数据 */
    data class Metadata(
        /** 视频编码 */
        val videoMimeType: String = "",
        /** 视频宽度 */
        val videoWidth: Int = 0,
        /** 视频高度 */
        val videoHeight: Int = 0,
        /** 视频颜色 */
        val videoColor: String = "",
        /** 视频帧率 */
        val videoFrameRate: Float = 0f,
        /** 视频比特率 */
        val videoBitrate: Int = 0,
        /** 视频解码器 */
        val videoDecoder: String = "",

        /** 音频编码 */
        val audioMimeType: String = "",
        /** 音频通道 */
        val audioChannels: Int = 0,
        /** 音频采样率 */
        val audioSampleRate: Int = 0,
        /** 音频解码器 */
        val audioDecoder: String = "",
    )
}
