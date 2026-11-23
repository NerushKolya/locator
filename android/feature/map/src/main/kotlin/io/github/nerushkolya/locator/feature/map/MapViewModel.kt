package io.github.nerushkolya.locator.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.nerushkolya.locator.core.data.RemoteConfigRepository
import io.github.nerushkolya.locator.core.data.ServerStatusRepository
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerAddressProvider
import io.github.nerushkolya.locator.core.model.ServerStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MapSource {
    data object Loading : MapSource

    data class Ready(val styleUrl: String) : MapSource

    /** The configuration cannot be read, or the style failed to load. */
    data object Unavailable : MapSource
}

/** One immutable state. `status` is null until the first health answer arrives. */
data class MapUiState(
    val address: ServerAddress,
    val status: ServerStatus? = null,
    val map: MapSource = MapSource.Loading,
    val refreshing: Boolean = true,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    addressProvider: ServerAddressProvider,
    private val statusRepository: ServerStatusRepository,
    private val configRepository: RemoteConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MapUiState(address = addressProvider.address()))
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** Event `Refresh`: reads health and configuration again. */
    fun refresh() {
        if (job?.isActive == true) return
        _state.update { it.copy(refreshing = true) }
        job = viewModelScope.launch {
            val (status, map) = coroutineScope {
                val statusDeferred = async { statusRepository.status() }
                val status = statusDeferred.await()
                // An unreachable server cannot serve the configuration; do not wait for another timeout.
                val map = if (status is ServerStatus.Unreachable) {
                    MapSource.Unavailable
                } else {
                    configRepository.config().fold(
                        onSuccess = { MapSource.Ready(it.mapStyleUrl) },
                        onFailure = { MapSource.Unavailable },
                    )
                }
                status to map
            }
            _state.update { it.copy(status = status, map = map, refreshing = false) }
        }
    }

    /** The map view reports that the style could not be loaded. */
    fun onMapStyleFailed() {
        _state.update { it.copy(map = MapSource.Unavailable) }
    }
}
