package io.github.nerushkolya.locator.core.data

import io.github.nerushkolya.locator.core.common.LogSink
import io.github.nerushkolya.locator.core.common.Logger
import io.github.nerushkolya.locator.core.model.RemoteConfig
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.model.UnreachableReason
import io.github.nerushkolya.locator.core.testing.FakeServerApi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoriesTest {
    private val lines = mutableListOf<String>()
    private val logger = Logger(LogSink { _, line -> lines += line })
    private val api = FakeServerApi()

    @Test
    fun `status is read from the api on every call`() = runBlocking<Unit> {
        val repository = ServerStatusRepository(api, logger)
        assertEquals(ServerStatus.Reachable, repository.status())
        api.health = ServerStatus.NotReady("down")
        assertEquals(ServerStatus.NotReady("down"), repository.status())
        assertEquals(2, api.healthCalls)
    }

    @Test
    fun `unreachable status is logged with the reason and without free text`() = runBlocking<Unit> {
        api.health = ServerStatus.Unreachable("http://10.0.2.2:8080", UnreachableReason.TIMEOUT)
        ServerStatusRepository(api, logger).status()
        val line = lines.single()
        assertTrue(line.contains("\"event\":\"server.health\""))
        assertTrue(line.contains("\"reason\":\"TIMEOUT\""))
    }

    @Test
    fun `config is returned and failure is passed on`() = runBlocking<Unit> {
        val repository = RemoteConfigRepository(api, logger)
        assertEquals(RemoteConfig(1, "https://tiles.example.org/style"), repository.config().getOrThrow())
        api.config = Result.failure(IllegalStateException("boom"))
        assertTrue(repository.config().isFailure)
        assertEquals(2, api.configCalls)
    }

    @Test
    fun `config failure is logged without the exception text`() = runBlocking<Unit> {
        api.config = Result.failure(IllegalStateException("secret 50.4501, 30.5234"))
        RemoteConfigRepository(api, logger).config()
        assertFalse(lines.single().contains("50.4501"))
        assertTrue(lines.single().contains("config.failed"))
    }
}
