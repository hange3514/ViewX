package top.yogiczy.mytv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    @SuppressLint("SetWorldReadable")
    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
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

            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) FileProvider.getUriForFile(
                    context, context.packageName + ".FileProvider", cachedApkFile
                )
                else Uri.fromFile(cachedApkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, "application/vnd.android.package-archive")
            }

            context.startActivity(installIntent)
        }
    }
}