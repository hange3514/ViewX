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
import top.yogiczy.mytv.ui.screens.leanback.update.LeanBackUpdateViewModel
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.LiveSettingsBus
import top.yogiczy.mytv.ui.utils.SP

@Composable
fun LeanbackSettingsCategoryApp(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    updateViewModel: LeanBackUpdateViewModel = viewModel(),
) {

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "开机自启",
                supportingContent = "请确保当前设备支持该功能",
                trailingContent = {
                    Switch(checked = settingsViewModel.appBootLaunch, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.appBootLaunch = !settingsViewModel.appBootLaunch
                },
            )
        }

        item {
            val context = LocalContext.current

            // 各显示模式的默认界面密度
            fun defaultRatio(type: SP.AppDeviceDisplayType) = when (type) {
                SP.AppDeviceDisplayType.LEANBACK -> 1f
                SP.AppDeviceDisplayType.PAD -> 1.1f
                SP.AppDeviceDisplayType.MOBILE -> 1.3f
            }

            LeanbackSettingsCategoryListItem(
                headlineContent = "显示模式",
                supportingContent = "短按切换应用显示模式（平板/手机已支持全触摸操作）",
                trailingContent = when (settingsViewModel.appDeviceDisplayType) {
                    SP.AppDeviceDisplayType.LEANBACK -> "TV"
                    SP.AppDeviceDisplayType.PAD -> "Pad"
                    SP.AppDeviceDisplayType.MOBILE -> "手机"
                },
                onSelected = {
                    val entries = SP.AppDeviceDisplayType.entries
                    val current = settingsViewModel.appDeviceDisplayType
                    val next = entries[(current.ordinal + 1) % entries.size]

                    // 密度未被手动调整过（仍等于当前模式默认值）时，跟随新模式
                    if (SP.uiDensityScaleRatio == defaultRatio(current)) {
                        settingsViewModel.uiDensityScaleRatio = defaultRatio(next)
                    }

                    settingsViewModel.appDeviceDisplayType = next
                    LeanbackToastState.I.showToast("显示模式已切换，正在刷新...")
                    LiveSettingsBus.recreateAppRequests.tryEmit(Unit)
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "应用更新",
                supportingContent = "最新版本：v${updateViewModel.latestRelease.version}",
                trailingContent = if (updateViewModel.isUpdateAvailable) "发现新版本" else "无更新",
                onSelected = {
                    if (updateViewModel.isUpdateAvailable)
                        updateViewModel.showDialog = true
                },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryAppPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryApp(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel(),
        )
    }
}
