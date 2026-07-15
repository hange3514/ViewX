package top.yogiczy.mytv.ui.utils

import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 全局当前时间（毫秒）。
 * 每 1 秒更新一次，供所有需要时间判断的 UI 组件统一使用，
 * 避免每个 composable/item 反复调用 System.currentTimeMillis()。
 */
object CurrentTime {
    val ms = mutableLongStateOf(System.currentTimeMillis())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    fun startTick() {
        if (tickJob?.isActive == true) return

        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(1000)
                ms.value = System.currentTimeMillis()
            }
        }
    }
}
