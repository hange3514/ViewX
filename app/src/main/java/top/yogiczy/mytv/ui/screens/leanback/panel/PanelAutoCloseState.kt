package top.yogiczy.mytv.ui.screens.leanback.panel

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow

@Stable
class PanelAutoCloseState internal constructor(
    @IntRange(from = 0) private val timeout: Long,
    private val onTimeout: () -> Unit = {},
) {
    private var paused = false
    private val channel = Channel<Boolean>(Channel.CONFLATED)

    fun active() {
        if (!paused) channel.trySend(true)
    }

    fun setPaused(value: Boolean) {
        paused = value
        channel.trySend(!paused)
    }

    suspend fun observe() {
        channel.consumeAsFlow().collectLatest { active ->
            if (active) {
                delay(timeout)
                onTimeout()
            }
        }
    }
}

@Composable
fun rememberPanelAutoCloseState(
    @IntRange(from = 0) timeout: Long = 1000L * 15,
    onTimeout: () -> Unit = {},
) = remember { PanelAutoCloseState(timeout = timeout, onTimeout = onTimeout) }.also {
    LaunchedEffect(it) { it.observe() }
}
