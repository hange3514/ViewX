package top.yogiczy.mytv.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    @SuppressLint("SetWorldReadable")
    fun installApk(context: Context, filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        val cacheDir = context.cacheDir
        val cachedApkFile = File(cacheDir, file.name)

        // 源文件已在 cacheDir 时就是同一个文件，跳过自复制；
        // 否则流式拷贝，避免 85MB APK 一次性读进内存
        if (cachedApkFile.absolutePath != file.absolutePath) {
            file.inputStream().use { input ->
                cachedApkFile.outputStream().use { input.copyTo(it) }
            }
        }
        // 解决Android6 无法解析安装包
        cachedApkFile.setReadable(true, false)

        return try {
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) FileProvider.getUriForFile(
                    context, context.packageName + ".FileProvider", cachedApkFile
                )
                else Uri.fromFile(cachedApkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, "application/vnd.android.package-archive")
            }

            // MIUI 等 ROM 的安装器读取 FileProvider URI 时可能丢授权（SecurityException），
            // 向所有能处理安装意图的包 + 已知安装器包显式授权（业界通用做法）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val installerPackages = (
                        context.packageManager.queryIntentActivities(installIntent, 0)
                            .map { it.activityInfo.packageName } + listOf(
                            "com.miui.packageinstaller",
                            "com.android.packageinstaller",
                            "com.google.android.packageinstaller",
                        )).toSet()
                installerPackages.forEach { pkg ->
                    try {
                        context.grantUriPermission(
                            pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            context.startActivity(installIntent)
            true
        } catch (ex: Exception) {
            // 部分电视 ROM 的安装器不响应 ACTION_VIEW（如 MIUI TV），
            // 回退到 PackageInstaller 会话式安装
            ex.printStackTrace()
            installViaPackageInstaller(context, cachedApkFile)
        }
    }

    /**
     * 会话式安装：不依赖安装器 Intent，兼容性更好；
     * 系统仍会弹出安装确认界面
     */
    private fun installViaPackageInstaller(context: Context, file: File): Boolean {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)

            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, Intent("${context.packageName}.INSTALL_COMPLETE"), flags,
                )
                session.commit(pendingIntent.intentSender)
            }
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }
}