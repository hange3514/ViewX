package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * VLC 播放器内核。
 * 对 HEVC 等格式在硬解较弱的设备上容错更好（硬解异常可回退软解），
 * 通过设置"播放器内核"切换使用。
 */
class LeanbackVlcVideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null

    override fun initialize() {
        super.initialize()
        if (libVLC == null) {
            libVLC = LibVLC(
                context,
                arrayListOf(
                    "--no-video-title-show",
                    "--network-caching=3000", // 网络缓冲 3 秒
                    "--no-drop-late-frames",
                    "--clock-jitter=0",
                    "--clock-synchro=0",
                ),
            )
            mediaPlayer = MediaPlayer(libVLC!!).apply {
                setEventListener { event -> handleEvent(event) }
            }
        }
        surfaceView?.let { attachSurface(it) }
    }

    private fun attachSurface(view: SurfaceView) {
        val vout = mediaPlayer?.vlcVout ?: return
        if (!vout.areViewsAttached()) {
            vout.setVideoView(view)
            vout.attachViews()
        }
    }

    private fun handleEvent(event: MediaPlayer.Event) {
        val mp = mediaPlayer ?: return
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                triggerReady()
                triggerBuffering(false)
                reportVideoTrack(mp)
            }

            MediaPlayer.Event.Vout -> reportVideoTrack(mp)

            MediaPlayer.Event.Buffering -> triggerBuffering(event.buffering < 100f)

            MediaPlayer.Event.Paused,
            MediaPlayer.Event.Stopped,
            -> triggerBuffering(false)

            MediaPlayer.Event.EndReached -> triggerCutoff()

            MediaPlayer.Event.EncounteredError ->
                triggerError(PlaybackException("VLC_ERROR", event.type))

            MediaPlayer.Event.TimeChanged -> triggerCurrentPosition(event.timeChanged)
        }
    }

    /**
     * 上报视频分辨率与编码信息（该版本 libvlc 没有 currentVideoTrack，从 media.tracks 取）
     */
    private fun reportVideoTrack(mp: MediaPlayer) {
        val track = mp.media?.tracks
            ?.firstOrNull { it.type == IMedia.Track.Type.Video } as? IMedia.VideoTrack
            ?: return
        triggerResolution(track.width, track.height)
        triggerMetadata(
            metadata.copy(
                videoMimeType = track.codec ?: "",
                videoWidth = track.width,
                videoHeight = track.height,
            )
        )
    }

    override fun prepare(url: String) {
        val mp = mediaPlayer ?: return
        mp.stop()
        val media = Media(libVLC ?: return, Uri.parse(url))
        mp.media = media
        media.release()
        surfaceView?.let { attachSurface(it) }
        mp.play()
        triggerPrepared()
    }

    override fun play() {
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    override val currentPositionMs: Long
        get() = mediaPlayer?.time ?: 0L

    override fun setVolume(volume: Float) {
        mediaPlayer?.volume = (volume * 100).toInt()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        attachSurface(surfaceView)
    }

    override fun clearVideoSurface() {
        mediaPlayer?.vlcVout?.detachViews()
        surfaceView = null
    }

    override fun release() {
        mediaPlayer?.stop()
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer?.release()
        mediaPlayer = null
        libVLC?.release()
        libVLC = null
        surfaceView = null
        super.release()
    }
}
