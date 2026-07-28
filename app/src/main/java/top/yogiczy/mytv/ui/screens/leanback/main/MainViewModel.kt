package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.utils.LiveSettingsBus
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Logger

class LeanbackMainViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    init {
        load()

        // 网页端推送新的节目单地址后实时刷新，无需重启应用
        viewModelScope.launch {
            LiveSettingsBus.epgRefreshRequests.collect {
                if (_uiState.value is LeanbackMainUiState.Ready) {
                    refreshEpg()
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            refreshIptv()
            refreshEpg()
        }
    }

    /**
     * 加载失败后重试（错误页"按确定重试"）
     */
    fun retry() {
        if (_uiState.value is LeanbackMainUiState.Loading) return
        _uiState.value = LeanbackMainUiState.Loading()
        load()
    }

    private suspend fun refreshIptv() {
        flow {
            emit(
                iptvRepository.getIptvGroupList(
                    sourceUrl = SP.iptvSourceUrl,
                    cacheTime = SP.iptvSourceCacheTime,
                    simplify = SP.iptvSourceSimplify,
                )
            )
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    LeanbackMainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch {
                _uiState.value = LeanbackMainUiState.Error(it.message)
                SP.iptvSourceUrlHistoryList -= SP.iptvSourceUrl
            }
            .map {
                val hiddenGroups = SP.iptvSourceHiddenGroupList
                val filtered = if (hiddenGroups.isEmpty()) it
                else IptvGroupList(it.filter { group -> group.name !in hiddenGroups })
                // 全部隐藏会导致列表为空（用户可能误操作），此时回退为不过滤
                val visibleGroupList = if (filtered.isEmpty()) it else filtered

                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = visibleGroupList)
                SP.iptvSourceUrlHistoryList += SP.iptvSourceUrl
                visibleGroupList
            }
            .collect()
    }

    private suspend fun refreshEpg() {
        if (_uiState.value is LeanbackMainUiState.Ready) {
            val iptvGroupList = (_uiState.value as LeanbackMainUiState.Ready).iptvGroupList

            val filteredChannels = iptvGroupList.iptvList.map { it.channelName }
            val filteredChannelIds = iptvGroupList.iptvList.map { it.tvgId }.filter { it.isNotBlank() }

            // 源内整合了节目单（#EXTM3U 的 x-tvg-url）时优先使用，否则回退到设置中的地址
            val sourceEpgUrl = iptvRepository.getSourceEpgUrl(SP.iptvSourceUrl, SP.iptvSourceCacheTime)
            val xmlUrl = sourceEpgUrl ?: SP.epgXmlUrl
            log.i(if (sourceEpgUrl != null) "使用源内整合节目单: $xmlUrl" else "使用设置中节目单: $xmlUrl")

            flow {
                emit(
                    epgRepository.getEpgList(
                        xmlUrl = xmlUrl,
                        filteredChannels = filteredChannels,
                        filteredChannelIds = filteredChannelIds,
                        refreshTimeThreshold = SP.epgRefreshTimeThreshold,
                    )
                )
            }
                .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
                .catch {
                    emit(EpgList())
                    SP.epgXmlUrlHistoryList -= SP.epgXmlUrl
                }
                .map { epgList ->
                    _uiState.value =
                        (_uiState.value as LeanbackMainUiState.Ready).copy(epgList = epgList)
                    SP.epgXmlUrlHistoryList += SP.epgXmlUrl
                }
                .collect()
        }
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}