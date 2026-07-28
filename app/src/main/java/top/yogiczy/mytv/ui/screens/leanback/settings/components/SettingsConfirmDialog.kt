package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.DialogProperties
import top.yogiczy.mytv.ui.utils.tvTouchClickable

/**
 * 破坏性操作二次确认对话框（清空收藏、清除缓存等）
 */
@Composable
fun LeanbackSettingsConfirmDialog(
    showDialogProvider: () -> Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showDialogProvider()) return

    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { confirmFocusRequester.requestFocus() }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = {
            androidx.tv.material3.Button(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
                modifier = Modifier
                    .focusRequester(confirmFocusRequester)
                    .tvTouchClickable(onClick = {
                        onConfirm()
                        onDismissRequest()
                    }),
            ) {
                androidx.tv.material3.Text(text = "确定")
            }
        },
        dismissButton = {
            androidx.tv.material3.Button(
                onClick = onDismissRequest,
                modifier = Modifier.tvTouchClickable(onClick = onDismissRequest),
            ) {
                androidx.tv.material3.Text(text = "取消")
            }
        },
        title = { Text(title) },
        text = { Text(text) },
    )
}
