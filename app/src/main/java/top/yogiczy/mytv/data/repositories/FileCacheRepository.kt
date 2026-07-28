package top.yogiczy.mytv.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.AppGlobal
import java.io.File

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
        if (file.exists()) file.readText()
        else null
    }

    private suspend fun setCacheData(data: String) = withContext(Dispatchers.IO) {
        // 先写临时文件再原子替换，避免进程被杀/并发写留下半截缓存导致下次解析崩溃
        val file = getCacheFile()
        val tmpFile = File(file.parentFile, "$fileName.tmp")
        tmpFile.writeText(data)
        if (!tmpFile.renameTo(file)) {
            // 个别文件系统 renameTo 失败时回退直接写
            file.writeText(data)
            tmpFile.delete()
        }
    }

    protected suspend fun getOrRefresh(cacheTime: Long, refreshOp: suspend () -> String): String {
        return getOrRefresh(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    fun clearCache() {
        try {
            getCacheFile().delete()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    protected suspend fun getOrRefresh(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String = mutex.withLock {
        val cacheData = getCacheData()

        if (!isExpired(getCacheFile().lastModified(), cacheData) && !cacheData.isNullOrBlank()) {
            return@withLock cacheData
        }

        try {
            val data = refreshOp()
            setCacheData(data)
            data
        } catch (ex: Exception) {
            // 刷新失败时回退到陈旧缓存（stale-if-error），网络抖动不至于把数据清空
            if (!cacheData.isNullOrBlank()) cacheData else throw ex
        }
    }
}

private val cacheMutexes = mutableMapOf<String, Mutex>()

private fun mutexFor(fileName: String): Mutex = synchronized(cacheMutexes) {
    cacheMutexes.getOrPut(fileName) { Mutex() }
}
