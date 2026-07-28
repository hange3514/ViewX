package top.yogiczy.mytv.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * 初始焦点请求（带重试）。
 * 部分电视（如长虹）首次弹出面板时布局尚未完成，焦点请求会被静默丢弃，
 * 导致面板按键无响应；这里重试直至真正获得焦点。
 * 收编各列表条目里逐字复制的重试块
 */
@Composable
fun FocusRequester.requestInitialFocusWithRetry(
    initialFocusedProvider: () -> Boolean,
    isFocusedProvider: () -> Boolean,
    onInitialFocused: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        if (initialFocusedProvider()) {
            onInitialFocused()
            repeat(20) {
                if (isFocusedProvider()) return@LaunchedEffect
                this@requestInitialFocusWithRetry.requestFocus()
                delay(50)
            }
        }
    }
}
