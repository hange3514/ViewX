package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import kotlinx.coroutines.launch
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.update.LeanBackUpdateViewModel
import top.yogiczy.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackSettingsCategoryUpdate(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    updateViewModel: LeanBackUpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "检查更新",
                supportingContent = "当前版本 v$versionName",
                onSelected = {
                    coroutineScope.launch {
                        when {
                            updateViewModel.isChecking ->
                                LeanbackToastState.I.showToast("正在检查更新...")

                            updateViewModel.isUpdateAvailable ->
                                updateViewModel.showDialog = true

                            else -> {
                                LeanbackToastState.I.showToast("正在检查更新...")
                                updateViewModel.checkUpdate(versionName)
                                if (updateViewModel.isUpdateAvailable) {
                                    updateViewModel.showDialog = true
                                } else {
                                    LeanbackToastState.I.showToast("当前已是最新版本")
                                }
                            }
                        }
                    }
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "更新强提醒",
                supportingContent = if (settingsViewModel.updateForceRemind) "检测到新版本时会弹窗提醒"
                else "检测到新版本时仅在左上角提示",
                trailingContent = {
                    Switch(checked = settingsViewModel.updateForceRemind, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.updateForceRemind = !settingsViewModel.updateForceRemind
                },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryUpdatePreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryUpdate(
            modifier = Modifier.padding(20.dp),
        )
    }
}
