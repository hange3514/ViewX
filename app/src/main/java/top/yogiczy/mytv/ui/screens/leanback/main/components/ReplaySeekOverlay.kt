package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.data.entities.EpgProgramme
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
                        Text(
                            text = "${shortTimeFormat.format(programme.startAt)} ~ " +
                                    shortTimeFormat.format(programme.endAt),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alpha(0.8f),
                        )
                    }

                    // 第二行：进度条
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )

                    // 第三行：相对进度 + 绝对时刻
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${formatDuration(current)} / ${formatDuration(duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(0.8f),
                        )
                        Text(
                            text = timeFormat.format(programme.startAt + current),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alpha(0.8f),
                        )
                    }
                }
            }
        }
    }
}
