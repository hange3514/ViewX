package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.LiveSettingsBus
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.humanizeMs
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryVideoPlayer(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器内核",
                supportingContent = "部分设备 HEVC 硬解花屏时切换 VLC 试试（切换后自动刷新生效）",
                trailingContent = when (settingsViewModel.videoPlayerEngine) {
                    SP.VideoPlayerEngine.MEDIA3 -> "Media3"
                    SP.VideoPlayerEngine.VLC -> "VLC"
                },
                onSelected = {
                    settingsViewModel.videoPlayerEngine =
                        SP.VideoPlayerEngine.entries.let {
                            it[(it.indexOf(settingsViewModel.videoPlayerEngine) + 1) % it.size]
                        }
                    LeanbackToastState.I.showToast("播放器内核已切换，正在刷新...")
                    LiveSettingsBus.recreateAppRequests.tryEmit(Unit)
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "VLC 硬解",
                supportingContent = "使用 VLC 内核时，HEVC 花屏可尝试关闭硬解改用软件解码（下次换台生效）",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.videoPlayerVlcHardwareDecode,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.videoPlayerVlcHardwareDecode =
                        !settingsViewModel.videoPlayerVlcHardwareDecode
                    LeanbackToastState.I.showToast(
                        if (settingsViewModel.videoPlayerVlcHardwareDecode) "已开启硬解" else "已切换软件解码，换台后生效"
                    )
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "全局画面比例",
                trailingContent = when (settingsViewModel.videoPlayerAspectRatio) {
                    SP.VideoPlayerAspectRatio.ORIGINAL -> "原始"
                    SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> "16:9"
                    SP.VideoPlayerAspectRatio.FOUR_THREE -> "4:3"
                    SP.VideoPlayerAspectRatio.AUTO -> "自动拉伸"
                },
                onSelected = {
                    settingsViewModel.videoPlayerAspectRatio =
                        SP.VideoPlayerAspectRatio.entries.let {
                            it[(it.indexOf(settingsViewModel.videoPlayerAspectRatio) + 1) % it.size]
                        }
                },
            )
        }


        item {
            val min = 1000 * 5L
            val max = 1000 * 30L
            val step = 1000 * 5L

            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器加载超时",
                supportingContent = "影响超时换源、断线重连",
                trailingContent = settingsViewModel.videoPlayerLoadTimeout.humanizeMs(),
                onSelected = {
                    settingsViewModel.videoPlayerLoadTimeout =
                        max(min, (settingsViewModel.videoPlayerLoadTimeout + step) % (max + step))
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器自定义UA",
                supportingContent = settingsViewModel.videoPlayerUserAgent,
                remoteConfig = true,
            )
        }

    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryHttpPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryVideoPlayer(
            modifier = Modifier.padding(20.dp),
        )
    }
}
