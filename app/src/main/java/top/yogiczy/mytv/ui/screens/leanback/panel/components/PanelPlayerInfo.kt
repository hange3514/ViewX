package top.yogiczy.mytv.ui.screens.leanback.panel.components

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import java.text.DecimalFormat

@Composable
fun LeanbackPanelPlayerInfo(
    modifier: Modifier = Modifier,
    metadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
) {
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.bodyLarge,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelPlayerInfoResolution(metadataProvider = metadataProvider)
            PanelPlayerInfoNetSpeed()
        }
    }
}

@Composable
private fun PanelPlayerInfoResolution(
    modifier: Modifier = Modifier,
    metadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
) {
    val resolution by remember(metadataProvider) {
        derivedStateOf {
            val metadata = metadataProvider()
            metadata.videoWidth to metadata.videoHeight
        }
    }

    Text(
        text = "分辨率：${resolution.first}×${resolution.second}",
        modifier = modifier,
    )
}

@Composable
private fun PanelPlayerInfoNetSpeed(
    modifier: Modifier = Modifier,
    netSpeed: Long = rememberNetSpeed(),
) {
    val speedFormat = remember { DecimalFormat("0.#") }
    Text(
        text = when {
            netSpeed < 0 -> "接收：—"
            netSpeed < 1024 * 1024 -> "接收：${netSpeed / 1024} KB/s"
            else -> "接收：${speedFormat.format(netSpeed / 1024 / 1024f)} MB/s"
        },
        modifier = modifier,
    )
}

@Composable
private fun rememberNetSpeed(): Long {
    var netSpeed by remember { mutableLongStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        val uid = Process.myUid()
        suspend fun readSample() = withContext(Dispatchers.IO) {
            // 读取 UID 流量可能同步调用系统服务，避免阻塞遥控器的焦点事件。
            val bytes = runCatching { TrafficStats.getUidRxBytes(uid) }.getOrDefault(-1L)
            bytes to SystemClock.elapsedRealtime()
        }

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var (lastRxBytes, lastTimeStamp) = readSample()
            while (true) {
                delay(1000)
                val (nowRxBytes, nowTimeStamp) = readSample()
                val elapsed = nowTimeStamp - lastTimeStamp
                netSpeed = when {
                    nowRxBytes < 0 -> -1L
                    elapsed > 0 && lastRxBytes >= 0 && nowRxBytes >= lastRxBytes ->
                        (nowRxBytes - lastRxBytes) * 1_000 / elapsed
                    else -> 0L
                }
                lastTimeStamp = nowTimeStamp
                lastRxBytes = nowRxBytes
            }
        }
    }

    return netSpeed
}

@Preview
@Composable
private fun LeanbackPanelPlayerInfoPreview() {
    LeanbackTheme {
        LeanbackPanelPlayerInfo(
            metadataProvider = {
                LeanbackVideoPlayer.Metadata(
                    videoWidth = 1920,
                    videoHeight = 1080,
                )
            },
        )
    }
}

@Preview
@Composable
private fun LeanbackPanelPlayerInfoNetSpeedPreview() {
    LeanbackTheme {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelPlayerInfoNetSpeed()
            PanelPlayerInfoNetSpeed(netSpeed = 54321)
            PanelPlayerInfoNetSpeed(netSpeed = 1222 * 1222)
        }
    }
}
