package top.yogiczy.mytv.ui.screens.leanback.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelChannelNo
import top.yogiczy.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackPanelChannelNoSelectScreen(
    modifier: Modifier = Modifier,
    channelNoProvider: () -> String = { "" },
) {
    val childPadding = rememberLeanbackChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        LeanbackPanelChannelNo(
            channelNoProvider = channelNoProvider,
            modifier = Modifier
                .padding(top = childPadding.top, end = childPadding.end)
                .align(Alignment.TopEnd),
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelChannelNoSelectScreenPreview() {
    LeanbackTheme {
        LeanbackPanelChannelNoSelectScreen(
            channelNoProvider = { "01" }
        )
    }
}

@Stable
class LeanbackPanelChannelNoSelectState(
    private val onChannelNoConfirm: (String) -> Unit = {},
    initialChannelNo: String = "",
) {
    private var _channelNo by mutableStateOf(initialChannelNo)
    val channelNo get() = _channelNo

    private var generation = 0

    fun input(no: Int) {
        // 限制最大长度，防止连按数字导致 confirm 时 toInt 溢出崩溃
        if (_channelNo.length >= MAX_CHANNEL_NO_LENGTH) return
        _channelNo += no.toString()
        channel.trySend(++generation)
    }

    /** 按 OK 立即确认，跳过等待倒计时 */
    fun confirm() {
        generation++ // 作废倒计时里残留的待确认值
        if (_channelNo.isNotEmpty()) {
            onChannelNoConfirm(_channelNo)
            _channelNo = ""
        }
    }

    /** 按返回取消本次输入（同时作废已进入倒计时的值） */
    fun cancel() {
        generation++
        _channelNo = ""
    }

    private val channel = Channel<Int>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow()
            .debounce { (4 - _channelNo.length).coerceAtLeast(0) * 1000L }
            .collect { gen ->
                // 倒计时结束时校验世代，取消/确认过的输入不再生效
                if (gen == generation && _channelNo.isNotEmpty()) {
                    val no = _channelNo
                    _channelNo = ""
                    onChannelNoConfirm(no)
                }
            }
    }
}

@Composable
fun rememberLeanbackPanelChannelNoSelectState(
    onChannelNoConfirm: (String) -> Unit = {},
) = remember {
    LeanbackPanelChannelNoSelectState(onChannelNoConfirm)
}.also { LaunchedEffect(it) { it.observe() } }

/** 数字选台最大输入位数 */
private const val MAX_CHANNEL_NO_LENGTH = 4