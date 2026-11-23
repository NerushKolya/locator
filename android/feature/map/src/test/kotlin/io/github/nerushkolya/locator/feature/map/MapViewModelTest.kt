package io.github.nerushkolya.locator.feature.map

import app.cash.turbine.test
import io.github.nerushkolya.locator.core.common.LogSink
import io.github.nerushkolya.locator.core.common.Logger
import io.github.nerushkolya.locator.core.data.RemoteConfigRepository
import io.github.nerushkolya.locator.core.data.ServerStatusRepository
import io.github.nerushkolya.locator.core.model.RemoteConfig
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.model.UnreachableReason
import io.github.nerushkolya.locator.core.testing.FakeServerApi
import io.github.nerushkolya.locator.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class MapViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val address = ServerAddress("http://10.0.2.2:8080", configurable = true)
    private val api = FakeServerApi(config = Result.success(RemoteConfig(1, "https://tiles.example.org/style")))
    private val logger = Logger(LogSink { _, _ -> })

    private fun viewModel() = MapViewModel(
        addressProvider = { address },
        statusRepository = ServerStatusRepository(api, logger),
        configRepository = RemoteConfigRepository(api, logger),
    )

    @Test
    fun `starts loading then shows reachable server and the map style`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            // The dispatcher is unconfined, so the first emitted state may already be the final one.
            var state = awaitItem()
            while (state.refreshing) state = awaitItem()
            assertEquals(ServerStatus.Reachable, state.status)
            assertEquals(MapSource.Ready("https://tiles.example.org/style"), state.map)
            assertEquals(address, state.address)
        }
    }

    @Test
    fun `unreachable server means unavailable map`() = runTest {
        api.health = ServerStatus.Unreachable(address.baseUrl, UnreachableReason.CONNECTION_REFUSED)
        val state = viewModel().settled()
        assertEquals(api.health, state.status)
        assertEquals(MapSource.Unavailable, state.map)
        assertEquals(0, api.configCalls)
    }

    @Test
    fun `503 is not ready`() = runTest {
        api.health = ServerStatus.NotReady("down")
        assertEquals(ServerStatus.NotReady("down"), viewModel().settled().status)
    }

    @Test
    fun `configuration error means unavailable map`() = runTest {
        api.config = Result.failure(IllegalStateException())
        val state = viewModel().settled()
        assertEquals(ServerStatus.Reachable, state.status)
        assertEquals(MapSource.Unavailable, state.map)
    }

    @Test
    fun `refresh reads health and configuration again`() = runTest {
        val viewModel = viewModel()
        viewModel.settled()
        api.health = ServerStatus.NotReady("degraded")
        viewModel.refresh()
        assertEquals(ServerStatus.NotReady("degraded"), viewModel.settled().status)
        assertEquals(2, api.healthCalls)
        assertEquals(2, api.configCalls)
    }

    private suspend fun MapViewModel.settled(): MapUiState {
        var result: MapUiState? = null
        state.test {
            var state = awaitItem()
            while (state.refreshing || state.status == null) state = awaitItem()
            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }
}
