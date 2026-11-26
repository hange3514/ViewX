package top.yogiczy.mytv.data.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.AppGlobal
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 用于将数据缓存至本地
 */
abstract class FileCacheRepository(
    private val fileName: String,
) {
    // 仓库在调用点到处 new 实例，实例级互斥锁防不住并发，
    // 按缓存文件名全局共享一把锁
    private val mutex get() = mutexFor(fileName)

    private fun getCacheFile() = File(AppGlobal.cacheDir, fileName)

    private suspend fun getCacheData(): String? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        try {
            if (file.exists()) file.readText() else null
        } catch (ex: IOException) {
            ex.printStackTrace()
            null
        }
    }

    private suspend fun setCacheData(data: String) = withContext(Dispatchers.IO) {
        // 先写临时文件再原子替换，避免进程被杀/并发写留下半截缓存导致下次解析崩溃
        val file = getCacheFile()
        val tmpFile = File(file.parentFile, "$fileName.tmp")
        try {
            tmpFile.writeText(data)
            if (!tmpFile.renameTo(file)) throw IOException("无法替换缓存文件")
        } finally {
            tmpFile.delete()
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                getCacheFile().delete()
            } catch (ex: SecurityException) {
                ex.printStackTrace()
            }
        }
    }

    protected suspend fun getOrRefresh(
        cacheTime: Long,
        cacheKey: String,
        refreshOp: suspend () -> String,
    ): String = mutex.withLock {
        // 缓存内容与来源一起写入。旧格式没有来源，无法用于当前请求的回退。
        val digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray())
        val header = digest.joinToString("", prefix = "ViewX-cache-v1: ", postfix = "\n") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        val cacheData = getCacheData()?.takeIf { it.startsWith(header) }
            ?.substring(header.length)?.takeIf { it.isNotBlank() }

        val age = System.currentTimeMillis() - getCacheFile().lastModified()
        if (cacheData != null && age in 0 until cacheTime) {
            return@withLock cacheData
        }

        val freshData = try {
            refreshOp()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // 刷新失败时仍可读同源旧缓存。
            return@withLock cacheData ?: throw ex
        }

        try {
            setCacheData(header + freshData)
        } catch (ex: IOException) {
            // 磁盘故障不影响本次已成功加载的数据。
            ex.printStackTrace()
        }
        freshData
    }
}

private val cacheMutexes = mutableMapOf<String, Mutex>()

private fun mutexFor(fileName: String): Mutex = synchronized(cacheMutexes) {
    cacheMutexes.getOrPut(fileName) { Mutex() }
}
