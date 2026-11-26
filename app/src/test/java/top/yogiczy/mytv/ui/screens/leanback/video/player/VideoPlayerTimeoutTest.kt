package top.yogiczy.mytv.ui.screens.leanback.video.player

import android.view.SurfaceView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer.PlaybackException

class VideoPlayerTimeoutTest {
    @Test
    fun errorCallbackKeepsTheNextStreamsTimeout() = runBlocking {
        val player = TestPlayer(this)
        val timedOutStream = CompletableDeferred<String>()
        val failed = PlaybackException("SOURCE_ERROR", 1)
        player.onError { error ->
            when (error) {
                failed -> player.prepare("second")
                PlaybackException.LOAD_TIMEOUT -> timedOutStream.complete(player.preparedStream)
                else -> Unit
            }
        }

        try {
            player.prepare("first")
            player.reportError(failed)
            assertEquals("second", withTimeout(2_000) { timedOutStream.await() })
        } finally {
            player.release()
        }
    }

    @Test
    fun bufferingNotificationKeepsTheLoadingTimeout() = runBlocking {
        val player = TestPlayer(this)
        val timeout = CompletableDeferred<PlaybackException>()
        player.onError { error ->
            if (error != null) timeout.complete(error)
        }

        try {
            player.prepare("buffering")
            player.reportError(null)
            assertEquals(PlaybackException.LOAD_TIMEOUT, withTimeout(2_000) { timeout.await() })
        } finally {
            player.release()
        }
    }

    @Test
    fun naturalEndCancelsTimeoutWithoutReportingAStall() = runBlocking {
        val player = TestPlayer(this)
        val events = mutableListOf<String>()
        player.onError { if (it != null) events.add("error") }
        player.onCutoff { events.add("cutoff") }
        player.onEnded { events.add("ended") }

        try {
            player.prepare("finite")
            player.reportEnded()
            delay(80)
            assertEquals(listOf("ended"), events)
        } finally {
            player.release()
        }
    }

    @Test
    fun pausedLoadCanStartItsTimeoutAgainOnResume() = runBlocking {
        val player = TestPlayer(this)
        val timeout = CompletableDeferred<PlaybackException>()
        player.onError { if (it != null) timeout.complete(it) }

        try {
            player.prepare("buffering")
            player.pause()
            delay(80)
            assertFalse(timeout.isCompleted)

            player.play()
            assertEquals(PlaybackException.LOAD_TIMEOUT, withTimeout(2_000) { timeout.await() })
        } finally {
            player.release()
        }
    }

    private class TestPlayer(scope: CoroutineScope) : LeanbackVideoPlayer(scope) {
        var preparedStream = ""
            private set

        override val currentPositionMs = 0L

        override fun prepare(url: String) {
            preparedStream = url
            triggerPrepared(loadTimeoutMillis = 20L)
        }

        fun reportError(error: PlaybackException?) {
            triggerError(error)
        }

        fun reportEnded() {
            triggerEnded()
        }

        override fun play() = startLoadTimeout(loadTimeoutMillis = 20L)
        override fun pause() = cancelLoadTimeout()
        override fun seekTo(positionMs: Long) = Unit
        override fun setVideoSurfaceView(surfaceView: SurfaceView) = Unit
    }
}
