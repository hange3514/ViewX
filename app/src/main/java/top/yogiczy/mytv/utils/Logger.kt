package top.yogiczy.mytv.utils

import android.util.Log
import kotlinx.serialization.Serializable
import top.yogiczy.mytv.data.utils.Constants

/**
 * 日志工具类
 */
class Logger private constructor(
    private val tag: String
) {
    fun d(message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        // addHistoryItem(HistoryItem(LevelType.DEBUG, tag, message, throwable?.message))
    }

    fun i(message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.INFO, tag, message, throwable?.message))
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.WARN, tag, message, throwable?.message))
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.ERROR, tag, message, throwable?.message))
    }

    fun wtf(message: String, throwable: Throwable? = null) {
        Log.wtf(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.ERROR, tag, message, throwable?.message))
    }

    companion object {
        fun create(tag: String) = Logger(tag)

        // 播放器回调/IO 协程/AsyncServer 线程都会并发读写，需加锁保护
        private val _history = java.util.Collections.synchronizedList(mutableListOf<HistoryItem>())

        /** 返回快照，避免调用方遍历与写入并发导致 ConcurrentModificationException */
        val history: List<HistoryItem>
            get() = synchronized(_history) { _history.toList() }

        fun addHistoryItem(item: HistoryItem) {
            synchronized(_history) {
                _history.add(item)
                while (_history.size > Constants.LOG_HISTORY_MAX_SIZE) _history.removeAt(0)
            }
        }
    }

    enum class LevelType {
        DEBUG, INFO, WARN, ERROR
    }

    @Serializable
    data class HistoryItem(
        val level: LevelType,
        val tag: String,
        val message: String,
        val cause: String? = null,
        val time: Long = System.currentTimeMillis(),
    )
}

/**
 * 注入日志
 */
abstract class Loggable(private val tag: String? = null) {
    protected val log: Logger
        get() = Logger.create(tag ?: javaClass.simpleName)
}