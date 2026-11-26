package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.preload.PreloadMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yogiczy.mytv.ui.utils.SP
import java.io.IOException
import java.util.concurrent.locks.LockSupport
import androidx.media3.common.PlaybackException as Media3PlaybackException

@OptIn(UnstableApi::class)
class LeanbackMedia3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(12_000, 24_000, 750, 2_000)
        .build()
    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)

    // EXTENSION_RENDERER_MODE_ON 让 FFmpeg 扩展音频解码器参与选择。
    private val videoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    ).setLoadControl(loadControl)
        .setBandwidthMeter(bandwidthMeter)
        .build().apply {
            playWhenReady = true
        }

    private val contentTypeAttempts = mutableSetOf<Int>()
    private var behindLiveWindowRecovered = false
    private var updatePositionJob: Job? = null
    private var attachedSurfaceView: SurfaceView? = null
    private var mediaSequence = 0L
    private val eventWindow = Timeline.Window()
    private var hasBeenReady = false
    private var rebufferStartedAt: Long? = null
    private var pendingLivePreload: PendingLivePreload? = null
    private var livePreloadExpiry: Job? = null
    private var activeLivePreload: PreloadMediaSource? = null
    private var preloadTrackSelector: DefaultTrackSelector? = null

    private data class HttpSettings(val userAgent: String, val timeoutMs: Int)

    private data class PendingLivePreload(
        val uri: Uri,
        val httpSettings: HttpSettings,
        val startedAtMs: Long,
        val progress: LivePreloadProgress,
        val source: PreloadMediaSource,
    )

    private fun httpSettings() = HttpSettings(SP.videoPlayerUserAgent, SP.videoPlayerLoadTimeout.toInt())

    private fun dataSourceFactory(
        settings: HttpSettings,
        transferListener: TransferListener? = null,
    ): DefaultDataSource.Factory = DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory().apply {
            setUserAgent(settings.userAgent)
            setConnectTimeoutMs(settings.timeoutMs)
            setReadTimeoutMs(settings.timeoutMs)
            setKeepPostFor302Redirects(true)
            setAllowCrossProtocolRedirects(true)
            if (transferListener != null) setTransferListener(transferListener)
        },
    )

    private fun mediaItem(uri: Uri): MediaItem =
        MediaItem.Builder().setUri(uri).setMediaId((++mediaSequence).toString()).build()

    @OptIn(UnstableApi::class)
    private fun prepare(uri: Uri, contentType: Int? = null, preloadedSource: PreloadMediaSource? = null) {
        releaseActiveLivePreload()
        stopPositionTracking()
        hasBeenReady = false
        rebufferStartedAt = null
        val type = contentType ?: Util.inferContentType(uri)
        val mediaSource = preloadedSource ?: when (type) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory(httpSettings())).createMediaSource(mediaItem(uri))
            }

            C.CONTENT_TYPE_RTSP -> {
                RtspMediaSource.Factory().createMediaSource(mediaItem(uri))
            }

            C.CONTENT_TYPE_OTHER -> {
                ProgressiveMediaSource.Factory(dataSourceFactory(httpSettings())).createMediaSource(mediaItem(uri))
            }

            else -> {
                triggerError(
                    PlaybackException.UNSUPPORTED_TYPE.copy(
                        errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                    )
                )
                null
            }
        }

        if (mediaSource != null) {
            contentTypeAttempts.add(type)
            activeLivePreload = preloadedSource
            videoPlayer.setMediaSource(mediaSource)
            triggerPrepared()
            videoPlayer.prepare()
        }
    }

    override fun preloadLive(url: String) {
        val uri = Uri.parse(url)
        if ((!uri.scheme.equals("http", ignoreCase = true) && !uri.scheme.equals("https", ignoreCase = true)) ||
            Util.inferContentType(uri) != C.CONTENT_TYPE_OTHER ||
            videoPlayer.currentMediaItem?.localConfiguration?.uri == uri ||
            !videoPlayer.isPlaying
        ) {
            cancelLivePreload()
            return
        }

        val settings = httpSettings()
        if (pendingLivePreload?.let { it.uri == uri && it.httpSettings == settings && !it.progress.rejected } == true) {
            return
        }
        cancelLivePreload()
        val progress = LivePreloadProgress { rejectedProgress ->
            coroutineScope.launch {
                if (pendingLivePreload?.progress === rejectedProgress) cancelLivePreload()
            }
        }
        val trackSelector = preloadTrackSelector ?: DefaultTrackSelector(context).apply {
            init({}, bandwidthMeter)
            preloadTrackSelector = this
        }
        trackSelector.setParameters(videoPlayer.trackSelectionParameters)
        val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory(settings, progress))
            .setContinueLoadingCheckIntervalBytes(PRELOAD_LOADING_CHECK_BYTES)
        val source = PreloadMediaSource.Factory(
            progressiveFactory,
            progress,
            trackSelector,
            bandwidthMeter,
            Array(videoPlayer.rendererCount) { videoPlayer.getRenderer(it).capabilities },
            loadControl.allocator,
            videoPlayer.playbackLooper,
        ).createMediaSource(mediaItem(uri))
        source.addEventListener(Handler(videoPlayer.playbackLooper), progress)
        val pending = PendingLivePreload(uri, settings, SystemClock.elapsedRealtime(), progress, source)
        pendingLivePreload = pending
        source.preload(C.TIME_UNSET)
        Log.d("ViewXPlayback", "preload_started")
        livePreloadExpiry = coroutineScope.launch {
            delay(PRELOAD_TTL_MS)
            if (pendingLivePreload === pending) cancelLivePreload()
        }
    }

    override fun cancelLivePreload() {
        val pending = detachLivePreload() ?: return
        pending.progress.discard()
        pending.source.removeEventListener(pending.progress)
        pending.source.releasePreloadMediaSource()
    }

    private fun detachLivePreload(): PendingLivePreload? {
        livePreloadExpiry?.cancel()
        livePreloadExpiry = null
        val pending = pendingLivePreload
        pendingLivePreload = null
        return pending
    }

    override fun prepareLive(url: String, allowPreload: Boolean): Boolean {
        val uri = Uri.parse(url)
        val pending = pendingLivePreload?.takeIf {
            allowPreload && it.uri == uri && it.progress.ready && !it.progress.rejected &&
                SystemClock.elapsedRealtime() - it.startedAtMs < PRELOAD_TTL_MS &&
                it.httpSettings == httpSettings()
        }
        if (pending == null) {
            cancelLivePreload()
        } else {
            detachLivePreload()
            pending.progress.claimed = true
            pending.source.removeEventListener(pending.progress)
            Log.d("ViewXPlayback", "preload_claimed age_ms=${SystemClock.elapsedRealtime() - pending.startedAtMs}")
        }
        prepareUrl(uri, pending?.source)
        return pending != null
    }

    private fun releaseActiveLivePreload() {
        activeLivePreload?.releasePreloadMediaSource()
        activeLivePreload = null
    }

    private fun startPositionTracking() {
        if (!videoPlayer.playWhenReady || videoPlayer.playbackState != Player.STATE_READY ||
            updatePositionJob?.isActive == true
        ) return

        updatePositionJob = coroutineScope.launch {
            while (isActive) {
                triggerCurrentPosition(videoPlayer.currentPosition)
                delay(1000)
            }
        }
    }

    private fun stopPositionTracking() {
        updatePositionJob?.cancel()
        updatePositionJob = null
        resetCutoff()
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: Media3PlaybackException) {
            stopPositionTracking()
            // 预读建立的连接可能在交接前已经断开，沿用一次普通起播，再交给原有重试逻辑。
            if (activeLivePreload != null && !hasBeenReady && videoPlayer.playWhenReady) {
                videoPlayer.currentMediaItem?.localConfiguration?.uri?.let { uri ->
                    Log.d("ViewXPlayback", "preload_fallback error=${ex.errorCodeName}")
                    prepare(uri, C.CONTENT_TYPE_OTHER)
                    return
                }
            }
            // 如果是直播加载位置错误，尝试重新播放
            if (ex.errorCode == Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                videoPlayer.playWhenReady && !behindLiveWindowRecovered
            ) {
                behindLiveWindowRecovered = true
                videoPlayer.seekToDefaultPosition()
                triggerPrepared()
                videoPlayer.prepare()
            }
            // 当解析容器不支持时，尝试使用其他解析容器
            else if (videoPlayer.playWhenReady &&
                ex.errorCode == Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
            ) {
                val uri = videoPlayer.currentMediaItem?.localConfiguration?.uri
                when {
                    uri == null -> triggerError(PlaybackException.UNSUPPORTED_TYPE)
                    C.CONTENT_TYPE_HLS !in contentTypeAttempts -> prepare(uri, C.CONTENT_TYPE_HLS)
                    C.CONTENT_TYPE_OTHER !in contentTypeAttempts -> prepare(uri, C.CONTENT_TYPE_OTHER)
                    else -> triggerError(PlaybackException.UNSUPPORTED_TYPE)
                }
            } else {
                triggerError(
                    PlaybackException(ex.errorCodeName, ex.errorCode)
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            triggerBuffering(playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_BUFFERING) {
                if (hasBeenReady && videoPlayer.playWhenReady && rebufferStartedAt == null) {
                    rebufferStartedAt = SystemClock.elapsedRealtime()
                    Log.d("ViewXPlayback", "rebuffer_started")
                }
                triggerError(null)
            } else if (playbackState == Player.STATE_READY) {
                rebufferStartedAt?.let {
                    Log.d("ViewXPlayback", "rebuffer_finished elapsed_ms=${SystemClock.elapsedRealtime() - it}")
                }
                rebufferStartedAt = null
                hasBeenReady = true
                triggerReady()
                startPositionTracking()
            } else if (playbackState == Player.STATE_ENDED) {
                stopPositionTracking()
                triggerEnded()
            }
        }
    }

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long,
        ) {
            if (output !== attachedSurfaceView?.holder?.surface || !isCurrentMedia(eventTime)) return
            triggerFirstFrame(renderTimeMs)
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            if (attachedSurfaceView != null && isCurrentMedia(eventTime)) {
                Log.d("ViewXPlayback", "dropped_frames count=$droppedFrames window_ms=$elapsedMs")
            }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                // TODO 帧率、比特率目前是从tag中获取，有的返回空，后续需要实时计算
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }
    }

    private fun isCurrentMedia(eventTime: AnalyticsListener.EventTime): Boolean {
        if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return false
        val mediaId = eventTime.timeline.getWindow(eventTime.windowIndex, eventWindow).mediaItem.mediaId
        return mediaId == videoPlayer.currentMediaItem?.mediaId
    }

    override fun initialize() {
        super.initialize()
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
    }

    override fun release() {
        stopPositionTracking()
        cancelLivePreload()
        releaseActiveLivePreload()
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.release()
        preloadTrackSelector?.release()
        super.release()
    }

    @UnstableApi
    override fun prepare(url: String) {
        cancelLivePreload()
        prepareUrl(Uri.parse(url))
    }

    private fun prepareUrl(uri: Uri, preloadedSource: PreloadMediaSource? = null) {
        contentTypeAttempts.clear()
        behindLiveWindowRecovered = false
        metadata = Metadata()
        triggerMetadata(metadata)
        prepare(uri, preloadedSource = preloadedSource)
    }

    override fun play() {
        if (videoPlayer.playbackState == Player.STATE_BUFFERING) startLoadTimeout()
        videoPlayer.play()
        startPositionTracking()
    }

    override fun pause() {
        cancelLoadTimeout()
        stopPositionTracking()
        rebufferStartedAt = null
        cancelLivePreload()
        videoPlayer.pause()
    }

    override fun stop() {
        pause()
        releaseActiveLivePreload()
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        videoPlayer.seekTo(positionMs)
    }

    override val currentPositionMs: Long
        get() = videoPlayer.currentPosition

    override val bufferedDurationMs: Long
        get() = videoPlayer.totalBufferedDuration

    override val isPlaying: Boolean
        get() = videoPlayer.isPlaying

    override val hasLivePreload: Boolean
        get() = pendingLivePreload?.progress?.rejected == false

    override val hasVideoTrack: Boolean
        get() = videoPlayer.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        attachedSurfaceView = surfaceView
        videoPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurface() {
        attachedSurfaceView = null
        videoPlayer.setVideoSurfaceView(null)
    }
}

@UnstableApi
private class LivePreloadProgress(
    private val onRejected: (LivePreloadProgress) -> Unit,
) : PreloadMediaSource.PreloadControl, TransferListener, MediaSourceEventListener {
    @Volatile var ready = false
        private set
    @Volatile var rejected = false
        private set
    @Volatile var claimed = false
    private var bytesRead = 0
    private var firstReadAtNs = 0L

    override fun onTimelineRefreshed(mediaSource: PreloadMediaSource): Boolean = !rejected

    override fun onPrepared(mediaSource: PreloadMediaSource): Boolean = !rejected

    override fun onContinueLoadingRequested(mediaSource: PreloadMediaSource, bufferedPositionUs: Long): Boolean {
        if (bufferedPositionUs == C.TIME_END_OF_SOURCE) {
            reject()
            return false
        }
        if (!rejected && !ready && bufferedPositionUs >= PRELOAD_READY_US) {
            ready = true
            Log.d("ViewXPlayback", "preload_ready buffered_ms=${bufferedPositionUs / 1000}")
        }
        return !rejected && bufferedPositionUs < PRELOAD_TARGET_US
    }

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (!isNetwork || claimed || rejected) return
        if (firstReadAtNs == 0L) firstReadAtNs = SystemClock.elapsedRealtimeNanos()
        bytesRead += bytesTransferred
        if (bytesRead >= PRELOAD_MAX_BYTES) {
            reject()
            return
        }
        // 只在候选源的加载线程节制读取。切换接管后，当前台直接读取。
        val pacedBytes = (bytesRead - PRELOAD_READ_BURST_BYTES).coerceAtLeast(0)
        val nextReadAtNs = firstReadAtNs + pacedBytes * 1_000_000_000L / PRELOAD_READ_BYTES_PER_SECOND
        while (!claimed && !rejected && !Thread.currentThread().isInterrupted) {
            val waitNs = nextReadAtNs - SystemClock.elapsedRealtimeNanos()
            if (waitNs <= 0) break
            LockSupport.parkNanos(minOf(waitNs, 40_000_000L))
        }
    }

    override fun onLoadError(
        windowIndex: Int,
        mediaPeriodId: MediaSource.MediaPeriodId?,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        reject()
    }

    private fun reject() {
        if (claimed || rejected) return
        discard()
        onRejected(this)
    }

    fun discard() {
        rejected = true
    }
}

private const val PRELOAD_TTL_MS = 5_000L
private const val PRELOAD_READY_US = 750_000L
private const val PRELOAD_TARGET_US = 1_200_000L
private const val PRELOAD_LOADING_CHECK_BYTES = 64 * 1024
private const val PRELOAD_MAX_BYTES = 3 * 1024 * 1024
private const val PRELOAD_READ_BURST_BYTES = 64 * 1024
private const val PRELOAD_READ_BYTES_PER_SECOND = 512 * 1024
