package top.yogiczy.mytv.ui.screens.leanback.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.data.entities.EpgProgramme
import java.util.Locale

@Composable
fun LeanbackReplaySeekOverlay(
    visibleProvider: () -> Boolean,
    replayProgrammeProvider: () -> EpgProgramme?,
    currentPositionMsProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val visible = visibleProvider()
    val programme = replayProgrammeProvider() ?: return

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(200)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = tween(250),
            transformOrigin = TransformOrigin(0.5f, 0.5f),
        ),
        exit = fadeOut(animationSpec = tween(150)) + scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(200),
            transformOrigin = TransformOrigin(0.5f, 0.5f),
        ),
    ) {
        val currentPositionMs = currentPositionMsProvider()
        val duration = programme.endAt - programme.startAt
        val current = currentPositionMs.coerceIn(0, duration)
        val progress = if (duration > 0) (current.toFloat() / duration).coerceIn(0f, 1f) else 0f

        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
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
                        .padding(horizontal = 80.dp, vertical = 48.dp)
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = programme.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )

                    Text(
                        text = "${formatDuration(current)} / ${formatDuration(duration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(0.8f),
                    )

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
        }
    }
}
