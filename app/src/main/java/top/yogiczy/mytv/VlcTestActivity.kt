package top.yogiczy.mytv

import android.media.MediaCodecList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VlcTestActivity : ComponentActivity() {

    private lateinit var libVLC: LibVLC
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var logText: TextView
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        surfaceView = SurfaceView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                2f
            )
        }
        layout.addView(surfaceView)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        logText = TextView(this).apply {
            setTextIsSelectable(true)
        }
        scroll.addView(logText)
        layout.addView(scroll)

        setContentView(layout)

        val url = intent.getStringExtra("url") ?: intent.dataString ?: run {
            log("ERROR: no url extra or data")
            return
        }
        log("URL: $url")
        dumpMediaCodecs()

        val options = ArrayList<String>().apply {
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("--verbose=2")
            add("--codec=all")
        }
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)

        mediaPlayer?.setEventListener { event ->
            log("EVENT: ${event.type} ${event.typeName()}")
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                log("surfaceCreated")
                attachAndPlay(url)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                log("surfaceChanged ${width}x${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                log("surfaceDestroyed")
            }
        })
    }

    private fun attachAndPlay(url: String) {
        val mp = mediaPlayer ?: return
        val vout = mp.getVLCVout()
        vout.setVideoView(surfaceView)
        vout.attachViews()

        val media = Media(libVLC, Uri.parse(url))
        media.setHWDecoderEnabled(false, false)
        if (url.endsWith(".avs2", ignoreCase = true) || url.endsWith(".avs", ignoreCase = true)) {
            media.addOption(":demux=avformat")
            log("forced demux=avformat")
        }
        mp.media = media
        media.release()
        mp.play()
        log("play() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.detachViews()
        mediaPlayer?.release()
        libVLC.release()
    }

    private fun dumpMediaCodecs() {
        try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            val interesting = StringBuilder()
            for (info in list.codecInfos) {
                val name = info.name
                for (type in info.supportedTypes) {
                    if (type.contains("avs", ignoreCase = true) ||
                        type.contains("cavs", ignoreCase = true) ||
                        name.contains("avs", ignoreCase = true) ||
                        name.contains("cavs", ignoreCase = true)
                    ) {
                        interesting.append("${info.name}: $type (encoder=${info.isEncoder})\n")
                    }
                }
            }
            if (interesting.isNotEmpty()) {
                log("AVS/CAVS MediaCodec found:\n$interesting")
            } else {
                log("No AVS/CAVS MediaCodec found on this device")
            }
        } catch (e: Exception) {
            log("MediaCodecList error: ${e.message}")
        }
    }

    private fun log(msg: String) {
        Log.d("VlcTest", msg)
        runOnUiThread {
            logText.append("$msg\n")
        }
    }

    private fun MediaPlayer.Event.typeName(): String {
        return when (type) {
            MediaPlayer.Event.MediaChanged -> "MediaChanged"
            MediaPlayer.Event.Opening -> "Opening"
            MediaPlayer.Event.Buffering -> "Buffering(${buffering.toDouble() / 100}%)"
            MediaPlayer.Event.Playing -> "Playing"
            MediaPlayer.Event.Paused -> "Paused"
            MediaPlayer.Event.Stopped -> "Stopped"
            MediaPlayer.Event.EndReached -> "EndReached"
            MediaPlayer.Event.EncounteredError -> "EncounteredError"
            MediaPlayer.Event.TimeChanged -> "TimeChanged"
            MediaPlayer.Event.PositionChanged -> "PositionChanged"
            MediaPlayer.Event.SeekableChanged -> "SeekableChanged"
            MediaPlayer.Event.PausableChanged -> "PausableChanged"
            MediaPlayer.Event.Vout -> "Vout(${getVoutCount()})"
            else -> "Unknown($type)"
        }
    }
}
