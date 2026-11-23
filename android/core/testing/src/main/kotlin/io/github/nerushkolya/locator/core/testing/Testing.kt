package io.github.nerushkolya.locator.core.testing

import io.github.nerushkolya.locator.core.model.RemoteConfig
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.network.ConfigSource
import io.github.nerushkolya.locator.core.network.HealthSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Replaces `Dispatchers.Main` for view model tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Fake of the health and configuration endpoints: tests set the next answers and count the calls. */
class FakeServerApi(
    var health: ServerStatus = ServerStatus.Reachable,
    var config: Result<RemoteConfig> = Result.success(RemoteConfig(1, "https://tiles.example.org/style")),
) : HealthSource, ConfigSource {
    var healthCalls = 0
        private set
    var configCalls = 0
        private set

    override suspend fun check(): ServerStatus {
        healthCalls++
        return health
    }

    override suspend fun fetch(): Result<RemoteConfig> {
        configCalls++
        return config
    }
}
