package net.igng.mcstatus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.igng.mcstatus.data.OverviewMetrics
import net.igng.mcstatus.data.OverviewServerSnapshot
import net.igng.mcstatus.data.PerformanceSample
import net.igng.mcstatus.data.RangePreset
import net.igng.mcstatus.data.ServerCardState
import net.igng.mcstatus.data.ServerDetailResponse
import net.igng.mcstatus.data.ServerSummary
import net.igng.mcstatus.data.StatusRepository

data class OverviewUiState(
    val isLoading: Boolean = true,
    val servers: List<ServerSummary> = emptyList(),
    val snapshots: List<OverviewServerSnapshot> = emptyList(),
    val errorMessage: String? = null,
) {
    val cards: List<ServerCardState>
        get() = snapshots.map { snapshot ->
            val latestPerf = snapshot.latestPerf
            ServerCardState(
                server = snapshot.server,
                latestPerf = latestPerf,
                bestLatency = snapshot.bestLatency,
                isOnline = latestPerf.isRecentEnough()
            )
        }

    val metrics: OverviewMetrics
        get() {
            val latest = cards.mapNotNull { it.latestPerf }
            return OverviewMetrics(
                serverCount = servers.size,
                onlineCount = cards.count { it.isOnline },
                totalPlayers = latest.sumOf { it.online_players ?: 0 },
                avgTps = if (latest.isEmpty()) 0.0 else latest.mapNotNull { it.avg_tps }.average()
            )
        }
}

data class DetailUiState(
    val isLoading: Boolean = true,
    val selectedRange: RangePreset = RangePreset.DAY_1,
    val detail: ServerDetailResponse? = null,
    val errorMessage: String? = null,
)

class OverviewViewModel(
    private val repository: StatusRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            runCatching {
                val snapshots = repository.fetchOverview()
                val servers = snapshots.map { it.server }
                _uiState.value.copy(
                    isLoading = false,
                    servers = servers,
                    snapshots = snapshots
                )
            }.onSuccess { _uiState.value = it }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "加载失败"
                    )
                }
        }
    }
}

class DetailViewModel(
    private val repository: StatusRepository,
    private val serverId: Int,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectRange(preset: RangePreset) {
        if (_uiState.value.selectedRange == preset) return
        refresh(preset)
    }

    fun refresh(range: RangePreset = _uiState.value.selectedRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedRange = range,
                errorMessage = null
            )

            runCatching { repository.fetchServerDetail(serverId, range) }
                .onSuccess { detail ->
                    _uiState.value = DetailUiState(
                        isLoading = false,
                        selectedRange = range,
                        detail = detail
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "加载失败"
                    )
                }
        }
    }
}

class OverviewViewModelFactory(
    private val repository: StatusRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return OverviewViewModel(repository) as T
    }
}

class DetailViewModelFactory(
    private val repository: StatusRepository,
    private val serverId: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DetailViewModel(repository, serverId) as T
    }
}

private fun PerformanceSample?.isRecentEnough(): Boolean {
    val timestamp = this?.recorded_at ?: return false
    return runCatching {
        Duration.between(Instant.parse(timestamp), Instant.now()).toHours() <= 2
    }.getOrDefault(false)
}
