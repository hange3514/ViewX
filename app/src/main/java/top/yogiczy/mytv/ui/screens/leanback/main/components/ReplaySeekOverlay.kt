package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.ui.utils.tvTouchClickable
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 回放控制条：节目标题/时间范围、进度、绝对时刻、暂停状态、片段序号
 */
@Composable
fun LeanbackReplayControlBar(
    visibleProvider: () -> Boolean,
    replayProgrammeProvider: () -> EpgProgramme?,
    currentPositionMsProvider: () -> Long,
    isPausedProvider: () -> Boolean = { false },
    segmentInfoProvider: () -> String = { "" },
    onTap: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onExitReplay: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val visible = visibleProvider()
    val programme = replayProgrammeProvider() ?: return
    val isPaused = isPausedProvider()

    AnimatedVisibility(
        visible = visible || isPaused, // 暂停时常显，避免不知道处于暂停态
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(250),
        ),
        exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(200),
        ),
    ) {
        val currentPositionMs = currentPositionMsProvider()
        val duration = (programme.endAt - programme.startAt).coerceAtLeast(0)
        val current = currentPositionMs.coerceIn(0, duration)
        val progress = if (duration > 0) (current.toFloat() / duration).coerceIn(0f, 1f) else 0f

        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        val shortTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        val segmentInfo = segmentInfoProvider()

        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0)
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            else
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 60.dp, vertical = 40.dp)
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        // 触摸点按控制条 = 暂停/继续
                        .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 第一行：播放状态 + 标题 + 片段序号 + 节目时间范围
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPaused) "已暂停" else "播放中",
                        )
                        Text(
                            text = programme.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (segmentInfo.isNotBlank()) {
                            Text(
                                text = segmentInfo,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.alpha(0.8f),
                            )
                        }
                        // 退出回放入口：触摸可点、遥控器可聚焦（双击返回的方式保留）
                        val exitFocusRequester = remember { FocusRequester() }
                        var exitFocused by remember { mutableStateOf(false) }
                        Text(
                            text = "退出回放",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (exitFocused) MaterialTheme.colorScheme.background
                            else LocalContentColor.current,
                            modifier = Modifier
                                .focusRequester(exitFocusRequester)
                                .onFocusChanged { exitFocused = it.isFocused || it.hasFocus }
                                .background(
                                    if (exitFocused) LocalContentColor.current
                                    else LocalContentColor.current.copy(alpha = 0.2f),
                                    MaterialTheme.shapes.small,
                                )
                                .focusable()
                                .tvTouchClickable(onClick = onExitReplay)
                                .handleLeanbackKeyEvents(onSelect = { onExitReplay() })
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        Text(
                            text = "${shortTimeFormat.format(programme.startAt)} ~ " +
                                    shortTimeFormat.format(programme.endAt),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alpha(0.8f),
                        )
                    }

                    // 第二行：进度条（触屏可拖拽/点按定位）
                    var dragFraction by remember { mutableStateOf<Float?>(null) }
                    val shownFraction = dragFraction ?: progress
                    val shownPositionMs = (shownFraction * duration).toLong()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            // 拖拽进度条：拖动时预览，松手后定位
                            .pointerInput(duration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        dragFraction?.let { onSeekTo((it * duration).toLong()) }
                                        dragFraction = null
                                    },
                                    onDragCancel = { dragFraction = null },
                                ) { change, _ ->
                                    change.consume()
                                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                            // 点按进度条直接定位
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    onSeekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                                }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // 轨道
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    MaterialTheme.shapes.small,
                                ),
                        )
                        // 已播放部分
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(shownFraction)
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.onBackground,
                                    MaterialTheme.shapes.small,
                                ),
                        )
                        // 拖动手柄
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(shownFraction)
                                .height(16.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(12.dp)
                                    .background(MaterialTheme.colorScheme.onBackground),
                            )
                        }
                    }

                    // 第三行：相对进度 + 绝对时刻
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${formatDuration(shownPositionMs)} / ${formatDuration(duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(0.8f),
                        )
                        Text(
                            text = timeFormat.format(programme.startAt + shownPositionMs),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alpha(0.8f),
                        )
                    }
                }
            }
        }
    }
}
