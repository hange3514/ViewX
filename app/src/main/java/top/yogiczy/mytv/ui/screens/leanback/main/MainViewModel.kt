package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.repositories.HttpStatusException
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.repositories.iptv.LoadedPlaylist
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.utils.LiveSettingsBus
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Logger
import java.io.IOException

class LeanbackMainViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var epgJob: Job? = null
    private var playlistRevision = 0L

    init {
        viewModelScope.launch {
            LiveSettingsBus.iptvReloadRequests.collect { load(forceRefresh = it) }
        }
        viewModelScope.launch {
            LiveSettingsBus.epgRefreshRequests.collect { refreshEpg(forceRefresh = it) }
        }
        load()
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        epgJob?.cancel()
        val sourceUrl = SP.iptvSourceUrl
        val cacheTime = if (forceRefresh) 0L else SP.iptvSourceCacheTime
        val simplify = SP.iptvSourceSimplify
        val previous = (_uiState.value as? LeanbackMainUiState.Ready)?.takeIf { it.sourceUrl == sourceUrl }
        if (previous == null) _uiState.value = LeanbackMainUiState.Loading()
        loadJob = viewModelScope.launch {
            val playlist = try {
                loadPlaylist(sourceUrl, cacheTime, simplify, showLoading = previous == null)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                if (previous == null) {
                    val message = if (ex is IOException && ex !is HttpStatusException) {
                        "获取直播源失败，请检查网络连接"
                    } else ex.message
                    _uiState.value = LeanbackMainUiState.Error(message)
                } else {
                    LeanbackToastState.I.showToast("直播源刷新失败，继续播放当前频道")
                    refreshEpg()
                }
                return@launch
            }

            val hiddenGroups = SP.iptvSourceHiddenGroupList
            val visibleGroups = if (hiddenGroups.isEmpty()) {
                playlist.groups
            } else {
                val filtered = IptvGroupList(playlist.groups.filter { it.name !in hiddenGroups })
                if (filtered.isEmpty()) playlist.groups else filtered
            }
            _uiState.value = LeanbackMainUiState.Ready(
                iptvGroupList = visibleGroups,
                epgList = previous?.epgList ?: EpgList(),
                playlistRevision = previous?.takeIf { it.iptvGroupList == visibleGroups }
                    ?.playlistRevision ?: ++playlistRevision,
                sourceUrl = sourceUrl,
                embeddedEpgUrl = playlist.embeddedEpgUrl,
            )
            SP.iptvSourceUrlHistoryList += sourceUrl
            refreshEpg(forceRefresh)
        }
    }

    /**
     * 加载失败后重试（错误页"按确定重试"）
     */
    fun retry() {
        if (_uiState.value is LeanbackMainUiState.Loading) return
        load(forceRefresh = true)
    }

    private suspend fun loadPlaylist(
        sourceUrl: String,
        cacheTime: Long,
        simplify: Boolean,
        showLoading: Boolean,
    ): LoadedPlaylist {
        var retryCount = 0L
        while (true) {
            try {
                return iptvRepository.loadPlaylist(sourceUrl, cacheTime, simplify)
            } catch (ex: IOException) {
                if (ex is HttpStatusException && ex.status in 400..499 && ex.status !in listOf(408, 429)) throw ex
                if (retryCount >= Constants.HTTP_RETRY_COUNT) throw ex
                retryCount++
                if (showLoading) {
                    _uiState.value = LeanbackMainUiState.Loading(
                        "重新获取直播源（$retryCount/${Constants.HTTP_RETRY_COUNT}）…"
                    )
                }
                delay(Constants.HTTP_RETRY_INTERVAL)
            }
        }
    }

    private fun refreshEpg(forceRefresh: Boolean = false) {
        epgJob?.cancel()
        val ready = (_uiState.value as? LeanbackMainUiState.Ready)?.takeIf {
            it.sourceUrl == SP.iptvSourceUrl
        } ?: return
        if (!SP.epgEnable) {
            _uiState.value = ready.copy(epgList = EpgList())
            return
        }

        val configuredEpgUrl = SP.epgXmlUrl
        val epgUrls = listOfNotNull(ready.embeddedEpgUrl, configuredEpgUrl).distinct()
        val refreshIntervalHours = SP.epgRefreshTimeThreshold
        epgJob = viewModelScope.launch {
            val iptvGroupList = ready.iptvGroupList
            val filteredChannels = iptvGroupList.iptvList.map { it.channelName }
            val filteredChannelIds = iptvGroupList.iptvList.map { it.tvgId }.filter { it.isNotBlank() }
            try {
                val (epgList, usedEpgUrl) = epgRepository.getEpgList(
                    xmlUrls = epgUrls,
                    filteredChannels = filteredChannels,
                    filteredChannelIds = filteredChannelIds,
                    refreshIntervalHours = refreshIntervalHours,
                    forceRefresh = forceRefresh,
                )
                val current = _uiState.value as? LeanbackMainUiState.Ready
                if (current != null && current.playlistRevision == ready.playlistRevision) {
                    if (usedEpgUrl == configuredEpgUrl) SP.epgXmlUrlHistoryList += configuredEpgUrl
                    _uiState.value = current.copy(epgList = epgList)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                log.w("暂时无法刷新节目单", ex)
            }
        }
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
        val playlistRevision: Long = 0L,
        val sourceUrl: String = "",
        val embeddedEpgUrl: String? = null,
    ) : LeanbackMainUiState
}
