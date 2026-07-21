package top.yogiczy.mytv.ui.screens.leanback.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.update.components.LeanbackUpdateDialog
import top.yogiczy.mytv.utils.ApkInstaller
import java.io.File

@Composable
fun LeanbackUpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    updateViewModel: LeanBackUpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val packageInfo = rememberPackageInfo()
    val latestFile = remember { File(AppGlobal.cacheDir, "latest.apk") }

    LaunchedEffect(Unit) {
        // 延迟到启动稳定后再检测，避免与启动期的直播源/EPG 加载争抢网络和 CPU
        delay(30_000)
        updateViewModel.checkUpdate(packageInfo.versionName)

        val latestRelease = updateViewModel.latestRelease
        if (
            updateViewModel.isUpdateAvailable &&
            latestRelease.version != settingsViewModel.appLastLatestVersion
        ) {
            settingsViewModel.appLastLatestVersion = latestRelease.version

            if (settingsViewModel.updateForceRemind) {
                updateViewModel.showDialog = true
            } else {
                LeanbackToastState.I.showToast("新版本: v${latestRelease.version}")
            }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    if (!ApkInstaller.installApk(context, latestFile.path)) {
                        LeanbackToastState.I.showToast("无法调起安装界面，请通过U盘手动安装")
                    }
                } else {
                    LeanbackToastState.I.showToast("未授予安装权限")
                }
            }
        }

    LaunchedEffect(updateViewModel.updateDownloaded) {
        if (!updateViewModel.updateDownloaded) return@LaunchedEffect

        fun tryInstall() {
            if (!ApkInstaller.installApk(context, latestFile.path)) {
                LeanbackToastState.I.showToast("无法调起安装界面，请通过U盘手动安装")
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            tryInstall()
        } else {
            if (context.packageManager.canRequestPackageInstalls()) {
                tryInstall()
            } else {
                // 部分电视 ROM（如小米 MIUI TV）没有"未知来源"设置页，
                // 直接 launch 会 ActivityNotFoundException 闪退，逐级回退
                val intents = listOf(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    },
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    },
                )
                var launched = false
                for (intent in intents) {
                    try {
                        launcher.launch(intent)
                        launched = true
                        break
                    } catch (ex: android.content.ActivityNotFoundException) {
                        // 尝试下一个
                    }
                }
                if (!launched) {
                    LeanbackToastState.I.showToast("请在系统设置中允许本应用安装应用后重试")
                }
            }
        }
    }

    LeanbackUpdateDialog(
        modifier = modifier,
        showDialogProvider = { updateViewModel.showDialog },
        onDismissRequest = { updateViewModel.showDialog = false },
        releaseProvider = { updateViewModel.latestRelease },
        onUpdateAndInstall = {
            updateViewModel.showDialog = false
            coroutineScope.launch(Dispatchers.IO) {
                updateViewModel.downloadAndUpdate(latestFile)
            }
        },
    )
}

@Composable
private fun rememberPackageInfo(context: Context = LocalContext.current): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)
