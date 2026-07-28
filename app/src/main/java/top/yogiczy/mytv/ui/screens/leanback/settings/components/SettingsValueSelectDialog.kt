package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import top.yogiczy.mytv.ui.utils.tvTouchClickable
import kotlin.math.max

/**
 * 通用单选对话框：替代循环步进式设置项（改一个值要连按几十次 OK）
 */
@Composable
fun <T> LeanbackSettingsValueSelectDialog(
    showDialogProvider: () -> Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    options: List<Pair<String, T>>,
    currentValueProvider: () -> T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!showDialogProvider()) return

    val currentValue = currentValueProvider()

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = { Text(text = "短按选择") },
        title = { Text(title) },
        text = {
            TvLazyColumn(
                state = rememberTvLazyListState(
                    max(0, options.indexOfFirst { it.second == currentValue } - 2)
                ),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options.size, key = { it }) { index ->
                    val (label, value) = options[index]
                    val focusRequester = remember { FocusRequester() }
                    var isFocused by remember { mutableStateOf(false) }

                    // 打开即聚焦当前选中项（遥控器可直接操作）
                    LaunchedEffect(Unit) {
                        if (value == currentValue) focusRequester.requestFocus()
                    }

                    androidx.tv.material3.ListItem(
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                            .tvTouchClickable(onClick = {
                                onSelected(value)
                                onDismissRequest()
                            }),
                        selected = value == currentValue,
                        onClick = {
                            onSelected(value)
                            onDismissRequest()
                        },
                        headlineContent = {
                            androidx.tv.material3.Text(
                                text = label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        trailingContent = {
                            if (value == currentValue) {
                                androidx.tv.material3.Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "checked",
                                )
                            }
                        },
                    )
                }
            }
        },
    )
}
